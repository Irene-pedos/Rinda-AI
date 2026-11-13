package com.rinda.backend.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rinda.backend.admin.AdminDtos.AuditLogEntry;
import com.rinda.backend.admin.AdminDtos.EnrollmentRequest;
import com.rinda.backend.admin.AdminDtos.EnrollmentResponse;
import com.rinda.backend.admin.AdminDtos.SeasonRequest;
import com.rinda.backend.admin.AdminDtos.SeasonResponse;
import com.rinda.backend.admin.AdminDtos.ThresholdResponse;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.plot.PlotRules;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The values that drive decisions and money (spec §0 rule 2, §8 admin): thresholds, seasons, cover and payout
 * rates. They come from the team and the insurer and are entered here, never invented. Every change is written to
 * audit_log with the value before and after.
 */
@Service
public class AdminConfigService {

    /** Thresholds that are scores, so they may be negative. Every other threshold is a count, size or percent. */
    private static final Set<String> MAY_BE_NEGATIVE = Set.of("at_risk_anomaly", "watch_anomaly");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PlotRules plotRules;

    public AdminConfigService(JdbcTemplate jdbc, ObjectMapper mapper, PlotRules plotRules) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.plotRules = plotRules;
    }

    // ===== Thresholds =====

    @Transactional(readOnly = true)
    public List<ThresholdResponse> thresholds() {
        return jdbc.query("""
                SELECT t.threshold_key, t.threshold_value, t.status, t.description, u.full_name, t.updated_at
                FROM thresholds t LEFT JOIN users u ON u.id = t.updated_by
                ORDER BY t.threshold_key
                """, (rs, n) -> new ThresholdResponse(rs.getString(1), rs.getBigDecimal(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant()));
    }

    /** The ML service reads thresholds on every run, so a change applies from the next analysis. */
    @Transactional
    public ThresholdResponse updateThreshold(long adminId, String key, BigDecimal value, String status) {
        record Current(BigDecimal value, String status) {
        }
        Current before = jdbc.query("SELECT threshold_value, status FROM thresholds WHERE threshold_key = ?",
                        (rs, n) -> new Current(rs.getBigDecimal(1), rs.getString(2)), key).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "THRESHOLD_NOT_FOUND", "No threshold " + key + "."));
        if (value != null && value.signum() < 0 && !MAY_BE_NEGATIVE.contains(key)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "THRESHOLD_INVALID", key + " can't be negative.");
        }
        if (value == null && !"TO_CONFIRM".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "THRESHOLD_INVALID",
                    "A threshold without a value can only be TO_CONFIRM.");
        }
        jdbc.update("UPDATE thresholds SET threshold_value = ?, status = ?, updated_by = ? WHERE threshold_key = ?",
                value, status, adminId, key);
        audit(adminId, "THRESHOLD_CHANGED", "thresholds", null,
                json("key", key, "value", before.value(), "status", before.status()),
                json("key", key, "value", value, "status", status));
        return thresholds().stream().filter(t -> t.key().equals(key)).findFirst().orElseThrow();
    }

    // ===== Seasons =====

    @Transactional(readOnly = true)
    public List<SeasonResponse> seasons() {
        return jdbc.query("SELECT id, code, type, start_date, end_date, data_status, is_demo FROM seasons ORDER BY code",
                (rs, n) -> season(rs));
    }

    @Transactional
    public SeasonResponse createSeason(long adminId, SeasonRequest r) {
        checkSeason(r);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO seasons (code, type, start_date, end_date, data_status) VALUES (?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, r.code().trim());
                ps.setString(2, r.type());
                ps.setDate(3, r.startDate() == null ? null : Date.valueOf(r.startDate()));
                ps.setDate(4, r.endDate() == null ? null : Date.valueOf(r.endDate()));
                ps.setString(5, r.dataStatus());
                return ps;
            }, keys);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "SEASON_EXISTS", "A season with this code already exists.");
        }
        long id = Objects.requireNonNull(keys.getKey()).longValue();
        SeasonResponse created = season(id);
        audit(adminId, "SEASON_CREATED", "seasons", id, null, mapper.valueToTree(created));
        return created;
    }

    /** Confirming a season turns on the planting-date window check for new plots (question 5). */
    @Transactional
    public SeasonResponse updateSeason(long adminId, long id, SeasonRequest r) {
        SeasonResponse before = season(id);
        checkSeason(r);
        try {
            jdbc.update("UPDATE seasons SET code = ?, type = ?, start_date = ?, end_date = ?, data_status = ? WHERE id = ?",
                    r.code().trim(), r.type(), r.startDate(), r.endDate(), r.dataStatus(), id);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "SEASON_EXISTS", "A season with this code already exists.");
        }
        SeasonResponse after = season(id);
        audit(adminId, "SEASON_CHANGED", "seasons", id, mapper.valueToTree(before), mapper.valueToTree(after));
        return after;
    }

    private static void checkSeason(SeasonRequest r) {
        if ("CONFIRMED".equals(r.dataStatus()) && (r.startDate() == null || r.endDate() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SEASON_DATES_REQUIRED",
                    "A confirmed season needs a start and an end date.");
        }
        if (r.startDate() != null && r.endDate() != null && !r.startDate().isBefore(r.endDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SEASON_DATES_INVALID", "The start must be before the end.");
        }
    }

    private SeasonResponse season(long id) {
        return jdbc.query("SELECT id, code, type, start_date, end_date, data_status, is_demo FROM seasons WHERE id = ?",
                        (rs, n) -> season(rs), id).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SEASON_NOT_FOUND", "Season not found."));
    }

    private static SeasonResponse season(ResultSet rs) throws SQLException {
        return new SeasonResponse(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4, LocalDate.class),
                rs.getObject(5, LocalDate.class), rs.getString(6), rs.getBoolean(7));
    }

    // ===== Enrollments =====

    private static final String ENROLLMENT_SELECT = """
            SELECT e.id, e.plot_id, p.name, u.full_name, e.season_id, s.code, e.status, e.sum_insured, e.premium,
                   e.start_date, e.end_date, e.is_demo
            FROM enrollments e JOIN plots p ON p.id = e.plot_id JOIN users u ON u.id = p.farmer_id
            JOIN seasons s ON s.id = e.season_id
            """;

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> enrollments(Long seasonId, Long plotId, int page, int size) {
        PageResponse.check(page, size);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        if (seasonId != null) {
            where.append(" AND e.season_id = ?");
            args.add(seasonId);
        }
        if (plotId != null) {
            where.append(" AND e.plot_id = ?");
            args.add(plotId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM enrollments e" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        List<EnrollmentResponse> items = jdbc.query(ENROLLMENT_SELECT + where + " ORDER BY e.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> enrollment(rs), args.toArray());
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    /** Cover is set by the cooperative or insurer, not bought in the app (spec §2.2 #5). One per plot and season. */
    @Transactional
    public EnrollmentResponse createEnrollment(long adminId, EnrollmentRequest r) {
        checkEnrollment(r);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO enrollments (plot_id, season_id, status, sum_insured, premium, start_date, end_date,
                                                 is_demo)
                        VALUES (?, ?, ?, ?, ?, ?, ?, (SELECT is_demo FROM plots WHERE id = ?))
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, r.plotId());
                ps.setLong(2, r.seasonId());
                ps.setString(3, r.status());
                ps.setBigDecimal(4, r.sumInsured());
                ps.setBigDecimal(5, r.premium());
                ps.setDate(6, r.startDate() == null ? null : Date.valueOf(r.startDate()));
                ps.setDate(7, r.endDate() == null ? null : Date.valueOf(r.endDate()));
                ps.setLong(8, r.plotId());
                return ps;
            }, keys);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "ENROLLMENT_EXISTS",
                    "This plot already has cover for this season. Change it instead.");
        }
        long id = Objects.requireNonNull(keys.getKey()).longValue();
        EnrollmentResponse created = enrollment(id);
        audit(adminId, "ENROLLMENT_CREATED", "enrollments", id, null, mapper.valueToTree(created));
        return created;
    }

    @Transactional
    public EnrollmentResponse updateEnrollment(long adminId, long id, EnrollmentRequest r) {
        EnrollmentResponse before = enrollment(id);
        if (before.plotId() != r.plotId() || before.seasonId() != r.seasonId()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ENROLLMENT_INVALID",
                    "The plot and season of a cover can't change. Cancel it and create a new one.");
        }
        checkEnrollment(r);
        jdbc.update("""
                UPDATE enrollments SET status = ?, sum_insured = ?, premium = ?, start_date = ?, end_date = ? WHERE id = ?
                """, r.status(), r.sumInsured(), r.premium(), r.startDate(), r.endDate(), id);
        EnrollmentResponse after = enrollment(id);
        audit(adminId, "ENROLLMENT_CHANGED", "enrollments", id, mapper.valueToTree(before), mapper.valueToTree(after));
        return after;
    }

    private void checkEnrollment(EnrollmentRequest r) {
        String plotStatus = jdbc.queryForList("SELECT status FROM plots WHERE id = ?", String.class, r.plotId())
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found."));
        if (jdbc.queryForList("SELECT id FROM seasons WHERE id = ?", Long.class, r.seasonId()).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SEASON_NOT_FOUND", "Season not found.");
        }
        // Cover is a financial action: FIELD_VERIFIED or higher and not disputed (docs/01-plot-verification.md).
        if ("COVERED".equals(r.status()) && plotRules.financialBlock(r.plotId()) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "PLOT_NOT_ACTIVE",
                    "Only a plot checked in the field (FIELD_VERIFIED) and not disputed can be covered.");
        }
        if (r.startDate() != null && r.endDate() != null && !r.startDate().isBefore(r.endDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ENROLLMENT_INVALID", "The start must be before the end.");
        }
    }

    private EnrollmentResponse enrollment(long id) {
        return jdbc.query(ENROLLMENT_SELECT + " WHERE e.id = ?", (rs, n) -> enrollment(rs), id).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Cover not found."));
    }

    private static EnrollmentResponse enrollment(ResultSet rs) throws SQLException {
        return new EnrollmentResponse(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5),
                rs.getString(6), rs.getString(7), rs.getBigDecimal(8), rs.getBigDecimal(9),
                rs.getObject(10, LocalDate.class), rs.getObject(11, LocalDate.class), rs.getBoolean(12));
    }

    // ===== Claim payout rate =====

    /**
     * The insurer's payout rate for this claim's loss level (spec §3.7). Entered by an admin from the insurer's
     * table, never computed. The amount is worked out again at payout from sum insured × rate.
     */
    @Transactional
    public void setPayoutRate(long adminId, long claimId, BigDecimal rate) {
        record Claim(String status, BigDecimal rate) {
        }
        Claim claim = jdbc.query("SELECT status, payout_rate FROM claims WHERE id = ? FOR UPDATE",
                        (rs, n) -> new Claim(rs.getString(1), rs.getBigDecimal(2)), claimId).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found."));
        if (Set.of("PAID", "FINAL", "DECLINED").contains(claim.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "CLAIM_CLOSED_FOR_RATE",
                    "The payout rate can't change on a " + claim.status() + " claim.");
        }
        jdbc.update("UPDATE claims SET payout_rate = ?, amount = NULL WHERE id = ?", rate, claimId);
        audit(adminId, "CLAIM_PAYOUT_RATE_SET", "claims", claimId, json("payoutRate", claim.rate()),
                json("payoutRate", rate));
    }

    // ===== Audit log =====

    @Transactional(readOnly = true)
    public PageResponse<AuditLogEntry> auditLog(String entity, Long entityId, String action, Long actorId,
                                                int page, int size) {
        PageResponse.check(page, size);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        if (entity != null) {
            where.append(" AND a.entity = ?");
            args.add(entity);
        }
        if (entityId != null) {
            where.append(" AND a.entity_id = ?");
            args.add(entityId);
        }
        if (action != null) {
            where.append(" AND a.action = ?");
            args.add(action);
        }
        if (actorId != null) {
            where.append(" AND a.actor_id = ?");
            args.add(actorId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log a" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        List<AuditLogEntry> items = jdbc.query("""
                SELECT a.id, a.action, a.entity, a.entity_id, a.actor_id, u.full_name, a.before_json, a.after_json,
                       a.occurred_at
                FROM audit_log a LEFT JOIN users u ON u.id = a.actor_id
                """ + where + " ORDER BY a.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new AuditLogEntry(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, Long.class), rs.getObject(5, Long.class), rs.getString(6), read(rs.getString(7)),
                        read(rs.getString(8)), rs.getTimestamp(9).toInstant()), args.toArray());
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    // ===== Helpers =====

    void audit(long actorId, String action, String entity, Long entityId, JsonNode before, JsonNode after) {
        jdbc.update("""
                INSERT INTO audit_log (actor_id, action, entity, entity_id, before_json, after_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """, actorId, action, entity, entityId, write(before), write(after));
    }

    private ObjectNode json(Object... pairs) {
        ObjectNode node = mapper.createObjectNode();
        for (int i = 0; i < pairs.length; i += 2) {
            node.set((String) pairs[i], mapper.valueToTree(pairs[i + 1]));
        }
        return node;
    }

    private String write(JsonNode node) {
        try {
            return node == null ? null : mapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private JsonNode read(String json) {
        try {
            return json == null ? null : mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return mapper.getNodeFactory().textNode(json);
        }
    }
}
