package com.rinda.backend.plot.registry;

import com.rinda.backend.plot.VerificationLevel;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The provider for now: the record is what the farmer typed from their own e-title or portal screenshot.
 * Nothing is read from the image (no OCR); the agent compares the typed values with the image at the field check.
 */
@Component
@ConditionalOnProperty(name = "rinda.plots.land-registry-provider", havingValue = "DOCUMENT", matchIfMissing = true)
public class DocumentLandRegistryProvider implements LandRegistryProvider {

    @Override
    public String name() {
        return "DOCUMENT";
    }

    @Override
    public VerificationLevel maxLevel() {
        return VerificationLevel.DOCUMENT_SUPPORTED;
    }

    @Override
    public Optional<PlotRecord> lookup(RegistryQuery query) {
        if (!query.hasProofDocument()) {
            return Optional.empty();
        }
        return Optional.of(new PlotRecord(query.upi(), null, query.typedAreaM2(), null, null, null,
                VerificationLevel.DOCUMENT_SUPPORTED, name()));
    }
}
