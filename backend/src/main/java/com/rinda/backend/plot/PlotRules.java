package com.rinda.backend.plot;

import com.rinda.backend.config.RindaProperties;
import com.rinda.backend.plot.PlotDtos.CheckResponse;
import com.rinda.backend.plot.PlotDtos.CheckResult;
import com.rinda.backend.plot.PlotDtos.FlagResponse;
import com.rinda.backend.plot.registry.LandRegistryProvider;
import com.rinda.backend.plot.registry.PlotRecord;
import com.rinda.backend.plot.registry.RegistryQuery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The verification rules in one place (docs/01-plot-verification.md):
 * <ul>
 *   <li>the level only moves up, and only when every check passed with no open flag;</li>
 *   <li>the status follows from the flags, the walk, the proof document and the level;</li>
 *   <li>financial actions need FIELD_VERIFIED or higher and no open dispute.</li>
 * </ul>
 */
@Component
public class PlotRules {

    private final PlotFlags flags;
    private final JdbcTemplate jdbc;
    private final LandRegistryProvider registry;
    private final RindaProperties properties;

    public PlotRules(PlotFlags flags, JdbcTemplate jdbc, LandRegistryProvider registry, RindaProperties properties) {
        this.flags = flags;
        this.jdbc = jdbc;
        this.registry = registry;
        this.properties = properties;
    }

    /** Recomputes level and status. Call after anything that changes a plot's evidence or flags. */
    public void refresh(Plot plot) {
        if (plot.getStatus() == PlotStatus.ARCHIVED || plot.getStatus() == PlotStatus.REJECTED) {
            return;
        }
        List<FlagType> open = flags.openTypes(plot.getId());
        raiseLevelIfEarned(plot, open);
        plot.setStatus(statusFor(plot.getVerificationLevel(), open, plot.hasBoundary()));
    }

    static PlotStatus statusFor(VerificationLevel level, List<FlagType> open, boolean hasBoundary) {
        if (open.stream().anyMatch(FlagType::isDispute)) {
            return PlotStatus.DISPUTED;
        }
        // Once a person verified the plot, other flags are shown for review but don't undo that check.
        if (level.atLeast(VerificationLevel.FIELD_VERIFIED)) {
            return PlotStatus.ACTIVE;
        }
        if (!open.isEmpty()) {
            return PlotStatus.FLAGGED;
        }
        return level == VerificationLevel.DOCUMENT_SUPPORTED && hasBoundary
                ? PlotStatus.PENDING_VERIFICATION : PlotStatus.DRAFT;
    }

    private void raiseLevelIfEarned(Plot plot, List<FlagType> open) {
        if (plot.getVerificationLevel().atLeast(VerificationLevel.FIELD_VERIFIED) || !open.isEmpty()
                || !hasCurrentWalk(plot.getId()) || plot.getUpi() == null || plot.getDeclarationAcceptedAt() == null) {
            return;
        }
        RegistryQuery query = new RegistryQuery(plot.getId(), plot.getUpi(), farmerName(plot.getFarmerId()),
                hasProofDocument(plot.getId()), plot.getDocumentAreaM2());
        Optional<VerificationLevel> earned = registry.lookup(query).map(PlotRecord::sourceLevel)
                .map(level -> level.compareTo(registry.maxLevel()) > 0 ? registry.maxLevel() : level);
        earned.filter(level -> level.compareTo(plot.getVerificationLevel()) > 0)
                .ifPresent(plot::setVerificationLevel);
    }

    /** Null when payouts and other financial actions are allowed for this plot; otherwise the error code. */
    public String financialBlock(long plotId) {
        List<String[]> rows = jdbc.query("SELECT status, verification_level FROM plots WHERE id = ?",
                (rs, n) -> new String[] {rs.getString(1), rs.getString(2)}, plotId);
        if (rows.isEmpty()) {
            return "PLOT_NOT_FOUND";
        }
        if (!VerificationLevel.valueOf(rows.get(0)[1]).atLeast(VerificationLevel.FIELD_VERIFIED)) {
            return "PLOT_NOT_FIELD_VERIFIED";
        }
        return PlotStatus.ACTIVE.name().equals(rows.get(0)[0]) ? null : "PLOT_NOT_ACTIVE";
    }

    public static boolean financialActionsAllowed(Plot plot) {
        return plot.getVerificationLevel().atLeast(VerificationLevel.FIELD_VERIFIED)
                && plot.getStatus() == PlotStatus.ACTIVE;
    }

