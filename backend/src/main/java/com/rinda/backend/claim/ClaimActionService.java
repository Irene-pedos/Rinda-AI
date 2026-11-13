package com.rinda.backend.claim;

import com.rinda.backend.common.ApiException;
import com.rinda.backend.plot.PlotRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What people do with a claim after the trigger created it (spec §3.7):
 * UNDER_REVIEW → (FIELD_VISIT_REQUESTED) → APPROVED | DECLINED → PAID | REVIEW_REQUESTED → FINAL.
 *
 * Only an officer decides, always with a written reason; the system never declines. A farmer can ask for one
 * review per decision. Payouts are simulated, and only happen with an amount the insurer confirmed and a
 * verified payout account, on a plot that is FIELD_VERIFIED or higher and not disputed. Every change goes to
 * audit_log, and the farmer is told.
 */
@Service
public class ClaimActionService {

    private static final Set<String> DECIDABLE = Set.of("UNDER_REVIEW", "FIELD_VISIT_REQUESTED", "REVIEW_REQUESTED");
    private static final Set<String> REVIEWABLE = Set.of("APPROVED", "DECLINED");

    private final JdbcTemplate jdbc;
    private final PlotRules plotRules;
    private final SecureRandom random = new SecureRandom();

    public ClaimActionService(JdbcTemplate jdbc, PlotRules plotRules) {
        this.jdbc = jdbc;
        this.plotRules = plotRules;
    }

    private record ClaimRow(long id, long plotId, long seasonId, String status, BigDecimal payoutRate,
                            BigDecimal amount, boolean demo, long farmerId, String plotName) {
    }

    /** Payout result for the API: PAID, or why it can't happen yet. */
    public enum PayoutOutcome { PAID, AMOUNT_NOT_CONFIRMED, ACCOUNT_NOT_VERIFIED, PLOT_NOT_ELIGIBLE }

    // ===== Officer =====

    @Transactional
    public void decide(long officerId, long claimId, String decision, String reason) {
        ClaimRow claim = lock(claimId);
        if (!DECIDABLE.contains(claim.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "CLAIM_NOT_DECIDABLE",
                    "This claim can't be decided now (status " + claim.status() + ").");
        }
        String text = reason.trim();

        // A decision on a review request answers it. A decline after a review is final (spec §3.7).
        Optional<Long> openReview = "REVIEW_REQUESTED".equals(claim.status()) ? openReview(claimId) : Optional.empty();
        String newStatus = "APPROVED".equals(decision) ? "APPROVED" : openReview.isPresent() ? "FINAL" : "DECLINED";

        jdbc.update("""
                INSERT INTO claim_decisions (claim_id, officer_id, decision, reason, decided_at, answers_review_id)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6), ?)
                """, claimId, officerId, decision, text, openReview.orElse(null));
        openReview.ifPresent(id -> jdbc.update("""
                UPDATE review_requests SET status = 'ANSWERED', answer = ?, answered_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """, text, id));
        setStatus(claim, newStatus, officerId, "CLAIM_DECIDED", decision);

        if ("APPROVED".equals(newStatus)) {
            notifyFarmer(claim, "CLAIM_APPROVED", "Your claim was approved",
                    "The claim for " + claim.plotName() + " was approved. The payout is simulated in this project.");
            PayoutOutcome paid = tryPay(claim, officerId);
            if (paid == PayoutOutcome.ACCOUNT_NOT_VERIFIED) {
                notifyFarmer(claim, "PAYOUT_ACCOUNT_NEEDED", "Add your payout account",
                        "To receive the payout for " + claim.plotName()
                                + ", add your mobile money number in Profile and confirm it with the code we send.");
            }
        } else if ("FINAL".equals(newStatus)) {
            notifyFarmer(claim, "CLAIM_FINAL", "Your review was answered",
                    "The officer looked at the claim for " + claim.plotName() + " again. The decision is final. "
                            + "Open the claim to read why.");
        } else {
            notifyFarmer(claim, "CLAIM_DECLINED", "Your claim was declined",
                    "The officer declined the claim for " + claim.plotName()
                            + ". Open the claim to read why. You can ask for a review once.");
        }
    }

