package com.rinda.backend.claim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.claim.ClaimDtos.ClaimResponse;
import com.rinda.backend.claim.ClaimDtos.DecisionResponse;
import com.rinda.backend.claim.ClaimDtos.PayoutResponse;
import com.rinda.backend.claim.ClaimDtos.ReviewResponse;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.PageResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads claims. Visibility follows the plot: a farmer sees claims on their own plots, an agent those in their
 * cooperative, and officers and admins all of them. Someone else's claim gets the same 404 as a missing one.
 */
@Service
public class ClaimService {

    private static final String SELECT = """
            SELECT c.id, c.plot_id, p.name, c.season_id, s.code, c.trigger_date, c.status, c.payout_rate, c.amount,
                   %s, c.model_version, c.is_demo, c.created_at,
                   d.decision, d.reason, d.decided_at,
                   py.amount, py.reference, py.status, py.is_simulated, py.created_at,
                   rr.farmer_message, rr.status, rr.answer, rr.created_at,
                   (c.status IN ('APPROVED', 'DECLINED') AND d.id IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM review_requests r3 WHERE r3.decision_id = d.id))
            FROM claims c
            JOIN plots p ON p.id = c.plot_id
            JOIN seasons s ON s.id = c.season_id
            LEFT JOIN claim_decisions d ON d.id = (
                SELECT MAX(d2.id) FROM claim_decisions d2 WHERE d2.claim_id = c.id)
            LEFT JOIN payouts py ON py.claim_id = c.id
            LEFT JOIN review_requests rr ON rr.id = (
                SELECT MAX(r2.id) FROM review_requests r2 WHERE r2.claim_id = c.id)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ClaimService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Newest first. The list leaves out the evidence snapshot to stay small; GET /claims/{id} has it. */
    @Transactional(readOnly = true)
    public PageResponse<ClaimResponse> list(AuthPrincipal principal, int page, int size) {
        PageResponse.check(page, size);
        Visibility v = visibility(principal);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM claims c JOIN plots p ON p.id = c.plot_id WHERE "
                + v.where(), Long.class, v.args());
        List<Object> args = new ArrayList<>(List.of(v.args()));
        args.add(size);
        args.add((long) page * size);
        List<ClaimResponse> items = jdbc.query(SELECT.formatted("NULL") + " WHERE " + v.where()
                        + " ORDER BY c.trigger_date DESC, c.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> map(rs), args.toArray());
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public ClaimResponse get(AuthPrincipal principal, long id) {
        Visibility v = visibility(principal);
        List<Object> args = new ArrayList<>(List.of(id));
        args.addAll(List.of(v.args()));
        return jdbc.query(SELECT.formatted("c.evidence_snapshot_json") + " WHERE c.id = ? AND " + v.where(),
                        (rs, n) -> map(rs), args.toArray())
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found."));
    }

    private record Visibility(String where, Object[] args) {
    }

    private Visibility visibility(AuthPrincipal principal) {
        return switch (principal.role()) {
            case FARMER -> new Visibility("p.farmer_id = ?", new Object[]{principal.userId()});
            case AGENT -> new Visibility("""
                    p.farmer_id IN (SELECT f.user_id FROM farmers f
                                    JOIN agents a ON a.cooperative_id = f.cooperative_id
                                    WHERE a.user_id = ?)""", new Object[]{principal.userId()});
            case OFFICER, ADMIN -> new Visibility("TRUE", new Object[]{});
        };
    }

    private ClaimResponse map(ResultSet rs) throws SQLException {
        String decision = rs.getString(14);
        Timestamp decidedAt = rs.getTimestamp(16);
        return new ClaimResponse(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5),
                rs.getObject(6, LocalDate.class), rs.getString(7), rs.getBigDecimal(8), rs.getBigDecimal(9),
                readJson(rs.getString(10)),
                decision == null ? null : new DecisionResponse(decision, rs.getString(15),
                        decidedAt == null ? null : decidedAt.toInstant()),
                rs.getString(11), rs.getBoolean(12), rs.getTimestamp(13).toInstant(),
                rs.getString(18) == null ? null : new PayoutResponse(rs.getBigDecimal(17), rs.getString(18),
                        rs.getString(19), rs.getBoolean(20), rs.getTimestamp(21).toInstant()),
                rs.getString(22) == null ? null : new ReviewResponse(rs.getString(22), rs.getString(23),
                        rs.getString(24), rs.getTimestamp(25).toInstant()),
                rs.getBoolean(26));
    }

    private JsonNode readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
