package com.rinda.backend.plot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.common.ThresholdService;
import com.rinda.backend.plot.PlotDtos.GeoJsonPolygon;
import com.rinda.backend.plot.PlotDtos.PlotRequest;
import com.rinda.backend.plot.PlotDtos.PlotResponse;
import com.rinda.backend.user.FarmerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlotService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId RWANDA = ZoneId.of("Africa/Kigali");

    private final PlotRepository plots;
    private final FarmerRepository farmers;
    private final PlotGeometry geometry;
    private final ThresholdService thresholds;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PlotService(PlotRepository plots, FarmerRepository farmers, PlotGeometry geometry,
                       ThresholdService thresholds, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.plots = plots;
        this.farmers = farmers;
        this.geometry = geometry;
        this.thresholds = thresholds;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<PlotResponse> list(AuthPrincipal principal, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "page must be 0 or more, and size between 1 and " + MAX_PAGE_SIZE + ".");
        }
        PageRequest request = PageRequest.of(page, size, Sort.by("id").descending());
        Page<Plot> result = switch (principal.role()) {
            case FARMER -> plots.findByFarmerIdAndStatusNot(principal.userId(), PlotStatus.ARCHIVED, request);
            case AGENT -> plots.findInCooperative(agentCooperative(principal.userId()), request);
            case OFFICER, ADMIN -> plots.findByStatusNot(PlotStatus.ARCHIVED, request);
        };
        return PageResponse.of(result, this::toResponse);
    }

    @Transactional(readOnly = true)
    public PlotResponse get(AuthPrincipal principal, long id) {
        Plot plot = find(id);
        if (!canView(principal, plot)) {
            throw notFound();
        }
        return toResponse(plot);
    }

    @Transactional
    public PlotResponse create(AuthPrincipal principal, PlotRequest request) {
        requireMaize(request);
        checkPlantingDate(request.plantingDate());
        String geojson = toJson(request.boundary());
        BigDecimal area = measure(request.boundary(), geojson);
        Plot plot = new Plot(principal.userId(), request.name().trim(), geojson, area, request.plantingDate());
        return toResponse(plots.save(plot));
    }

    @Transactional
    public PlotResponse update(AuthPrincipal principal, long id, PlotRequest request) {
        requireMaize(request);
        Plot plot = findOwned(principal, id);

        String geojson = toJson(request.boundary());
        boolean boundaryChanged = !sameBoundary(plot, request.boundary());
        boolean dateChanged = !request.plantingDate().equals(plot.getPlantingDate());
        if (boundaryChanged || dateChanged) {
            changeVerifiedFields(plot, request, geojson, boundaryChanged, dateChanged);
        }
        plot.rename(request.name().trim());
        return toResponse(plot);
    }

    /** §2.2 #11: a plot under cover or with an open claim is a legal record, so it stays until that closes. */
    @Transactional
    public void delete(AuthPrincipal principal, long id) {
        Plot plot = findOwned(principal, id);
        if (hasActiveCover(id) || hasOpenClaim(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "PLOT_DELETE_DEFERRED",
                    "This plot has active cover or an open claim. It can be deleted after the season or claim closes.");
        }
        plot.archive();
    }

    /** Verified details feed every satellite comparison, so they can't change after verification. */
    private void changeVerifiedFields(Plot plot, PlotRequest request, String geojson,
                                      boolean boundaryChanged, boolean dateChanged) {
        if (plot.getStatus() == PlotStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "PLOT_LOCKED",
                    "The boundary and planting date can't change after verification. Ask your agent.");
        }
        if (dateChanged) {
            checkPlantingDate(request.plantingDate());
            plot.changePlantingDate(request.plantingDate());
        }
        if (boundaryChanged) {
            plot.changeBoundary(geojson, measure(request.boundary(), geojson));
        }
        plot.returnToVerification();
    }

    private boolean sameBoundary(Plot plot, GeoJsonPolygon boundary) {
        return toPolygon(plot.getGeojson()).equals(boundary);
    }

    private BigDecimal measure(GeoJsonPolygon boundary, String geojson) {
        BigDecimal area = geometry.validateAndMeasure(boundary, geojson);
        thresholds.value("plot_min_area_ha").filter(min -> area.compareTo(min) < 0).ifPresent(min -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLOT_TOO_SMALL",
                    "The plot must be at least " + min.stripTrailingZeros().toPlainString()
                            + " hectares for reliable satellite readings.");
        });
        thresholds.value("plot_max_area_ha").filter(max -> area.compareTo(max) > 0).ifPresent(max -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLOT_TOO_LARGE",
                    "The plot must be at most " + max.stripTrailingZeros().toPlainString() + " hectares.");
        });
        return area;
    }

    private static void requireMaize(PlotRequest request) {
        if (request.crop() != null && !"MAIZE".equals(request.crop())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CROP_NOT_SUPPORTED", "Rinda only covers maize.");
        }
    }

    /** The season window applies once the team has confirmed one (docs/OPEN_QUESTIONS.md question 5). */
    private void checkPlantingDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(RWANDA))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLANTING_DATE_INVALID",
                    "The planting date can't be in the future.");
        }
        List<Long> counts = jdbc.queryForList("""
                SELECT COUNT(*) AS total FROM seasons WHERE data_status = 'CONFIRMED'
                UNION ALL
                SELECT COUNT(*) FROM seasons WHERE data_status = 'CONFIRMED' AND ? BETWEEN start_date AND end_date
                """, Long.class, date);
        if (counts.get(0) > 0 && counts.get(1) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLANTING_DATE_INVALID",
                    "The planting date must fall within a season.");
        }
    }

    private boolean canView(AuthPrincipal principal, Plot plot) {
        return switch (principal.role()) {
            case FARMER -> plot.getFarmerId() == principal.userId();
            case AGENT -> farmers.findById(plot.getFarmerId())
                    .filter(f -> f.getCooperativeId() == agentCooperative(principal.userId())).isPresent();
            case OFFICER, ADMIN -> true;
        };
    }

    private long agentCooperative(long agentId) {
        return jdbc.queryForList("SELECT cooperative_id FROM agents WHERE user_id = ?", Long.class, agentId)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "AGENT_NOT_LINKED",
                        "This agent is not linked to a cooperative."));
    }

    private boolean hasActiveCover(long plotId) {
        return exists("""
                SELECT COUNT(*) FROM enrollments
                WHERE plot_id = ? AND status = 'COVERED' AND (end_date IS NULL OR end_date >= CURRENT_DATE)
                """, plotId);
    }

    private boolean hasOpenClaim(long plotId) {
        return exists("SELECT COUNT(*) FROM claims WHERE plot_id = ? AND status NOT IN ('PAID', 'FINAL')", plotId);
    }

    private boolean exists(String sql, long plotId) {
        Long count = jdbc.queryForObject(sql, Long.class, plotId);
        return count != null && count > 0;
    }

    private Plot find(long id) {
        return plots.findByIdAndStatusNot(id, PlotStatus.ARCHIVED).orElseThrow(PlotService::notFound);
    }

    private Plot findOwned(AuthPrincipal principal, long id) {
        Plot plot = find(id);
        if (plot.getFarmerId() != principal.userId()) {
            throw notFound();
        }
        return plot;
    }

    /** Someone else's plot gets the same 404 as a missing one, so ids reveal nothing. */
    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found.");
    }

    private PlotResponse toResponse(Plot plot) {
        return new PlotResponse(plot.getId(), plot.getFarmerId(), plot.getName(), plot.getCrop(),
                toPolygon(plot.getGeojson()), plot.getAreaHa(), plot.getPlantingDate(), plot.getStatus(),
                plot.getDataStatus(), plot.isDemo(), plot.getCreatedAt(), plot.getUpdatedAt());
    }

    private String toJson(GeoJsonPolygon polygon) {
        try {
            return mapper.writeValueAsString(polygon);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private GeoJsonPolygon toPolygon(String geojson) {
        try {
            return mapper.readValue(geojson, GeoJsonPolygon.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
