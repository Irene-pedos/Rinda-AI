package com.rinda.backend.common;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reads the thresholds table. An empty result means the team has not supplied a value yet. */
@Service
public class ThresholdService {

    private final JdbcTemplate jdbc;

    public ThresholdService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BigDecimal> value(String key) {
        List<BigDecimal> rows = jdbc.queryForList(
                "SELECT threshold_value FROM thresholds WHERE threshold_key = ?", BigDecimal.class, key);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }
}
