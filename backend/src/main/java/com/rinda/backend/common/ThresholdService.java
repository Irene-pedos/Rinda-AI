package com.rinda.backend.common;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** Every threshold by key. A null value means the team has not supplied one yet. */
    public Map<String, BigDecimal> all() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        jdbc.query("SELECT threshold_key, threshold_value FROM thresholds ORDER BY threshold_key",
                rs -> {
                    values.put(rs.getString(1), rs.getBigDecimal(2));
                });
        return values;
    }
}
