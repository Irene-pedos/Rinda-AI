package com.rinda.backend.plot.registry;

import com.rinda.backend.plot.VerificationLevel;
import java.util.Optional;

/**
 * Where Rinda's knowledge of a land parcel comes from. The rest of the system only uses this interface, so an
 * NLA-backed provider can be added later (after an official agreement) without a redesign.
 *
 * <p>Never implement this by scraping or automating NLA, Irembo, Amakuru (LIIP) or the Land Dashboard.
 */
public interface LandRegistryProvider {

    /** Short name, for example "DOCUMENT". */
    String name();

    /** The highest level a plot can reach on this provider's evidence alone. */
    VerificationLevel maxLevel();

    /** The parcel record for this plot, or empty when the provider has nothing for it yet. */
    Optional<PlotRecord> lookup(RegistryQuery query);

    /**
     * What the provider knows about a UPI before any plot exists, for the registration screen: the parcel's
     * shape, area, land use and location. Empty when the provider has no parcel data (the document provider) or
     * doesn't know this UPI. Never returns the owner's name: anyone who types a UPI would learn who owns it.
     * The shape is a reference only; the plot's boundary still comes only from the GPS walk.
     */
    default Optional<PlotRecord> findByUpi(String upi) {
        return Optional.empty();
    }
}
