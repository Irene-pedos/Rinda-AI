package com.rinda.backend.report;

import com.rinda.backend.auth.AuthPrincipal;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.common.PhotoStorage;
import com.rinda.backend.common.PhotoStorage.StoredPhoto;
import com.rinda.backend.report.ProblemReportDtos.ProblemReportResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
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
 * "Report a problem" (spec §2.2 #1). A report is evidence only: it is attached to the plot's open claim if there
 * is one, can ask for an agent visit, and never creates a claim or a payout.
 */
@Service
public class ProblemReportService {

    static final Set<String> REASONS = Set.of("DRY_LEAVES", "YELLOW_LEAVES", "FLOODING", "OTHER");

    private static final String SELECT = """
            SELECT r.id, r.plot_id, p.name, r.farmer_id, r.reason, r.note, r.agent_visit_requested, r.claim_id,
                   r.photo_url IS NOT NULL, r.reported_at, r.created_at, p.is_demo
            FROM problem_reports r JOIN plots p ON p.id = r.plot_id
            """;

    private final JdbcTemplate jdbc;
    private final PhotoStorage photos;

    public ProblemReportService(JdbcTemplate jdbc, PhotoStorage photos) {
        this.jdbc = jdbc;
        this.photos = photos;
    }

    public record Created(ProblemReportResponse report, boolean isNew) {
    }

    /** Sending the same clientId again (an offline outbox retry) returns the first report, unchanged. */
    @Transactional
    public Created create(long farmerId, long plotId, String reason, String note, boolean agentVisitRequested,
                         String clientId, Instant reportedAt, MultipartFile photo) {
        if (!REASONS.contains(reason)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_REASON_INVALID",
                    "Reason must be DRY_LEAVES, YELLOW_LEAVES, FLOODING or OTHER.");
        }
        if ((clientId != null && clientId.length() > 40) || (note != null && note.length() > 2000)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "clientId is at most 40 characters and note at most 2000.");
        }
        String plotName = jdbc.query("SELECT name FROM plots WHERE id = ? AND farmer_id = ? AND status <> 'ARCHIVED'",
                        (rs, n) -> rs.getString(1), plotId, farmerId).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found."));

        if (clientId != null) {
            Optional<Long> existing = jdbc.queryForList(
                    "SELECT id FROM problem_reports WHERE farmer_id = ? AND client_id = ?", Long.class, farmerId, clientId)
                    .stream().findFirst();
            if (existing.isPresent()) {
                return new Created(byId(existing.get()), false);
            }
        }

        Long openClaim = jdbc.queryForList("""
                SELECT id FROM claims WHERE plot_id = ? AND status NOT IN ('PAID', 'FINAL') ORDER BY id DESC LIMIT 1
                """, Long.class, plotId).stream().findFirst().orElse(null);
        String photoKey = photos.save("problem-reports", photo);
        String cleanNote = note == null || note.isBlank() ? null : note.trim();

        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO problem_reports (plot_id, farmer_id, reason, note, photo_url, agent_visit_requested,
                                                 claim_id, client_id, reported_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, plotId);
            ps.setLong(2, farmerId);
            ps.setString(3, reason);
            ps.setString(4, cleanNote);
            ps.setString(5, photoKey);
            ps.setBoolean(6, agentVisitRequested);
            ps.setObject(7, openClaim);
            ps.setString(8, clientId);
            ps.setTimestamp(9, reportedAt == null ? null : Timestamp.from(reportedAt));
            return ps;
        }, keys);
        long id = Objects.requireNonNull(keys.getKey()).longValue();

        if (agentVisitRequested) {
            boolean demo = Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_demo FROM plots WHERE id = ?",
                    Boolean.class, plotId));
            for (long agent : agentsOf(farmerId)) {
                jdbc.update("""
                        INSERT INTO notifications (user_id, type, code, title, body, link, plot_id, is_demo)
                        VALUES (?, 'SYSTEM', 'AGENT_VISIT_REQUESTED', 'A farmer asked for a visit', ?, ?, ?, ?)
                        """, agent, "A farmer reported a problem at " + plotName + " and asked for a field visit.",
                        "/plots/" + plotId, plotId, demo);
            }
        }
        return new Created(byId(id), true);
    }

    /** Farmers see their own reports, agents those in their cooperative, officers and admins all. */
    @Transactional(readOnly = true)
    public PageResponse<ProblemReportResponse> list(AuthPrincipal principal, Long plotId, Long claimId,
                                                    int page, int size) {
        PageResponse.check(page, size);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE ").append(visibility(principal, args));
        if (plotId != null) {
            where.append(" AND r.plot_id = ?");
            args.add(plotId);
        }
        if (claimId != null) {
            where.append(" AND r.claim_id = ?");
            args.add(claimId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM problem_reports r JOIN plots p ON p.id = r.plot_id"
                + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        List<ProblemReportResponse> items = jdbc.query(SELECT + where + " ORDER BY r.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> map(rs), args.toArray());
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public StoredPhoto photo(AuthPrincipal principal, long id) {
        List<Object> args = new ArrayList<>(List.of(id));
        String where = visibility(principal, args);
        String key = jdbc.queryForList("SELECT r.photo_url FROM problem_reports r JOIN plots p ON p.id = r.plot_id"
                        + " WHERE r.id = ? AND " + where, String.class, args.toArray())
                .stream().filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "Photo not found."));
        return photos.read(key);
    }

    private String visibility(AuthPrincipal principal, List<Object> args) {
        switch (principal.role()) {
            case FARMER -> {
                args.add(principal.userId());
                return "p.farmer_id = ?";
            }
            case AGENT -> {
                args.add(principal.userId());
                return """
                        p.farmer_id IN (SELECT f.user_id FROM farmers f
                                        JOIN agents a ON a.cooperative_id = f.cooperative_id WHERE a.user_id = ?)""";
            }
            default -> {
                return "TRUE";
            }
        }
    }

    private List<Long> agentsOf(long farmerId) {
        return jdbc.queryForList("""
                SELECT a.user_id FROM agents a JOIN farmers f ON f.cooperative_id = a.cooperative_id WHERE f.user_id = ?
                """, Long.class, farmerId);
    }

    private ProblemReportResponse byId(long id) {
        return jdbc.query(SELECT + " WHERE r.id = ?", (rs, n) -> map(rs), id).get(0);
    }

    private static ProblemReportResponse map(ResultSet rs) throws SQLException {
        long id = rs.getLong(1);
        Timestamp reportedAt = rs.getTimestamp(10);
        return new ProblemReportResponse(id, rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5),
                rs.getString(6), rs.getBoolean(7), rs.getObject(8, Long.class),
                rs.getBoolean(9) ? "/api/v1/problem-reports/" + id + "/photo" : null,
                reportedAt == null ? null : reportedAt.toInstant(), rs.getTimestamp(11).toInstant(),
                rs.getBoolean(12));
    }
}
