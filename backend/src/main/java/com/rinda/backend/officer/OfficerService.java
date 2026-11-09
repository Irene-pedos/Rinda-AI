package com.rinda.backend.officer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rinda.backend.agent.AgentService;
import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.claim.ClaimDtos.ClaimResponse;
import com.rinda.backend.claim.ClaimService;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.officer.OfficerDtos.AuditEntry;
import com.rinda.backend.officer.OfficerDtos.CaseFile;
import com.rinda.backend.officer.OfficerDtos.Cover;
import com.rinda.backend.officer.OfficerDtos.Decision;
import com.rinda.backend.officer.OfficerDtos.Farmer;
import com.rinda.backend.officer.OfficerDtos.PayoutAccount;
import com.rinda.backend.officer.OfficerDtos.Plot;
import com.rinda.backend.officer.OfficerDtos.QueueItem;
import com.rinda.backend.officer.OfficerDtos.Review;
import com.rinda.backend.officer.OfficerDtos.Summary;
import com.rinda.backend.report.ProblemReportService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The officer dashboard: the claims queue, one claim's whole case file, and a summary. */
@Service
public class OfficerService {

    /** Needs-attention first: open reviews, then new claims, then field visits, then approved-but-unpaid. */
    private static final String PRIORITY = """
            CASE c.status WHEN 'REVIEW_REQUESTED' THEN 0 WHEN 'UNDER_REVIEW' THEN 1
                          WHEN 'FIELD_VISIT_REQUESTED' THEN 2 WHEN 'APPROVED' THEN 3 ELSE 4 END""";

    private static final List<String> TRIGGER_KEYS = List.of("trigger_at_risk_days", "trigger_forecast_pct",
            "trigger_rain_deficit_pct");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClaimService claims;
    private final ProblemReportService reports;
    private final AgentService agents;

