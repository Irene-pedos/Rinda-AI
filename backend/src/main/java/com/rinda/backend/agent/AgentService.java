package com.rinda.backend.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rinda.backend.agent.AgentDtos.FarmerSummaryResponse;
import com.rinda.backend.agent.AgentDtos.FieldVisitResponse;
import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.common.PhotoStorage;
import com.rinda.backend.common.PhotoStorage.StoredPhoto;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cooperative agents: their farmers, and field visits (spec §9.8). A field visit is ground truth for the officer.
 * It never decides a claim; it is attached to the plot's open claim as evidence.
 */
@Service
public class AgentService {

    static final Set<String> CONDITIONS = Set.of("GOOD", "STRESSED", "SEVERE_LOSS", "FAILED");
    static final Set<String> CAUSES = Set.of("WATER_DEFICIT", "EXCESS_WATER", "NUTRIENT", "OTHER");
    static final Set<String> STAGES = Set.of("EMERGENCE", "VEGETATIVE", "FLOWERING", "GRAIN", "MATURITY");

    private static final String SELECT_VISIT = """
            SELECT v.id, v.plot_id, p.name, v.agent_id, v.plant_condition, v.observed_cause, v.growth_stage,
                   v.checklist_json, v.notes, v.lat, v.lng, v.visited_at, v.claim_id, v.photo_url IS NOT NULL,
                   v.created_at, p.is_demo
            FROM field_visits v JOIN plots p ON p.id = v.plot_id
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PhotoStorage photos;

    public AgentService(JdbcTemplate jdbc, ObjectMapper mapper, PhotoStorage photos) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.photos = photos;
    }

    public record VisitInput(long plotId, String plantCondition, String observedCause, String growthStage,
                             Boolean moistureChecked, Boolean standDensityChecked, Boolean farmerPresent,
                             String notes, BigDecimal lat, BigDecimal lng, Instant visitedAt, String clientId) {
    }

    public record Created(FieldVisitResponse visit, boolean isNew) {
    }

    /** Farmers of the agent's cooperative, with what needs the agent's attention. */
    @Transactional(readOnly = true)
    public List<FarmerSummaryResponse> farmers(long agentId) {
        long cooperative = cooperativeOf(agentId);
        return jdbc.query("""
                SELECT u.id, u.full_name, u.phone,
                       (SELECT COUNT(*) FROM plots p WHERE p.farmer_id = u.id AND p.status <> 'ARCHIVED'),
                       (SELECT COUNT(*) FROM plots p WHERE p.farmer_id = u.id AND p.status = 'PENDING_VERIFICATION'),
                       (SELECT COUNT(*) FROM plots p WHERE p.farmer_id = u.id AND p.status <> 'ARCHIVED' AND
                            (SELECT s.status FROM plot_status s WHERE s.plot_id = p.id
                             ORDER BY s.status_date DESC, s.id DESC LIMIT 1) = 'AT_RISK'),
                       (SELECT COUNT(*) FROM claims c JOIN plots p ON p.id = c.plot_id
                        WHERE p.farmer_id = u.id AND c.status NOT IN ('PAID', 'FINAL')),
                       u.is_demo
                FROM farmers f JOIN users u ON u.id = f.user_id
                WHERE f.cooperative_id = ?
                ORDER BY 5 DESC, 6 DESC, u.full_name
                """, (rs, n) -> new FarmerSummaryResponse(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getBoolean(8)), cooperative);
    }

    /** Records a visit. Sending the same clientId again (an offline outbox retry) returns the first one. */
    @Transactional
    public Created recordVisit(long agentId, VisitInput in, MultipartFile photo) {
        check(CONDITIONS.contains(in.plantCondition()), "plantCondition must be GOOD, STRESSED, SEVERE_LOSS or FAILED.");
        check(CAUSES.contains(in.observedCause()), "observedCause must be WATER_DEFICIT, EXCESS_WATER, NUTRIENT or OTHER.");
        check(STAGES.contains(in.growthStage()), "growthStage must be EMERGENCE, VEGETATIVE, FLOWERING, GRAIN or MATURITY.");
        check(in.visitedAt() != null && !in.visitedAt().isAfter(Instant.now().plus(Duration.ofMinutes(10))),
                "visitedAt is required and can't be in the future.");
        check((in.lat() == null) == (in.lng() == null), "Send both lat and lng, or neither.");
        check(in.lat() == null || (in.lat().abs().compareTo(BigDecimal.valueOf(90)) <= 0
                && in.lng().abs().compareTo(BigDecimal.valueOf(180)) <= 0), "lat or lng is out of range.");
        check(in.clientId() == null || in.clientId().length() <= 40, "clientId is at most 40 characters.");
        check(in.notes() == null || in.notes().length() <= 2000, "notes is at most 2000 characters.");

        String plotName = plotInCooperative(agentId, in.plotId());
        if (in.clientId() != null) {
            Optional<Long> existing = jdbc.queryForList(
                    "SELECT id FROM field_visits WHERE agent_id = ? AND client_id = ?", Long.class, agentId, in.clientId())
                    .stream().findFirst();
            if (existing.isPresent()) {
                return new Created(visitById(existing.get()), false);
            }
        }

        record OpenClaim(long id, String status) {
        }
        Optional<OpenClaim> claim = jdbc.query("""
                SELECT id, status FROM claims WHERE plot_id = ? AND status NOT IN ('PAID', 'FINAL')
                ORDER BY id DESC LIMIT 1
                """, (rs, n) -> new OpenClaim(rs.getLong(1), rs.getString(2)), in.plotId()).stream().findFirst();

        ObjectNode checklist = mapper.createObjectNode();
        checklist.put("moistureChecked", in.moistureChecked());
        checklist.put("standDensityChecked", in.standDensityChecked());
        checklist.put("farmerPresent", in.farmerPresent());
        String photoKey = photos.save("field-visits", photo);

        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO field_visits (plot_id, agent_id, checklist_json, plant_condition, observed_cause,
                                              growth_stage, photo_url, lat, lng, visited_at, notes, client_id, claim_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, in.plotId());
            ps.setLong(2, agentId);
            ps.setString(3, checklist.toString());
            ps.setString(4, in.plantCondition());
            ps.setString(5, in.observedCause());
            ps.setString(6, in.growthStage());
            ps.setString(7, photoKey);
            ps.setBigDecimal(8, in.lat());
            ps.setBigDecimal(9, in.lng());
            ps.setTimestamp(10, Timestamp.from(in.visitedAt()));
            ps.setString(11, in.notes() == null || in.notes().isBlank() ? null : in.notes().trim());
            ps.setString(12, in.clientId());
            ps.setObject(13, claim.map(OpenClaim::id).orElse(null));
            return ps;
        }, keys);
        long id = Objects.requireNonNull(keys.getKey()).longValue();

        // Officers waiting for this visit are told. The claim status itself is only changed by an officer.
        claim.filter(c -> "FIELD_VISIT_REQUESTED".equals(c.status())).ifPresent(c -> {
            boolean demo = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT is_demo FROM claims WHERE id = ?", Boolean.class, c.id()));
            for (long officer : jdbc.queryForList("SELECT id FROM users WHERE role = 'OFFICER'", Long.class)) {
                jdbc.update("""
                        INSERT INTO notifications (user_id, type, code, title, body, link, plot_id, is_demo)
                        VALUES (?, 'CLAIM', 'FIELD_VISIT_RECORDED', 'Field visit recorded', ?, ?, ?, ?)
                        """, officer, "The agent recorded the field visit to " + plotName + ".",
                        "/claims/" + c.id(), in.plotId(), demo);
            }
        });
        return new Created(visitById(id), true);
    }

    /** Agents see visits in their cooperative; officers and admins see all. Farmers can't (403 in the controller). */
    @Transactional(readOnly = true)
    public PageResponse<FieldVisitResponse> visits(AuthPrincipal principal, Long plotId, Long claimId,
                                                   int page, int size) {
        PageResponse.check(page, size);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE ").append(visibility(principal, args));
        if (plotId != null) {
            where.append(" AND v.plot_id = ?");
            args.add(plotId);
        }
        if (claimId != null) {
            where.append(" AND v.claim_id = ?");
            args.add(claimId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM field_visits v JOIN plots p ON p.id = v.plot_id"
                + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        List<FieldVisitResponse> items = jdbc.query(SELECT_VISIT + where + " ORDER BY v.visited_at DESC, v.id DESC"
                + " LIMIT ? OFFSET ?", (rs, n) -> mapVisit(rs), args.toArray());
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public StoredPhoto visitPhoto(AuthPrincipal principal, long id) {
        List<Object> args = new ArrayList<>(List.of(id));
        String where = visibility(principal, args);
        String key = jdbc.queryForList("SELECT v.photo_url FROM field_visits v JOIN plots p ON p.id = v.plot_id"
                        + " WHERE v.id = ? AND " + where, String.class, args.toArray())
                .stream().filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "Photo not found."));
        return photos.read(key);
    }

    // ===== Helpers =====

    long cooperativeOf(long agentId) {
        return jdbc.queryForList("SELECT cooperative_id FROM agents WHERE user_id = ?", Long.class, agentId)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "AGENT_NOT_LINKED",
                        "This agent is not linked to a cooperative."));
    }

    /** The plot's name if it belongs to a farmer of the agent's cooperative; otherwise the usual 404. */
    private String plotInCooperative(long agentId, long plotId) {
        long cooperative = cooperativeOf(agentId);
        return jdbc.query("""
                SELECT p.name FROM plots p JOIN farmers f ON f.user_id = p.farmer_id
                WHERE p.id = ? AND f.cooperative_id = ? AND p.status <> 'ARCHIVED'
                """, (rs, n) -> rs.getString(1), plotId, cooperative).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found."));
    }

    private String visibility(AuthPrincipal principal, List<Object> args) {
        return switch (principal.role()) {
            case AGENT -> {
                args.add(principal.userId());
                yield """
                        p.farmer_id IN (SELECT f.user_id FROM farmers f
                                        JOIN agents a ON a.cooperative_id = f.cooperative_id WHERE a.user_id = ?)""";
            }
            case OFFICER, ADMIN -> "TRUE";
            case FARMER -> "FALSE";
        };
    }

    private FieldVisitResponse visitById(long id) {
        return jdbc.query(SELECT_VISIT + " WHERE v.id = ?", (rs, n) -> mapVisit(rs), id).get(0);
    }

    private FieldVisitResponse mapVisit(ResultSet rs) throws SQLException {
        long id = rs.getLong(1);
        return new FieldVisitResponse(id, rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5),
                rs.getString(6), rs.getString(7), readJson(rs.getString(8)), rs.getString(9), rs.getBigDecimal(10),
                rs.getBigDecimal(11), rs.getTimestamp(12).toInstant(), rs.getObject(13, Long.class),
                rs.getBoolean(14) ? "/api/v1/field-visits/" + id + "/photo" : null,
                rs.getTimestamp(15).toInstant(), rs.getBoolean(16));
    }

    private JsonNode readJson(String json) {
        try {
            return json == null ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return mapper.createObjectNode();
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
        }
    }
}