    public boolean hasProofDocument(long plotId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM plot_documents WHERE plot_id = ? AND type IN ('E_TITLE', 'PORTAL_SCREENSHOT')",
                Integer.class, plotId);
        return count != null && count > 0;
    }

    public boolean hasCurrentWalk(long plotId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM gps_walks WHERE plot_id = ? AND status = 'CURRENT'", Integer.class, plotId);
        return count != null && count > 0;
    }

    private String farmerName(long farmerId) {
        return jdbc.queryForList("SELECT full_name FROM users WHERE id = ?", String.class, farmerId)
                .stream().findFirst().orElse(null);
    }

    /** Every check with its outcome, so a check that did not run is visible rather than silently skipped. */
    public List<CheckResponse> checks(Plot plot, List<FlagResponse> plotFlags) {
        boolean walked = hasCurrentWalk(plot.getId());
        List<CheckResponse> checks = new ArrayList<>();
        checks.add(plot.getUpi() == null
                ? new CheckResponse("UPI_FORMAT", CheckResult.PENDING, "No UPI recorded.")
                : new CheckResponse("UPI_FORMAT", CheckResult.PASSED,
                        "Five numeric parts. The exact format is not confirmed yet."));
        checks.add(hasProofDocument(plot.getId())
                ? new CheckResponse("PROOF_DOCUMENT", CheckResult.PASSED,
                        "E-title or portal screenshot uploaded. The agent compares it with the typed details.")
                : new CheckResponse("PROOF_DOCUMENT", CheckResult.PENDING, "Upload the e-title or a portal screenshot."));
        checks.add(flagCheck("GPS_WALK", Set.of(FlagType.LEGACY_DRAWN_BOUNDARY), plotFlags, walked,
                "Boundary built from the GPS walk.", "Walk the edge of the plot."));
        checks.add(flagCheck("GPS_ACCURACY", Set.of(FlagType.GPS_ACCURACY_UNCHECKED), plotFlags, walked,
                "Every point was within the accuracy limit.", "Walk the plot first."));
        checks.add(flagCheck("MOCK_LOCATION", Set.of(FlagType.MOCK_LOCATION), plotFlags, walked,
                "No mock location reported.", "Walk the plot first."));
        checks.add(flagCheck("AREA_MATCH", Set.of(FlagType.AREA_MISMATCH, FlagType.AREA_UNCHECKED), plotFlags,
                plot.getAreaDiffPct() != null,
                "Walked area is within the tolerance of the area typed from the document.",
                "Needs both the walk and the document area."));
        checks.add(flagCheck("OVERLAP", Set.of(FlagType.OVERLAP), plotFlags, walked,
                "No overlap with other plots in Rinda.", "Walk the plot first."));
        checks.add(flagCheck("DUPLICATE_UPI", Set.of(FlagType.DUPLICATE_UPI), plotFlags, plot.getUpi() != null,
                "No other plot in Rinda uses this UPI.", "No UPI recorded."));
        checks.add(properties.plots().adminAreaCheckEnabled()
                ? flagCheck("ADMIN_AREA", Set.of(FlagType.OUTSIDE_ADMIN_AREA), plotFlags, walked,
                        "Inside the area in the UPI.", "Walk the plot first.")
                : new CheckResponse("ADMIN_AREA", CheckResult.NOT_RUN,
                        "Switched off until a cell boundary file with a confirmed licence is available."));
        checks.add(plot.getRelationshipToPlot() == null || plot.getRelationshipToPlot() == RelationshipToPlot.OWNER
                ? new CheckResponse("TENURE", CheckResult.PASSED, "Declared as the owner.")
                : flagCheck("TENURE", Set.of(FlagType.NON_OWNER), plotFlags, true,
                        "Not the owner; the right to farm was reviewed.", ""));
        checks.add(plot.getVerificationLevel().atLeast(VerificationLevel.FIELD_VERIFIED)
                ? new CheckResponse("FIELD_VERIFICATION", CheckResult.PASSED, "Checked at the plot by a person.")
                : new CheckResponse("FIELD_VERIFICATION", CheckResult.PENDING,
                        "Needed before any payout. Ownership is not proven until then."));
        return checks;
    }

    private static CheckResponse flagCheck(String check, Set<FlagType> types, List<FlagResponse> plotFlags,
                                           boolean ran, String passedNote, String pendingNote) {
        List<FlagResponse> matching = plotFlags.stream().filter(f -> types.contains(f.type())).toList();
        Optional<FlagResponse> open = matching.stream().filter(f -> f.resolvedAt() == null).findFirst();
        if (open.isPresent()) {
            return new CheckResponse(check, CheckResult.FLAGGED, open.get().details());
        }
        if (!matching.isEmpty()) {
            return new CheckResponse(check, CheckResult.REVIEWED, "Reviewed by a person: "
                    + matching.get(matching.size() - 1).resolutionNote());
        }
        return ran ? new CheckResponse(check, CheckResult.PASSED, passedNote)
                : new CheckResponse(check, CheckResult.PENDING, pendingNote);
    }

    /** |walked − document| / document, in percent. */
    static BigDecimal areaDiffPct(BigDecimal walkedM2, BigDecimal documentM2) {
        return walkedM2.subtract(documentM2).abs().multiply(BigDecimal.valueOf(100))
                .divide(documentM2, 2, java.math.RoundingMode.HALF_UP);
    }
}
