package com.rinda.backend.plot;

import com.rinda.backend.common.ThresholdService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The automatic checks that raise review flags (docs/01-plot-verification.md). A threshold that is not supplied
 * yet never skips a check: the plot goes to a person instead. Nothing here rejects a plot.
 */
@Component
public class PlotChecks {

    private record Overlap(long plotId, long farmerId, boolean demo, BigDecimal pct) {
    }

    private final JdbcTemplate jdbc;
    private final ThresholdService thresholds;
    private final PlotFlags flags;
    private final PlotRepository plots;
    private final PlotRules rules;

    public PlotChecks(JdbcTemplate jdbc, ThresholdService thresholds, PlotFlags flags, PlotRepository plots,
                      PlotRules rules) {
        this.jdbc = jdbc;
        this.thresholds = thresholds;
        this.flags = flags;
        this.plots = plots;
        this.rules = rules;
    }

    /**
     * Walked area against the area the farmer typed from the document. The typed number is the farmer's, not
     * read from the document, so the agent's comparison with the image is what really covers this check.
     */
    public void area(Plot plot) {
        if (plot.getWalkedAreaM2() == null || plot.getDocumentAreaM2() == null) {
            return;
        }
        BigDecimal diff = PlotRules.areaDiffPct(plot.getWalkedAreaM2(), plot.getDocumentAreaM2());
        plot.setAreaDiffPct(diff);
        String areas = "walked " + plain(plot.getWalkedAreaM2()) + " m², document " + plain(plot.getDocumentAreaM2())
                + " m² (" + plain(diff) + "% apart)";
        Optional<BigDecimal> limit = thresholds.value("plot_area_mismatch_pct");
        if (limit.isEmpty()) {
            flags.raise(plot.getId(), FlagType.AREA_UNCHECKED,
                    "The area tolerance is not set yet, so a person must compare the areas: " + areas + ".", null);
        } else if (diff.compareTo(limit.get()) > 0) {
            flags.raise(plot.getId(), FlagType.AREA_MISMATCH,
                    "The areas differ by more than " + plain(limit.get()) + "%: " + areas + ".", null);
        }
    }

    /**
     * Overlap with other plots in Rinda. Both plots become DISPUTED and both farmers are told; a verifier
     * resolves it. Only works if the other farmer registered first, which is why payouts need a field check.
     */
    public void overlap(Plot plot) {
        Optional<BigDecimal> limit = thresholds.value("plot_max_overlap_pct");
        // Planar area (SRID 0) is accurate enough for a ratio at plot scale; geographic ST_Area rejects the
        // mixed collections ST_Intersection returns. Shared edges (ST_Touches) are neighbours, not overlaps.
        List<Overlap> found = jdbc.query("""
                SELECT p.id, p.farmer_id, p.is_demo,
                       ST_Area(ST_Intersection(ST_SRID(p.boundary, 0), ST_SRID(ST_GeomFromGeoJSON(?), 0))) * 100
                         / LEAST(ST_Area(ST_SRID(p.boundary, 0)), ST_Area(ST_SRID(ST_GeomFromGeoJSON(?), 0)))
                FROM plots p
                WHERE p.id <> ? AND p.boundary IS NOT NULL AND p.status NOT IN ('ARCHIVED', 'REJECTED')
                  AND ST_Intersects(p.boundary, ST_GeomFromGeoJSON(?))
                  AND NOT ST_Touches(p.boundary, ST_GeomFromGeoJSON(?))
                """, (rs, n) -> new Overlap(rs.getLong(1), rs.getLong(2), rs.getBoolean(3), rs.getBigDecimal(4)),
                plot.getGeojson(), plot.getGeojson(), plot.getId(), plot.getGeojson(), plot.getGeojson());
        for (Overlap other : found) {
            if (limit.isPresent() && other.pct().compareTo(limit.get()) <= 0) {
                continue;
            }
            String pct = plain(other.pct().setScale(1, java.math.RoundingMode.HALF_UP));
            String details = limit.isEmpty()
                    ? "Overlaps another registered plot by " + pct + "% (the overlap limit is not set yet)."
                    : "Overlaps another registered plot by " + pct + "%, above the " + plain(limit.get()) + "% limit.";
            dispute(plot.getId(), plot.getFarmerId(), plot.isDemo(), other.plotId(), FlagType.OVERLAP, details);
            dispute(other.plotId(), other.farmerId(), other.demo(), plot.getId(), FlagType.OVERLAP, details);
            refreshOther(other.plotId());
        }
    }

    /** One UPI per Rinda plot for now (multi-UPI plots are future work). A second plot with the UPI is a dispute. */
    public void duplicateUpi(Plot plot) {
        List<long[]> others = jdbc.query("""
                SELECT id, farmer_id, is_demo FROM plots
                WHERE upi = ? AND id <> ? AND status NOT IN ('ARCHIVED', 'REJECTED')
                """, (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getBoolean(3) ? 1 : 0},
                plot.getUpi(), plot.getId());
        for (long[] other : others) {
            String details = "Another plot in Rinda was registered with the same UPI.";
            dispute(plot.getId(), plot.getFarmerId(), plot.isDemo(), other[0], FlagType.DUPLICATE_UPI, details);
            dispute(other[0], other[1], other[2] == 1, plot.getId(), FlagType.DUPLICATE_UPI, details);
            refreshOther(other[0]);
        }
    }

    private void dispute(long plotId, long farmerId, boolean demo, long relatedPlotId, FlagType type, String details) {
        if (!flags.raise(plotId, type, details, relatedPlotId)) {
            return;
        }
        jdbc.update("""
                INSERT INTO audit_log (action, entity, entity_id, after_json)
                VALUES ('PLOT_DISPUTED', 'plots', ?, JSON_OBJECT('flag', ?, 'relatedPlotId', ?))
                """, plotId, type.name(), relatedPlotId);
        // The other farmer is never named: the agent handles the dispute in person.
        jdbc.update("""
                INSERT INTO notifications (user_id, type, code, title, body, link, plot_id, is_demo)
                VALUES (?, 'SYSTEM', 'PLOT_DISPUTED', 'Your plot needs a check', ?, ?, ?, ?)
                """, farmerId, "Another registration in Rinda matches part of this plot. Your agent will contact you. "
                        + "Keep your e-title ready. Any payout waits until this is resolved.",
                "/plots/" + plotId, plotId, demo);
    }

    private void refreshOther(long plotId) {
        plots.findById(plotId).ifPresent(other -> {
            rules.refresh(other);
            plots.save(other);
        });
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