    public OfficerService(JdbcTemplate jdbc, ObjectMapper mapper, ClaimService claims, ProblemReportService reports,
                          AgentService agents) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.claims = claims;
        this.reports = reports;
        this.agents = agents;
    }

    public record QueueFilter(List<String> statuses, String flag, Boolean demo, Long cooperativeId, String search) {
    }

    @Transactional(readOnly = true)
    public PageResponse<QueueItem> queue(QueueFilter filter, int page, int size) {
        PageResponse.check(page, size);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            where.append(" AND c.status IN (").append(String.join(",", filter.statuses().stream().map(s -> "?").toList()))
                    .append(')');
            args.addAll(filter.statuses());
        }
        if (filter.flag() != null) {
            where.append(" AND JSON_CONTAINS(c.evidence_snapshot_json->'$.reviewFlags', JSON_QUOTE(?))");
            args.add(filter.flag());
        }
        if (filter.demo() != null) {
            where.append(" AND c.is_demo = ?");
            args.add(filter.demo());
        }
        if (filter.cooperativeId() != null) {
            where.append(" AND f.cooperative_id = ?");
            args.add(filter.cooperativeId());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            where.append(" AND (p.name LIKE ? OR u.full_name LIKE ? OR u.phone LIKE ?)");
            String like = "%" + filter.search().trim().replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String from = """
                FROM claims c
                JOIN plots p ON p.id = c.plot_id
                JOIN users u ON u.id = p.farmer_id
                JOIN farmers f ON f.user_id = u.id
                JOIN cooperatives co ON co.id = f.cooperative_id
                JOIN seasons s ON s.id = c.season_id
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        List<QueueItem> items = jdbc.query("""
                SELECT c.id, c.status, c.trigger_date,
                       CASE WHEN c.status IN ('PAID', 'FINAL', 'DECLINED') THEN NULL
                            ELSE DATEDIFF(CURRENT_DATE, DATE(c.created_at)) END,
                       p.id, p.name, p.area_ha, u.id, u.full_name, u.phone, co.name, s.code,
                       COALESCE(c.evidence_snapshot_json->'$.reviewFlags', JSON_ARRAY()),
                       (SELECT COUNT(*) FROM problem_reports r WHERE r.plot_id = p.id),
                       (SELECT COUNT(*) FROM field_visits v WHERE v.plot_id = p.id),
                       EXISTS (SELECT 1 FROM review_requests rr WHERE rr.claim_id = c.id AND rr.status = 'OPEN'),
                       c.payout_rate, c.amount, c.model_version, c.is_demo
                """ + from + where + " ORDER BY " + PRIORITY + ", c.trigger_date, c.id LIMIT ? OFFSET ?",
                (rs, n) -> new QueueItem(rs.getLong(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                        rs.getObject(4, Integer.class), rs.getLong(5), rs.getString(6), rs.getBigDecimal(7),
                        rs.getLong(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        readJson(rs.getString(13)), rs.getInt(14), rs.getInt(15), rs.getBoolean(16),
                        rs.getBigDecimal(17), rs.getBigDecimal(18), rs.getString(19), rs.getBoolean(20)),
                args.toArray());
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public CaseFile caseFile(AuthPrincipal officer, long claimId) {
        ClaimResponse claim = claims.get(officer, claimId); // 404 if missing

        Farmer farmer = jdbc.queryForObject("""
                SELECT u.id, u.full_name, u.phone, co.name, u.is_demo
                FROM plots p JOIN users u ON u.id = p.farmer_id JOIN farmers f ON f.user_id = u.id
                JOIN cooperatives co ON co.id = f.cooperative_id
                WHERE p.id = ?
                """, (rs, n) -> new Farmer(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getBoolean(5)), claim.plotId());

        Plot plot = jdbc.queryForObject("""
                SELECT p.id, p.name, p.area_ha, p.planting_date, p.status, p.data_status, v.full_name, p.is_demo
                FROM plots p LEFT JOIN users v ON v.id = p.verified_by WHERE p.id = ?
                """, (rs, n) -> new Plot(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3),
                rs.getObject(4, LocalDate.class), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getBoolean(8)), claim.plotId());

        Cover cover = jdbc.query("""
                SELECT e.status, s.code, e.sum_insured, e.premium, e.start_date, e.end_date
                FROM enrollments e JOIN seasons s ON s.id = e.season_id
                WHERE e.plot_id = ? AND e.season_id = ?
                """, (rs, n) -> new Cover(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4),
                rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class)), claim.plotId(), claim.seasonId())
                .stream().findFirst().orElse(null);

        PayoutAccount account = jdbc.query("SELECT provider, msisdn, verified FROM payout_accounts WHERE user_id = ?",
                (rs, n) -> new PayoutAccount(rs.getString(1), mask(rs.getString(2)), rs.getBoolean(3)), farmer.id())
                .stream().findFirst().orElse(null);

        List<Decision> decisions = jdbc.query("""
                SELECT d.decision, d.reason, u.full_name, d.decided_at, d.answers_review_id IS NOT NULL
                FROM claim_decisions d JOIN users u ON u.id = d.officer_id
                WHERE d.claim_id = ? ORDER BY d.id
                """, (rs, n) -> new Decision(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getTimestamp(4).toInstant(), rs.getBoolean(5)), claimId);

        List<Review> reviews = jdbc.query("""
                SELECT farmer_message, status, answer, created_at, answered_at FROM review_requests
                WHERE claim_id = ? ORDER BY id
                """, (rs, n) -> {
            Timestamp answered = rs.getTimestamp(5);
            return new Review(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(),
                    answered == null ? null : answered.toInstant());
        }, claimId);

        List<AuditEntry> history = jdbc.query("""
                SELECT a.action, u.full_name, u.role, a.before_json, a.after_json, a.occurred_at
                FROM audit_log a LEFT JOIN users u ON u.id = a.actor_id
                WHERE a.entity = 'claims' AND a.entity_id = ? ORDER BY a.id
                """, (rs, n) -> new AuditEntry(rs.getString(1), rs.getString(2), rs.getString(3),
                readJson(rs.getString(4)), readJson(rs.getString(5)), rs.getTimestamp(6).toInstant()), claimId);

        return new CaseFile(claim, farmer, plot, cover, account, decisions, reviews,
                reports.list(officer, claim.plotId(), null, 0, 50).items(),
                agents.visits(officer, claim.plotId(), null, 0, 50).items(), history);
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) FROM claims GROUP BY status ORDER BY status",
                rs -> {
                    byStatus.put(rs.getString(1), rs.getLong(2));
                });
        Map<String, Long> plots = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) FROM plots GROUP BY status ORDER BY status",
                rs -> {
                    plots.put(rs.getString(1), rs.getLong(2));
                });
        long awaiting = count("SELECT COUNT(*) FROM claims WHERE status IN ('UNDER_REVIEW', 'FIELD_VISIT_REQUESTED', "
                + "'REVIEW_REQUESTED')");
        long openReviews = count("SELECT COUNT(*) FROM review_requests WHERE status = 'OPEN'");
        long flagged = count("""
                SELECT COUNT(*) FROM claims WHERE status NOT IN ('PAID', 'FINAL', 'DECLINED')
                  AND JSON_LENGTH(COALESCE(evidence_snapshot_json->'$.reviewFlags', JSON_ARRAY())) > 0
                """);
        long approvedUnpaid = count("SELECT COUNT(*) FROM claims WHERE status = 'APPROVED'");
        BigDecimal paid = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM payouts WHERE status = 'PAID'",
                BigDecimal.class);
        List<String> missing = jdbc.queryForList("""
                SELECT threshold_key FROM thresholds WHERE threshold_value IS NULL AND threshold_key IN (?, ?, ?)
                ORDER BY threshold_key
                """, String.class, TRIGGER_KEYS.toArray());
        JsonNode latestRun = jdbc.query("""
                SELECT id, source, status, plots_total, plots_done, plots_failed, created_at, finished_at
                FROM analysis_runs ORDER BY id DESC LIMIT 1
                """, (rs, n) -> {
            ObjectNode run = mapper.createObjectNode();
            run.put("id", rs.getLong(1));
            run.put("source", rs.getString(2));
            run.put("status", rs.getString(3));
            run.put("plotsTotal", rs.getInt(4));
            run.put("plotsDone", rs.getInt(5));
            run.put("plotsFailed", rs.getInt(6));
            run.put("createdAt", rs.getTimestamp(7).toInstant().toString());
            Timestamp finished = rs.getTimestamp(8);
            run.put("finishedAt", finished == null ? null : finished.toInstant().toString());
            return (JsonNode) run;
        }).stream().findFirst().orElse(null);
        return new Summary(byStatus, awaiting, openReviews, flagged, approvedUnpaid, paid, plots, missing, latestRun);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    /** Officers need to know an account exists and is verified, not the full number. */
    static String mask(String msisdn) {
        return msisdn == null || msisdn.length() < 4 ? "***" : "*".repeat(msisdn.length() - 3)
                + msisdn.substring(msisdn.length() - 3);
    }

    private JsonNode readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return mapper.getNodeFactory().textNode(json);
        }
    }
}
