package com.rinda.backend.monitoring;

/**
 * Published when a plot's boundary or planting date changes. After the change commits, a satellite job is queued
 * (a backfill from the planting date if the season isn't covered yet).
 */
public record MonitoringRequested(long plotId) {
}
