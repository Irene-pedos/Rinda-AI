package com.rinda.backend.payout;

import com.rinda.backend.auth.AuthService;
import com.rinda.backend.auth.OtpCode;
import com.rinda.backend.auth.OtpPurpose;
import com.rinda.backend.auth.OtpService;
import com.rinda.backend.common.ApiException;
import com.rinda.backend.payout.PayoutDtos.PayoutAccountResponse;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The farmer's mobile-money number for simulated payouts (spec §2.2 #12). A new or changed number is unverified
 * until the farmer enters the code sent to it, and payouts only go to a verified number.
 * No real money moves and Rinda cannot see mobile-money accounts, so the name check is simulated.
 */
@Service
public class PayoutAccountService {

    private final JdbcTemplate jdbc;
    private final OtpService otp;

    public PayoutAccountService(JdbcTemplate jdbc, OtpService otp) {
        this.jdbc = jdbc;
        this.otp = otp;
    }

    record Account(long id, String provider, String msisdn, boolean verified, boolean nameMatch, Timestamp verifiedAt) {
    }

    @Transactional(readOnly = true)
    public PayoutAccountResponse get(long userId) {
        return find(userId).map(PayoutAccountService::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYOUT_ACCOUNT_NOT_SET",
                        "No payout account yet."));
    }

    /**
     * Saves the number and sends a code to it. Saving the same verified number again keeps it verified;
     * any change makes it unverified until confirmed (spec §2.2 #12).
     */
    @Transactional
    public PayoutAccountResponse set(long userId, String provider, String rawMsisdn) {
        String msisdn = AuthService.normalise(rawMsisdn);
        checkProvider(provider, msisdn);

        Optional<Account> current = find(userId);
        boolean unchanged = current.filter(a -> a.provider().equals(provider) && a.msisdn().equals(msisdn)).isPresent();
        if (current.isEmpty()) {
            jdbc.update("INSERT INTO payout_accounts (user_id, provider, msisdn) VALUES (?, ?, ?)",
                    userId, provider, msisdn);
        } else if (!unchanged) {
            jdbc.update("""
                    UPDATE payout_accounts
                    SET provider = ?, msisdn = ?, verified = FALSE, name_match = FALSE, verified_at = NULL
                    WHERE user_id = ?
                    """, provider, msisdn, userId);
        }
        if (!unchanged) {
            jdbc.update("""
                    INSERT INTO audit_log (actor_id, action, entity, entity_id, before_json, after_json)
                    VALUES (?, 'PAYOUT_ACCOUNT_CHANGED', 'payout_accounts', ?, ?, JSON_OBJECT('provider', ?, 'msisdn', ?))
                    """, userId, userId,
                    current.map(a -> "{\"provider\":\"" + a.provider() + "\",\"msisdn\":\"" + a.msisdn() + "\"}")
                            .orElse(null), provider, msisdn);
        }

        Account saved = find(userId).orElseThrow();
        if (!saved.verified()) {
            otp.request(msisdn, OtpPurpose.PAYOUT);
        }
        return toResponse(saved);
    }

    /** Confirms the number with the code that was sent to it. */
    @Transactional
    public PayoutAccountResponse verify(long userId, String code) {
        Account account = find(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "PAYOUT_ACCOUNT_NOT_SET", "No payout account yet."));
        if (account.verified()) {
            return toResponse(account);
        }
        OtpCode checked = otp.check(account.msisdn(), code, OtpPurpose.PAYOUT);
        otp.consume(checked);
        // Simulated name check: the MVP has no mobile-money API, so the match is recorded as simulated.
        jdbc.update("""
                UPDATE payout_accounts SET verified = TRUE, name_match = TRUE, verified_at = CURRENT_TIMESTAMP(6)
                WHERE user_id = ?
                """, userId);
        jdbc.update("""
                INSERT INTO audit_log (actor_id, action, entity, entity_id, after_json)
                VALUES (?, 'PAYOUT_ACCOUNT_VERIFIED', 'payout_accounts', ?, JSON_OBJECT('msisdn', ?))
                """, userId, userId, account.msisdn());
        return toResponse(find(userId).orElseThrow());
    }

    /** The verified account a payout can go to, if there is one. */
    Optional<Account> verifiedAccount(long userId) {
        return find(userId).filter(Account::verified);
    }

    private Optional<Account> find(long userId) {
        return jdbc.query("""
                SELECT id, provider, msisdn, verified, name_match, verified_at FROM payout_accounts WHERE user_id = ?
                """, (rs, n) -> new Account(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                rs.getBoolean(5), rs.getTimestamp(6)), userId).stream().findFirst();
    }

    /** MTN numbers start 078/079 and Airtel numbers 072/073. */
    private static void checkProvider(String provider, String msisdn) {
        String prefix = msisdn.substring(4, 6);
        boolean ok = "MTN_MOMO".equals(provider) ? prefix.equals("78") || prefix.equals("79")
                : prefix.equals("72") || prefix.equals("73");
        if (!ok) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYOUT_PROVIDER_MISMATCH",
                    "This number does not belong to the chosen provider.");
        }
    }

    private static PayoutAccountResponse toResponse(Account a) {
        return new PayoutAccountResponse(a.provider(), a.msisdn(), a.verified(), a.nameMatch(), "SIMULATED",
                a.verifiedAt() == null ? null : a.verifiedAt().toInstant());
    }
}
