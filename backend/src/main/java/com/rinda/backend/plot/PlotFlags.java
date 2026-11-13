package com.rinda.backend.plot;

import com.rinda.backend.plot.PlotDtos.FlagResponse;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Review flags on plots. Raising one never rejects anything; only a person resolves one. */
@Component
public class PlotFlags {

    private final JdbcTemplate jdbc;

    public PlotFlags(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Opens a flag unless the same one is already open (same type and related plot), so repeated walks don't
     * pile up copies. Returns true when a new flag was opened.
     */
    public boolean raise(long plotId, FlagType type, String details, Long relatedPlotId) {
        Integer open = jdbc.queryForObject("""
                SELECT COUNT(*) FROM plot_flags
                WHERE plot_id = ? AND type = ? AND resolved_at IS NULL AND related_plot_id <=> ?
                """, Integer.class, plotId, type.name(), relatedPlotId);
        if (open != null && open > 0) {
            return false;
        }
        jdbc.update("INSERT INTO plot_flags (plot_id, type, details, related_plot_id) VALUES (?, ?, ?, ?)",
                plotId, type.name(), details, relatedPlotId);
        return true;
    }

    public List<FlagResponse> forPlot(long plotId) {
        return jdbc.query("""
                SELECT id, type, details, related_plot_id, created_at, resolved_at, resolved_by, resolution_note
                FROM plot_flags WHERE plot_id = ? ORDER BY id
                """, (rs, n) -> new FlagResponse(rs.getLong(1), FlagType.valueOf(rs.getString(2)), rs.getString(3),
                rs.getObject(4, Long.class), rs.getTimestamp(5).toInstant(),
                Optional.ofNullable(rs.getTimestamp(6)).map(Timestamp::toInstant).orElse(null),
                rs.getObject(7, Long.class), rs.getString(8)), plotId);
    }

    public List<FlagType> openTypes(long plotId) {
        return jdbc.queryForList("SELECT type FROM plot_flags WHERE plot_id = ? AND resolved_at IS NULL",
                String.class, plotId).stream().map(FlagType::valueOf).toList();
    }

    public Optional<FlagResponse> find(long plotId, long flagId) {
        return forPlot(plotId).stream().filter(f -> f.id() == flagId).findFirst();
    }

    public void resolve(long flagId, long userId, String note) {
        jdbc.update("""
                UPDATE plot_flags SET resolved_at = CURRENT_TIMESTAMP(6), resolved_by = ?, resolution_note = ?
                WHERE id = ? AND resolved_at IS NULL
                """, userId, note, flagId);
    }

    /** The one mechanical resolution: walking the plot replaces a boundary that was drawn by hand. */
    public void resolveLegacyBoundary(long plotId, long farmerId) {
        jdbc.update("""
                UPDATE plot_flags SET resolved_at = CURRENT_TIMESTAMP(6), resolved_by = ?,
                       resolution_note = 'Replaced by a GPS boundary walk.'
                WHERE plot_id = ? AND type = 'LEGACY_DRAWN_BOUNDARY' AND resolved_at IS NULL
                """, farmerId, plotId);
    }
}
