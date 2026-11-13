package com.rinda.backend.plot;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** A non-owner's flag can be resolved once a lease or the owner's consent is on file. A person still reviews it. */
@Component
public class DocumentTenureConsentCheck implements TenureConsentCheck {

    private final JdbcTemplate jdbc;

    public DocumentTenureConsentCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String blockReason(long plotId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM plot_documents WHERE plot_id = ? AND type IN ('LEASE', 'OWNER_CONSENT')",
                Integer.class, plotId);
        return count != null && count > 0 ? null : "No lease or owner's consent has been uploaded yet.";
    }
}
