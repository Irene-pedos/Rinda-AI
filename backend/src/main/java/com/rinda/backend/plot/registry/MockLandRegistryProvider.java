package com.rinda.backend.plot.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinda.backend.plot.VerificationLevel;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * MOCK land registry for development and demos (dev profile only): invented parcels for zero-code UPIs, from
 * {@code mock-registry/parcels.json}. It stands where an NLA provider will go after an official agreement, so the
 * app's "type the UPI, see the parcel" screen can be built and tested now.
 *
 * <ul>
 *   <li>Only the fixture's fake UPIs are known. A real UPI is never matched and no shape is ever invented for it.</li>
 *   <li>Mock data proves nothing: for verification levels this provider behaves exactly like the document
 *       provider (an uploaded e-title or portal screenshot gives DOCUMENT_SUPPORTED, nothing more).</li>
 *   <li>Never scrape or automate NLA, Irembo, Amakuru (LIIP) or the Land Dashboard to fill this.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "rinda.plots.land-registry-provider", havingValue = "MOCK")
public class MockLandRegistryProvider implements LandRegistryProvider {

    static final String FIXTURE = "mock-registry/parcels.json";

    private record MockParcel(String landUse, String location, String boundaryGeojson) {
    }

    private final DocumentLandRegistryProvider documents = new DocumentLandRegistryProvider();
    private final Map<String, MockParcel> parcels = new HashMap<>();
    private final JdbcTemplate jdbc;

    public MockLandRegistryProvider(ObjectMapper mapper, JdbcTemplate jdbc) throws IOException {
        this.jdbc = jdbc;
        try (InputStream in = new ClassPathResource(FIXTURE).getInputStream()) {
            for (JsonNode parcel : mapper.readTree(in).path("parcels")) {
                parcels.put(parcel.path("upi").asText(), new MockParcel(parcel.path("landUse").asText(null),
                        parcel.path("location").asText(null), mapper.writeValueAsString(parcel.path("boundary"))));
            }
        }
    }

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public VerificationLevel maxLevel() {
        return documents.maxLevel();
    }

    @Override
    public Optional<PlotRecord> lookup(RegistryQuery query) {
        return documents.lookup(query);
    }

    @Override
    public Optional<PlotRecord> findByUpi(String upi) {
        MockParcel parcel = parcels.get(upi);
        if (parcel == null) {
            return Optional.empty();
        }
        // Same area calculation as a walked boundary (geographic, square metres).
        BigDecimal areaM2 = jdbc.queryForObject("SELECT ROUND(ST_Area(ST_GeomFromGeoJSON(?)), 1)", BigDecimal.class,
                parcel.boundaryGeojson());
        return Optional.of(new PlotRecord(upi, null, areaM2, parcel.landUse(), parcel.location(),
                parcel.boundaryGeojson(), VerificationLevel.SELF_DECLARED, name()));
    }
}