    /** The officer asks the cooperative agent to look at the field before deciding. */
    @Transactional
    public void requestFieldVisit(long officerId, long claimId) {
        ClaimRow claim = lock(claimId);
        if (!"UNDER_REVIEW".equals(claim.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "CLAIM_NOT_UNDER_REVIEW",
                    "A field visit can only be requested while the claim is under review.");
        }
        setStatus(claim, "FIELD_VISIT_REQUESTED", officerId, "CLAIM_FIELD_VISIT_REQUESTED", null);
        notifyFarmer(claim, "CLAIM_FIELD_VISIT", "An agent will visit your field",
                "Your cooperative agent will visit " + claim.plotName() + " to look at the crop for your claim.");
        // Tell the agents of the farmer's cooperative, who will record the visit (phase 3).
        List<Long> agents = jdbc.queryForList("""
                SELECT a.user_id FROM agents a JOIN farmers f ON f.cooperative_id = a.cooperative_id
                WHERE f.user_id = ?
                """, Long.class, claim.farmerId());
        for (long agent : agents) {
            jdbc.update("""
                    INSERT INTO notifications (user_id, type, code, title, body, link, plot_id, is_demo)
                    VALUES (?, 'CLAIM', 'FIELD_VISIT_NEEDED', 'Field visit needed', ?, ?, ?, ?)
                    """, agent, "An insurance officer asked for a field visit to " + claim.plotName() + ".",
                    "/plots/" + claim.plotId(), claim.plotId(), claim.demo());
        }
    }

