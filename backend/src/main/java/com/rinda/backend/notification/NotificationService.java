package com.rinda.backend.notification;

import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.PageResponse;
import com.rinda.backend.notification.NotificationDtos.NotificationResponse;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Map<String, Integer> SEVERITY = Map.of("HEALTHY", 0, "WATCH", 1, "AT_RISK", 2);

    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record LastStatus(String status, LocalDate date) {
    }

    /** The newest real status, skipping NOT_ENOUGH_DATA. Read it before saving a new status. */
    public Optional<LastStatus> lastKnownStatus(long plotId) {
        return jdbc.query("""
                SELECT status, status_date FROM plot_status
                WHERE plot_id = ? AND status <> 'NOT_ENOUGH_DATA'
                ORDER BY status_date DESC, id DESC
                LIMIT 1
                """, (rs, n) -> new LastStatus(rs.getString(1), rs.getObject(2, LocalDate.class)), plotId)
                .stream().findFirst();
    }

    /**
     * Alerts the farmer only when crop health gets worse than its last known status.
     * The status moves often (docs/ML_VALIDATION.md), so recoveries, NOT_ENOUGH_DATA and re-runs stay silent.
     */
    @Transactional
    public void healthChanged(long plotId, String status, String cause, LocalDate date, Optional<LastStatus> before,
                              boolean demoData) {
        Integer now = SEVERITY.get(status);
        if (now == null || now == 0) {
            return;
        }
        if (before.isPresent() && (before.get().date().isAfter(date)
                || SEVERITY.getOrDefault(before.get().status(), 0) >= now)) {
            return;
        }

        record Plot(long farmerId, String name, boolean demo) {
        }
        Plot plot = jdbc.queryForObject("SELECT farmer_id, name, is_demo FROM plots WHERE id = ?",
                (rs, n) -> new Plot(rs.getLong(1), rs.getString(2), rs.getBoolean(3)), plotId);

        boolean atRisk = "AT_RISK".equals(status);
        String title = atRisk ? "Your plot needs attention" : "Check your plot";
        String body = cause == null
                ? "Rain at " + plot.name() + " has been below normal."
                : plot.name() + (atRisk ? " has been growing well below" : " is growing below") + " its normal range.";
        jdbc.update("""
                INSERT INTO notifications (user_id, type, code, title, body, link, plot_id, is_demo)
                VALUES (?, 'HEALTH', ?, ?, ?, ?, ?, ?)
                """, plot.farmerId(), atRisk ? "HEALTH_AT_RISK" : "HEALTH_WATCH", title,
                body + " This is a crop health alert, not a claim.", "/plots/" + plotId, plotId,
                plot.demo() || demoData);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(long userId, int page, int size) {
        PageResponse.check(page, size);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ?", Long.class, userId);
        List<NotificationResponse> items = jdbc.query("""
                SELECT n.id, n.type, n.code, n.title, n.body, n.plot_id, p.name, n.link, n.is_read, n.is_demo,
                       n.created_at
                FROM notifications n
                LEFT JOIN plots p ON p.id = n.plot_id
                WHERE n.user_id = ?
                ORDER BY n.id DESC
                LIMIT ? OFFSET ?
                """, (rs, n) -> new NotificationResponse(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getObject(6, Long.class), rs.getString(7), rs.getString(8),
                rs.getBoolean(9), rs.getBoolean(10), rs.getObject(11, Timestamp.class).toInstant()),
                userId, size, (long) page * size);
        return PageResponse.of(items, page, size, total);
    }

    /** Marking twice is fine. Someone else's notification gets the same 404 as a missing one. */
    @Transactional
    public void markRead(long userId, long id) {
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE id = ? AND user_id = ?",
                Integer.class, id, userId);
        if (found == null || found == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found.");
        }
        jdbc.update("UPDATE notifications SET is_read = TRUE WHERE id = ?", id);
    }
}
