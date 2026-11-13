package com.rinda.backend.plot.registry;

import com.rinda.backend.plot.VerificationLevel;
import java.math.BigDecimal;

/**
 * A parcel as a provider knows it. Fields a provider can't supply are null. The document provider has no
 * owner-name match, land use, location or parcel polygon (no OCR). An NLA provider would fill polygonGeojson,
 * so the walked points can be checked against the official parcel.
 */
public record PlotRecord(String upi, String ownerName, BigDecimal areaM2, String landUse, String locationAdmin,
                         String polygonGeojson, VerificationLevel sourceLevel, String source) {
}