    /** Pays an approved claim (simulated). Call it again once the rates are confirmed or the account verified. */
    @Transactional
    public PayoutOutcome pay(long officerId, long claimId) {
        ClaimRow claim = lock(claimId);
        if (!"APPROVED".equals(claim.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "CLAIM_NOT_APPROVED", "Only an approved claim can be paid.");
        }
        String plotBlock = plotRules.financialBlock(claim.plotId());
        if (plotBlock != null) {
            throw new ApiException(HttpStatus.CONFLICT, plotBlock, "PLOT_NOT_FIELD_VERIFIED".equals(plotBlock)
                    ? "Payouts need a plot that a person checked in the field (FIELD_VERIFIED)."
                    : "The plot is disputed or under review, so the payout waits until that is resolved.");
        }
        PayoutOutcome outcome = tryPay(claim, officerId);
        if (outcome == PayoutOutcome.AMOUNT_NOT_CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYOUT_AMOUNT_NOT_CONFIRMED",
                    "The payout amount needs the insurer's sum insured and payout rate, which are not confirmed yet.");
        }
        if (outcome == PayoutOutcome.ACCOUNT_NOT_VERIFIED) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYOUT_ACCOUNT_NOT_VERIFIED",
                    "The farmer has no verified payout account yet.");
        }
        return outcome;
    }

    // ===== Farmer =====

    /** One review per decision. The farmer's own claim only; anything else gets the same 404 as a missing one. */
    @Transactional
    public void requestReview(long farmerId, long claimId, String message) {
        ClaimRow claim = lock(claimId);
        if (claim.farmerId() != farmerId) {
            throw notFound();
        }
        if (!REVIEWABLE.contains(claim.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "CLAIM_NOT_REVIEWABLE",
                    "A review can be asked for once the officer has decided, and before the payout.");
        }
        long decisionId = jdbc.query("SELECT MAX(id) FROM claim_decisions WHERE claim_id = ?",
                        (rs, n) -> rs.getObject(1, Long.class), claimId).stream().findFirst()
                .filter(id -> id != null).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "CLAIM_NOT_REVIEWABLE", "There is no decision to review yet."));
        Integer already = jdbc.queryForObject("SELECT COUNT(*) FROM review_requests WHERE decision_id = ?",
                Integer.class, decisionId);
        if (already != null && already > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ALREADY_REQUESTED",
                    "A review was already asked for on this decision.");
        }
        jdbc.update("""
                INSERT INTO review_requests (claim_id, farmer_message, decision_id, farmer_id) VALUES (?, ?, ?, ?)
                """, claimId, message.trim(), decisionId, farmerId);
        setStatus(claim, "REVIEW_REQUESTED", farmerId, "CLAIM_REVIEW_REQUESTED", null);
    }

    // ===== Helpers =====

    /**
     * Amount = sum insured × payout rate, both from the insurer (spec §3.7). Nothing is guessed: without both,
     * or without a verified payout account, the claim stays APPROVED and nothing is paid.
     */
    private PayoutOutcome tryPay(ClaimRow claim, long actorId) {
        // Financial actions need FIELD_VERIFIED or higher and no open dispute (docs/01-plot-verification.md).
        if (plotRules.financialBlock(claim.plotId()) != null) {
            return PayoutOutcome.PLOT_NOT_ELIGIBLE;
        }
        BigDecimal amount = claim.amount();
        if (amount == null && claim.payoutRate() != null) {
            BigDecimal sumInsured = jdbc.query("""
                    SELECT sum_insured FROM enrollments WHERE plot_id = ? AND season_id = ? AND status = 'COVERED'
                    """, (rs, n) -> rs.getBigDecimal(1), claim.plotId(), claim.seasonId())
                    .stream().findFirst().orElse(null);
            if (sumInsured != null) {
                amount = sumInsured.multiply(claim.payoutRate()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        if (amount == null) {
            return PayoutOutcome.AMOUNT_NOT_CONFIRMED;
        }
        Optional<Long> account = jdbc.queryForList(
                "SELECT id FROM payout_accounts WHERE user_id = ? AND verified = TRUE", Long.class, claim.farmerId())
                .stream().findFirst();
        if (account.isEmpty()) {
            return PayoutOutcome.ACCOUNT_NOT_VERIFIED;
        }

        String reference = "SIM-" + claim.id() + "-" + HexFormat.of().formatHex(randomBytes()).toUpperCase();
        jdbc.update("UPDATE claims SET amount = ? WHERE id = ?", amount, claim.id());
        jdbc.update("""
                INSERT INTO payouts (claim_id, account_id, amount, status, reference, is_simulated, is_demo)
                VALUES (?, ?, ?, 'PAID', ?, TRUE, ?)
                """, claim.id(), account.get(), amount, reference, claim.demo());
        // A payout only ever follows APPROVED, even when it runs straight after the decision.
        audit(claim.id(), "APPROVED", "PAID", actorId, "CLAIM_PAID_SIMULATED", null);
        jdbc.update("UPDATE claims SET status = 'PAID' WHERE id = ?", claim.id());
        notifyFarmer(claim, "CLAIM_PAID", "Payout sent (simulated)",
                "The payout of " + amount.stripTrailingZeros().toPlainString() + " RWF for " + claim.plotName()
                        + " was sent to your mobile money. This is a simulation: no real money moved.");
        return PayoutOutcome.PAID;
    }

    private byte[] randomBytes() {
        byte[] bytes = new byte[3];
        random.nextBytes(bytes);
        return bytes;
    }

    private ClaimRow lock(long claimId) {
        return jdbc.query("""
                SELECT c.id, c.plot_id, c.season_id, c.status, c.payout_rate, c.amount, c.is_demo, p.farmer_id, p.name
                FROM claims c JOIN plots p ON p.id = c.plot_id
                WHERE c.id = ?
                FOR UPDATE
                """, (rs, n) -> new ClaimRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBoolean(7), rs.getLong(8), rs.getString(9)), claimId)
                .stream().findFirst().orElseThrow(ClaimActionService::notFound);
    }

    private Optional<Long> openReview(long claimId) {
        return jdbc.queryForList("""
                SELECT id FROM review_requests WHERE claim_id = ? AND status = 'OPEN' ORDER BY id DESC LIMIT 1
                """, Long.class, claimId).stream().findFirst();
    }

    private void setStatus(ClaimRow claim, String status, long actorId, String action, String decision) {
        jdbc.update("UPDATE claims SET status = ? WHERE id = ?", status, claim.id());
        audit(claim.id(), claim.status(), status, actorId, action, decision);
    }

    private void audit(long claimId, String before, String after, long actorId, String action, String decision) {
        jdbc.update("""
                INSERT INTO audit_log (actor_id, action, entity, entity_id, before_json, after_json)
                VALUES (?, ?, 'claims', ?, JSON_OBJECT('status', ?), JSON_OBJECT('status', ?, 'decision', ?))
                """, actorId, action, claimId, before, after, decision);
    }

    private void notifyFarmer(ClaimRow claim, String code, String title, String body) {
        jdbc.update("""
                INSERT INTO notifications (user_id, type, code, title, body, link, plot_id, is_demo)
                VALUES (?, 'CLAIM', ?, ?, ?, ?, ?, ?)
                """, claim.farmerId(), code, title, body, "/claims/" + claim.id(), claim.plotId(), claim.demo());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found.");
    }
}
