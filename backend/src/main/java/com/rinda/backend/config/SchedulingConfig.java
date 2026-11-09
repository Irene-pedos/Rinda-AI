package com.rinda.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the optional 5-day "Run analysis" schedule. It stays off while rinda.analysis.cron is "-". */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
