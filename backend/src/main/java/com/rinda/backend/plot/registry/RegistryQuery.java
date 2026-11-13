package com.rinda.backend.plot.registry;

import java.math.BigDecimal;

/**
 * What Rinda knows when it asks a provider about a plot. ownerName is the farmer's account name.
 * typedAreaM2 is null until the farmer has uploaded a proof document.
 */
public record RegistryQuery(long plotId, String upi, String ownerName, boolean hasProofDocument,
                            BigDecimal typedAreaM2) {
}
