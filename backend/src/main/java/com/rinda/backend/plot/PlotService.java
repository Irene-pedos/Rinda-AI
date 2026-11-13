package com.rinda.backend.plot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.common.PhotoStorage;
import com.rinda.backend.common.ThresholdService;
import com.rinda.backend.config.RindaProperties;
import com.rinda.backend.monitoring.MonitoringQueries;
import com.rinda.backend.monitoring.MonitoringRequested;
import com.rinda.backend.plot.PlotDtos.FlagResponse;
import com.rinda.backend.plot.PlotDtos.GeoJsonPolygon;
import com.rinda.backend.plot.PlotDtos.PlotCreateRequest;
import com.rinda.backend.plot.PlotDtos.PlotResponse;
import com.rinda.backend.plot.PlotDtos.PlotUpdateRequest;
import com.rinda.backend.user.FarmerRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PlotService {

    private static final ZoneId RWANDA = ZoneId.of("Africa/Kigali");

    /** What the agent sends from the field. The photo's location is recorded by the agent's own app. */
    public record VerifyInput(String decision, LocalDate confirmedPlantingDate, String rejectionReason,
                              MultipartFile photo, Double photoLat, Double photoLng, Double photoAccuracyM,
                              Instant photoTakenAt) {
    }

    private final PlotRepository plots;
    private final FarmerRepository farmers;
    private final PlotRules rules;
    private final PlotFlags flags;
    private final PlotChecks checks;
    private final TenureConsentCheck tenure;
    private final ThresholdService thresholds;
    private final PhotoStorage storage;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Pattern upiPattern;

    private final MonitoringQueries monitoring;
    private final ApplicationEventPublisher events;

    public PlotService(PlotRepository plots, FarmerRepository farmers, PlotRules rules, PlotFlags flags,
                       PlotChecks checks, TenureConsentCheck tenure, ThresholdService thresholds,
                       PhotoStorage storage, JdbcTemplate jdbc, ObjectMapper mapper, RindaProperties properties,
                       MonitoringQueries monitoring, ApplicationEventPublisher events) {
        this.monitoring = monitoring;
        this.events = events;
        this.plots = plots;
        this.farmers = farmers;
        this.rules = rules;
        this.flags = flags;
        this.checks = checks;
        this.tenure = tenure;
        this.thresholds = thresholds;
        this.storage = storage;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.upiPattern = Pattern.compile(properties.plots().upiPattern());
    }

    @Transactional(readOnly = true)
    public PageResponse<PlotResponse> list(AuthPrincipal principal, int page, int size) {
        PageResponse.check(page, size);
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
        return toResponse(findVisible(principal, id));
    }

    /**
     * Registration step 1: UPI, planting date, relationship and the declaration. No boundary: a hand-drawn
     * polygon is refused, because the boundary comes only from a GPS walk (POST /plots/{id}/walk).
     */
    @Transactional
    public PlotResponse create(AuthPrincipal principal, PlotCreateRequest request) {
        refuseBoundary(request.boundary());
        requireMaize(request.crop());
        checkPlantingDate(request.plantingDate());
        if (!Boolean.TRUE.equals(request.declarationAccepted())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DECLARATION_REQUIRED",
                    "Confirm that you have the right to farm this plot and that a false claim can cancel cover.");
        }
        String upi = request.upi().trim();
        if (!upiPattern.matcher(upi).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPI_INVALID",
                    "Type the UPI exactly as it is on your e-title, for example 1/01/01/01/0001.");
        }
        Plot plot = plots.saveAndFlush(new Plot(principal.userId(), request.name().trim(), upi,
                request.plantingDate(), request.relationshipToPlot()));
        if (request.relationshipToPlot() != RelationshipToPlot.OWNER) {
            flags.raise(plot.getId(), FlagType.NON_OWNER, "Not the registered owner. Upload a lease or the owner's "
                    + "consent; a person reviews it.", null);
        }
        checks.duplicateUpi(plot);
        rules.refresh(plot);
        return toResponse(plots.saveAndFlush(plot));
    }

    @Transactional
    public PlotResponse update(AuthPrincipal principal, long id, PlotUpdateRequest request) {
        refuseBoundary(request.boundary());
        Plot plot = findOwned(principal, id);
        if (!request.plantingDate().equals(plot.getPlantingDate())) {
            // Verified details feed every satellite comparison, so they can't change after the field check.
            if (plot.getVerificationLevel().atLeast(VerificationLevel.FIELD_VERIFIED)) {
                throw new ApiException(HttpStatus.CONFLICT, "PLOT_LOCKED",
                        "The planting date can't change after the field check. Ask your agent.");
            }
            checkPlantingDate(request.plantingDate());
            plot.changePlantingDate(request.plantingDate());
            events.publishEvent(new MonitoringRequested(id));
        }
        plot.rename(request.name().trim());
        // Flush now so the response carries the new updatedAt, which is set when the row is written.
        return toResponse(plots.saveAndFlush(plot));
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

    /**
     * The agent's check at the plot. Verifying raises the plot to FIELD_VERIFIED and needs a photo whose location
     * the agent's app recorded, inside or near the walked boundary. Rejecting needs a reason.
     */
    @Transactional
    public PlotResponse verify(AuthPrincipal agent, long id, VerifyInput input) {
        Plot plot = findVisible(agent, id);
        if (plot.getFarmerId() == agent.userId()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "VERIFIER_IS_FARMER", "You can't verify your own plot.");
        }
        if (plot.getVerificationLevel().atLeast(VerificationLevel.FIELD_VERIFIED)
                || plot.getStatus() == PlotStatus.REJECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "PLOT_NOT_PENDING", "This plot was already checked.");
        }
        PlotStatus before = plot.getStatus();
        LocalDate dateBefore = plot.getPlantingDate();
        String title;
        String body;
        if ("ACTIVE".equals(input.decision())) {
            if (plot.getStatus() != PlotStatus.PENDING_VERIFICATION) {
                throw new ApiException(HttpStatus.CONFLICT, "PLOT_NOT_READY", "The plot can be verified once "
                        + "registration is complete and every flag is resolved. It is " + plot.getStatus() + ".");
            }
            if (input.confirmedPlantingDate() != null) {
                checkPlantingDate(input.confirmedPlantingDate());
                if (!input.confirmedPlantingDate().equals(plot.getPlantingDate())) {
                    events.publishEvent(new MonitoringRequested(id));
                }
                plot.changePlantingDate(input.confirmedPlantingDate());
            }
            if (plot.getPlantingDate() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PLANTING_DATE_REQUIRED",
                        "Confirm the planting date before verifying the plot.");
            }
            BigDecimal distance = checkPhoto(plot, input);
            String photoKey = storage.save("plot-verifications", input.photo());
            recordVerification(plot.getId(), agent.userId(), "VERIFIED", photoKey, input, distance, null);
            plot.verify(agent.userId());
            title = "Your plot is verified";
            body = plot.getName() + " was checked at the plot by your agent.";
        } else {
            if (input.rejectionReason() == null || input.rejectionReason().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "REJECTION_REASON_REQUIRED",
                        "Say why the plot was not accepted, so the farmer can fix it.");
            }
            String photoKey = storage.save("plot-verifications", input.photo());
            recordVerification(plot.getId(), agent.userId(), "REJECTED", photoKey, input, null,
                    input.rejectionReason().trim());
            plot.reject(agent.userId(), input.rejectionReason().trim());
            title = "Your plot was not accepted";
            body = "Your agent could not accept " + plot.getName() + ": " + input.rejectionReason().trim();
        }
        Plot saved = plots.saveAndFlush(plot);

        jdbc.update("""
                INSERT INTO audit_log (actor_id, action, entity, entity_id, before_json, after_json)
                VALUES (?, ?, 'plots', ?, JSON_OBJECT('status', ?, 'plantingDate', ?),
                        JSON_OBJECT('status', ?, 'verificationLevel', ?, 'plantingDate', ?, 'rejectionReason', ?))
                """, agent.userId(), "ACTIVE".equals(input.decision()) ? "PLOT_VERIFIED" : "PLOT_REJECTED", id,
                before.name(), dateBefore == null ? null : dateBefore.toString(), saved.getStatus().name(),
                saved.getVerificationLevel().name(),
                saved.getPlantingDate() == null ? null : saved.getPlantingDate().toString(), saved.getRejectionReason());
        notifyFarmer(saved, "ACTIVE".equals(input.decision()) ? "PLOT_VERIFIED" : "PLOT_REJECTED", title, body);
        return toResponse(saved);
    }

    /** A person resolves a flag after reviewing it. Nothing resolves a dispute automatically. */
    @Transactional
    public PlotResponse resolveFlag(AuthPrincipal reviewer, long plotId, long flagId, String note) {
        Plot plot = findVisible(reviewer, plotId);
        if (plot.getFarmerId() == reviewer.userId()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "VERIFIER_IS_FARMER", "You can't review your own plot.");
        }
        FlagResponse flag = flags.find(plotId, flagId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FLAG_NOT_FOUND", "Flag not found."));
        if (flag.resolvedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "FLAG_ALREADY_RESOLVED", "This flag was already resolved.");
        }
        if (flag.type() == FlagType.LEGACY_DRAWN_BOUNDARY) {
            throw new ApiException(HttpStatus.CONFLICT, "FLAG_NEEDS_WALK",
                    "A drawn boundary is replaced only by walking the plot.");
        }
        if (flag.type() == FlagType.NON_OWNER) {
            String block = tenure.blockReason(plotId);
            if (block != null) {
                throw new ApiException(HttpStatus.CONFLICT, "TENURE_PROOF_MISSING", block);
            }
        }
        flags.resolve(flagId, reviewer.userId(), note.trim());
        jdbc.update("""
                INSERT INTO audit_log (actor_id, action, entity, entity_id, after_json)
                VALUES (?, 'PLOT_FLAG_RESOLVED', 'plots', ?, JSON_OBJECT('flagId', ?, 'type', ?, 'note', ?))
                """, reviewer.userId(), plotId, flagId, flag.type().name(), note.trim());
        rules.refresh(plot);
        return toResponse(plots.saveAndFlush(plot));
    }

    /** The photo must carry the location the app recorded, inside or near the walked boundary. */
    private BigDecimal checkPhoto(Plot plot, VerifyInput input) {
        if (input.photo() == null || input.photo().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_PHOTO_REQUIRED",
                    "Take a photo at the plot with the app before verifying.");
        }
        if (input.photoLat() == null || input.photoLng() == null || input.photoAccuracyM() == null
                || input.photoTakenAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_LOCATION_REQUIRED",
                    "The photo needs the GPS location and time recorded by the app.");
        }
        if (input.photoTakenAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_LOCATION_REQUIRED",
                    "The photo time can't be in the future.");
        }
        String point = "{\"type\":\"Point\",\"coordinates\":[" + input.photoLng() + "," + input.photoLat() + "]}";
        BigDecimal distance = jdbc.queryForObject(
                "SELECT ROUND(ST_Distance(ST_GeomFromGeoJSON(?), boundary), 1) FROM plots WHERE id = ?",
                BigDecimal.class, point, plot.getId());
        Optional<BigDecimal> limit = thresholds.value("verification_photo_max_distance_m");
        // With no distance limit supplied yet, the strictest rule applies: the photo must be inside the boundary.
        BigDecimal allowed = limit.orElse(BigDecimal.ZERO);
        if (distance == null || distance.compareTo(allowed) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_PHOTO_TOO_FAR", limit.isPresent()
                    ? "The photo was taken " + plain(distance) + " m from the walked boundary. It must be within "
                            + plain(allowed) + " m."
                    : "The photo was taken " + plain(distance) + " m outside the walked boundary. Until a distance "
                            + "limit is set, take it inside the boundary.");
        }
        return distance;
    }

    private void recordVerification(long plotId, long verifierId, String result, String photoKey, VerifyInput input,
                                    BigDecimal distance, String note) {
        jdbc.update("""
                INSERT INTO plot_verifications (plot_id, verifier_id, result, photo_key, photo_lat, photo_lng,
                                                photo_accuracy_m, photo_taken_at, distance_to_boundary_m, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, plotId, verifierId, result, photoKey, input.photoLat(), input.photoLng(), input.photoAccuracyM(),
                input.photoTakenAt() == null ? null : Timestamp.from(input.photoTakenAt()), distance, note);
    }

    private void notifyFarmer(Plot plot, String code, String title, String body) {
        jdbc.update("""
                INSERT INTO notifications (user_id, type, code, title, body, link, plot_id, is_demo)
                VALUES (?, 'SYSTEM', ?, ?, ?, ?, ?, ?)
                """, plot.getFarmerId(), code, title, body, "/plots/" + plot.getId(), plot.getId(), plot.isDemo());
    }

    private static void refuseBoundary(Object boundary) {
        if (boundary != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BOUNDARY_NOT_ACCEPTED",
                    "Plot boundaries can't be drawn or sent. Walk the edge of the plot with the app instead.");
        }
    }

    private static void requireMaize(String crop) {
        if (crop != null && !"MAIZE".equals(crop)) {
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

    Plot findVisible(AuthPrincipal principal, long id) {
        Plot plot = find(id);
        if (!canView(principal, plot)) {
            throw notFound();
        }
        return plot;
    }

    Plot findOwned(AuthPrincipal principal, long id) {
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

    PlotResponse toResponse(Plot plot) {
        List<FlagResponse> plotFlags = flags.forPlot(plot.getId());
        return new PlotResponse(plot.getId(), plot.getFarmerId(), plot.getUpi(), plot.getName(), plot.getCrop(),
                plot.getGeojson() == null ? null : toPolygon(plot.getGeojson()), plot.getAreaHa(),
                plot.getWalkedAreaM2(), plot.getDocumentAreaM2(), plot.getAreaDiffPct(),
                plot.getPlantedAreaGeojson() == null ? null : toPolygon(plot.getPlantedAreaGeojson()),
                plot.getPlantingDate(), plot.getRelationshipToPlot(), plot.getStatus(), plot.getVerificationLevel(),
                PlotRules.financialActionsAllowed(plot), plotFlags, rules.checks(plot, plotFlags),
                monitoring.summary(plot.getId()),
                plot.getDataStatus(), plot.isDemo(), plot.getCreatedAt(), plot.getUpdatedAt());
    }

    private GeoJsonPolygon toPolygon(String geojson) {
        try {
            return mapper.readValue(geojson, GeoJsonPolygon.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "?" : value.stripTrailingZeros().toPlainString();
    }
}
