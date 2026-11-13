package com.rinda.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Registration without free drawing (docs/01-plot-verification.md, docs/03-data-model-and-api.md): UPI and
 * declaration, the farmer's own document, the GPS walk, the automatic checks and their review flags, the field
 * check, and the gate on financial actions. All data is fake.
 */
class PlotRegistrationApiTest extends ApiTestBase {

    private static final String PLANTED = LocalDate.now().minusDays(10).toString();
    private static final byte[] FAKE_PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};

    private String farmer;
    private String agent;

    @BeforeEach
    void farmerAndAgent() throws Exception {
        farmer = farmerToken("+250789999991");
        agent = staffToken("+250789999992", "AGENT", cooperativeId);
    }

    private JsonNode plot(String token, long id) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/plots/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String check(JsonNode plot, String name) {
        for (JsonNode c : plot.get("checks")) {
            if (name.equals(c.get("check").asText())) {
                return c.get("result").asText();
            }
        }
        throw new AssertionError("No check " + name);
    }

    private long openFlagId(long plotId, String type) {
        return jdbc.queryForObject("SELECT id FROM plot_flags WHERE plot_id = ? AND type = ? AND resolved_at IS NULL",
                Long.class, plotId, type);
    }

    private ResultActions resolve(String token, long plotId, long flagId) throws Exception {
        return send(token, post("/api/v1/plots/" + plotId + "/flags/" + flagId + "/resolve"),
                Map.of("note", "Checked the e-title and the plot with the farmer."));
    }

    private void limits() {
        setThreshold("walk_max_accuracy_m", 10);
        setThreshold("plot_area_mismatch_pct", 15);
    }

    // ===== No free drawing =====

    @Test
    void aHandDrawnBoundaryIsRefusedOnCreateAndEdit() throws Exception {
        Map<String, Object> body = registration("Hillside", PLANTED, "0/00/00/00/1");
        body.put("boundary", Map.of("type", "Polygon", "coordinates", List.of(List.of(List.of(30.1, -1.95),
                List.of(30.102, -1.95), List.of(30.102, -1.948), List.of(30.1, -1.95)))));
        send(farmer, post("/api/v1/plots"), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BOUNDARY_NOT_ACCEPTED"));

        long id = draftPlot(farmer, "Hillside", PLANTED);
        send(farmer, put("/api/v1/plots/" + id), Map.of("name", "x", "plantingDate", PLANTED,
                "boundary", Map.of("type", "Polygon")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BOUNDARY_NOT_ACCEPTED"));
        assertThat(jdbc.queryForObject("SELECT geojson IS NULL FROM plots WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void aWalkNeedsPointsNotAPolygon() throws Exception {
        long id = draftPlot(farmer, "Hillside", PLANTED);
        send(farmer, post("/api/v1/plots/" + id + "/walk"),
                Map.of("type", "Polygon", "coordinates", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ===== Step 1: UPI and declaration =====

    @Test
    void theUpiNeedsFiveNumericParts() throws Exception {
        for (String bad : List.of("ABC", "1/01/01/0001", "1/01/01/01/00A1", "1-01-01-01-0001")) {
            send(farmer, post("/api/v1/plots"), registration("Hillside", PLANTED, bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UPI_INVALID"));
        }
        send(farmer, post("/api/v1/plots"), registration("Hillside", PLANTED, " 1/01/01/01/0001 "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.upi").value("1/01/01/01/0001"));
    }

    @Test
    void theDeclarationMustBeAccepted() throws Exception {
        Map<String, Object> body = registration("Hillside", PLANTED, "0/00/00/00/1");
        body.put("declarationAccepted", false);
        send(farmer, post("/api/v1/plots"), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DECLARATION_REQUIRED"));
    }

    // ===== Step 2: GPS walk =====

    @Test
    void theServerBuildsTheBoundaryFromTheWalkAndKeepsTheRawPoints() throws Exception {
        long id = draftPlot(farmer, "Hillside", PLANTED);
        walk(farmer, id, squareFor(id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boundary.type").value("Polygon"))
                .andExpect(jsonPath("$.boundary.coordinates[0].length()").value(5)) // closed by the server
                .andExpect(jsonPath("$.status").value("FLAGGED")); // the accuracy limit is not supplied yet

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gps_walk_points", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT point_count FROM gps_walks WHERE plot_id = ?", Integer.class, id))
                .isEqualTo(4);
    }

    @Test
    void walkingAgainKeepsTheEarlierWalkAsARecord() throws Exception {
        long id = draftPlot(farmer, "Hillside", PLANTED);
        walk(farmer, id, squareFor(id)).andExpect(status().isOk());
        walk(farmer, id, squareFor(id)).andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT status FROM gps_walks WHERE plot_id = ? ORDER BY id", String.class, id))
                .containsExactly("SUPERSEDED", "CURRENT");
        // The same open flag is not raised twice.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plot_flags WHERE plot_id = ? AND type = 'GPS_ACCURACY_UNCHECKED'",
                Long.class, id)).isEqualTo(1);
    }

    @Test
    void walkPointsMustBeInTheOrderRecorded() throws Exception {
        long id = draftPlot(farmer, "Hillside", PLANTED);
        Map<String, Object> body = walkBody(squareFor(id), 4, false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = new java.util.ArrayList<>((List<Map<String, Object>>) body.get("points"));
        java.util.Collections.swap(points, 1, 2);
        send(farmer, post("/api/v1/plots/" + id + "/walk"), Map.of("points", points))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALK_INVALID"));
    }

    @Test
    void aWalkNeedsThreeDifferentCorners() throws Exception {
        long id = draftPlot(farmer, "Hillside", PLANTED);
        double[] corner = squareFor(id).get(0);
        walk(farmer, id, List.of(corner, corner, squareFor(id).get(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALK_INVALID"));
    }

    @Test
    void poorGpsAccuracyAsksForANewWalkOnceTheLimitIsSet() throws Exception {
        setThreshold("walk_max_accuracy_m", 10);
        long id = draftPlot(farmer, "Hillside", PLANTED);
        send(farmer, post("/api/v1/plots/" + id + "/walk"), walkBody(squareFor(id), 25, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALK_ACCURACY_POOR"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gps_walks", Long.class)).isZero();
    }

    @Test
    void withNoAccuracyLimitTheWalkGoesToAPerson() throws Exception {
        long id = draftPlot(farmer, "Hillside", PLANTED);
        walk(farmer, id, squareFor(id)).andExpect(status().isOk());

        JsonNode plot = plot(farmer, id);
        assertThat(plot.get("status").asText()).isEqualTo("FLAGGED");
        assertThat(check(plot, "GPS_ACCURACY")).isEqualTo("FLAGGED");
    }

    @Test
    void aMockLocationIsFlagged() throws Exception {
        limits();
        long id = draftPlot(farmer, "Hillside", PLANTED);
        send(farmer, post("/api/v1/plots/" + id + "/walk"), walkBody(squareFor(id), 4, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FLAGGED"))
                .andExpect(jsonPath("$.flags[0].type").value("MOCK_LOCATION"));
    }

    @Test
    void walkingReplacesABoundaryThatWasDrawnByHand() throws Exception {
        long id = draftPlot(farmer, "Hillside", PLANTED);
        jdbc.update("INSERT INTO plot_flags (plot_id, type, details) VALUES (?, 'LEGACY_DRAWN_BOUNDARY', 'Drawn')", id);
        resolve(agent, id, openFlagId(id, "LEGACY_DRAWN_BOUNDARY"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FLAG_NEEDS_WALK"));

        limits();
        walk(farmer, id, squareFor(id)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT resolved_at IS NOT NULL FROM plot_flags WHERE plot_id = ?",
                Boolean.class, id)).isTrue();
    }

    // ===== Step 3: the farmer's own document =====

    @Test
    void theTypedDocumentAreaIsRequiredForAnETitle() throws Exception {
        long id = draftPlot(farmer, "Hillside", PLANTED);
        mvc.perform(multipart("/api/v1/plots/" + id + "/documents")
                        .file(new MockMultipartFile("file", "e.png", "image/png", FAKE_PNG)).param("type", "E_TITLE")
                        .header("Authorization", "Bearer " + farmer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENT_AREA_REQUIRED"));
    }

    @Test
    void documentsArePdfOrImagesAndOnlyThePlotsViewersCanOpenThem() throws Exception {
        long id = draftPlot(farmer, "Hillside", PLANTED);
        mvc.perform(multipart("/api/v1/plots/" + id + "/documents")
                        .file(new MockMultipartFile("file", "e.pdf", "application/pdf", "not a pdf".getBytes()))
                        .param("type", "E_TITLE").param("documentAreaM2", "5000")
                        .header("Authorization", "Bearer " + farmer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENT_INVALID"));
        String created = mvc.perform(multipart("/api/v1/plots/" + id + "/documents")
                        .file(new MockMultipartFile("file", "e.pdf", "application/pdf", FAKE_PDF))
                        .param("type", "E_TITLE").param("documentAreaM2", "5000")
                        .header("Authorization", "Bearer " + farmer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.typedAreaM2").value(5000))
                .andReturn().getResponse().getContentAsString();
        long docId = mapper.readTree(created).get("id").asLong();

        String url = "/api/v1/plots/" + id + "/documents/" + docId + "/file";
        mvc.perform(get(url).header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk()).andExpect(content().bytes(FAKE_PDF));
        mvc.perform(get(url).header("Authorization", "Bearer " + farmerToken("+250789999993")))
                .andExpect(status().isNotFound());
        assertThat(check(plot(farmer, id), "PROOF_DOCUMENT")).isEqualTo("PASSED");
    }

    // ===== Automatic checks =====

    @Test
    void anAreaWithinTheToleranceRaisesThePlotToDocumentSupported() throws Exception {
        long id = pendingPlot(farmer, "Hillside", PLANTED);
        JsonNode plot = plot(farmer, id);
        assertThat(plot.get("verificationLevel").asText()).isEqualTo("DOCUMENT_SUPPORTED");
        assertThat(plot.get("status").asText()).isEqualTo("PENDING_VERIFICATION");
        assertThat(plot.get("areaDiffPct").decimalValue()).isZero();
        assertThat(plot.get("financialActionsAllowed").asBoolean()).isFalse();
        assertThat(check(plot, "AREA_MATCH")).isEqualTo("PASSED");
        assertThat(check(plot, "ADMIN_AREA")).isEqualTo("NOT_RUN"); // switched off, and shown as such
    }

    @Test
    void anAreaMismatchBeyondTheToleranceIsFlagged() throws Exception {
        limits();
        long id = draftPlot(farmer, "Hillside", PLANTED);
        walk(farmer, id, squareFor(id)).andExpect(status().isOk());
        uploadETitle(farmer, id, 20_000).andExpect(status().isCreated()); // walked about 49,200 m²

        JsonNode plot = plot(farmer, id);
        assertThat(plot.get("status").asText()).isEqualTo("FLAGGED");
        assertThat(plot.get("verificationLevel").asText()).isEqualTo("SELF_DECLARED");
        assertThat(check(plot, "AREA_MATCH")).isEqualTo("FLAGGED");
        assertThat(jdbc.queryForObject("SELECT type FROM plot_flags WHERE plot_id = ?", String.class, id))
                .isEqualTo("AREA_MISMATCH");
    }

    @Test
    void withNoAreaToleranceAPersonComparesTheAreas() throws Exception {
        setThreshold("walk_max_accuracy_m", 10);
        long id = draftPlot(farmer, "Hillside", PLANTED);
        String walked = walk(farmer, id, squareFor(id)).andReturn().getResponse().getContentAsString();
        uploadETitle(farmer, id, mapper.readTree(walked).get("walkedAreaM2").decimalValue())
                .andExpect(status().isCreated());

        assertThat(plot(farmer, id).get("status").asText()).isEqualTo("FLAGGED");
        assertThat(openFlagId(id, "AREA_UNCHECKED")).isPositive();
    }

    @Test
    void overlappingPlotsAreBothDisputedAndBothFarmersAreTold() throws Exception {
        limits();
        long first = draftPlot(farmer, "Mine", PLANTED);
        walk(farmer, first, squareFor(first)).andExpect(status().isOk());
        String other = farmerToken("+250789999994");
        long second = draftPlot(other, "Also mine", PLANTED);
        walk(other, second, squareFor(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"));

        assertThat(plot(farmer, first).get("status").asText()).isEqualTo("DISPUTED");
        assertThat(jdbc.queryForObject("SELECT related_plot_id FROM plot_flags WHERE plot_id = ? AND type = 'OVERLAP'",
                Long.class, first)).isEqualTo(second);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE code = 'PLOT_DISPUTED'", Long.class))
                .isEqualTo(2);
        // A disputed plot can't be walked somewhere else to escape the dispute.
        walk(other, second, squareFor(second))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_DISPUTED"));
    }

    @Test
    void neighboursSharingAnEdgeAreNotAnOverlap() throws Exception {
        limits();
        long first = draftPlot(farmer, "Mine", PLANTED);
        List<double[]> square = squareFor(first);
        walk(farmer, first, square).andExpect(status().isOk());
        String other = farmerToken("+250789999994");
        long second = draftPlot(other, "Next door", PLANTED);
        double lon = square.get(1)[0];
        double lat = square.get(1)[1];
        List<double[]> nextDoor = List.of(new double[] {lon, lat}, new double[] {lon + 0.002, lat},
                new double[] {lon + 0.002, lat + 0.002}, new double[] {lon, lat + 0.002});
        walk(other, second, nextDoor).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plot_flags WHERE type = 'OVERLAP'", Long.class)).isZero();
    }

    @Test
    void aSmallOverlapUnderTheLimitIsNotADispute() throws Exception {
        limits(); // plot_max_overlap_pct is 10 in V5
        long first = draftPlot(farmer, "Mine", PLANTED);
        List<double[]> square = squareFor(first);
        walk(farmer, first, square).andExpect(status().isOk());
        double lon = square.get(1)[0] - 0.0001; // 5% of the width
        double lat = square.get(1)[1];
        String other = farmerToken("+250789999994");
        long second = draftPlot(other, "Next door", PLANTED);
        walk(other, second, List.of(new double[] {lon, lat}, new double[] {lon + 0.002, lat},
                new double[] {lon + 0.002, lat + 0.002}, new double[] {lon, lat + 0.002})).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plot_flags WHERE type = 'OVERLAP'", Long.class)).isZero();
    }

    @Test
    void aSecondPlotWithTheSameUpiDisputesBoth() throws Exception {
        send(farmer, post("/api/v1/plots"), registration("Mine", PLANTED, "0/00/00/00/77"))
                .andExpect(status().isCreated());
        send(farmerToken("+250789999994"), post("/api/v1/plots"), registration("Also mine", PLANTED, "0/00/00/00/77"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DISPUTED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plots WHERE status = 'DISPUTED'", Long.class))
                .isEqualTo(2);
    }

    @Test
    void anAgentResolvesADisputeAfterReviewingIt() throws Exception {
        limits();
        long first = pendingPlot(farmer, "Mine", PLANTED);
        String other = farmerToken("+250789999994");
        long second = draftPlot(other, "Also mine", PLANTED);
        walk(other, second, squareFor(first)).andExpect(status().isOk());

        resolve(farmer, first, openFlagId(first, "OVERLAP")).andExpect(status().isForbidden());
        resolve(agent, first, openFlagId(first, "OVERLAP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'PLOT_FLAG_RESOLVED'", Long.class))
                .isEqualTo(1);
        assertThat(plot(other, second).get("status").asText()).isEqualTo("DISPUTED"); // each side is its own review
    }

    // ===== Tenants and relatives =====

    @Test
    void aTenantNeedsALeaseOrConsentBeforeAPersonClearsThePlot() throws Exception {
        limits();
        Map<String, Object> body = registration("Rented field", PLANTED, "0/00/00/00/88");
        body.put("relationshipToPlot", "TENANT");
        long id = mapper.readTree(send(farmer, post("/api/v1/plots"), body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String walked = walk(farmer, id, squareFor(id)).andReturn().getResponse().getContentAsString();
        uploadETitle(farmer, id, mapper.readTree(walked).get("walkedAreaM2").decimalValue());
        assertThat(plot(farmer, id).get("status").asText()).isEqualTo("FLAGGED");

        long flag = openFlagId(id, "NON_OWNER");
        resolve(agent, id, flag).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENURE_PROOF_MISSING"));
        mvc.perform(multipart("/api/v1/plots/" + id + "/documents")
                        .file(new MockMultipartFile("file", "lease.png", "image/png", FAKE_PNG)).param("type", "LEASE")
                        .header("Authorization", "Bearer " + farmer))
                .andExpect(status().isCreated());
        resolve(agent, id, flag).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.verificationLevel").value("DOCUMENT_SUPPORTED"));
    }

    // ===== Field check =====

    @Test
    void theFieldCheckRaisesThePlotToFieldVerifiedWithAGeotaggedPhoto() throws Exception {
        long id = pendingPlot(farmer, "Hillside", PLANTED);
        fieldCheck(agent, id, verifyAtPlot(id), FAKE_JPEG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationLevel").value("FIELD_VERIFIED"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.financialActionsAllowed").value(true));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM plot_verifications WHERE plot_id = ?", id);
        assertThat(row.get("result")).isEqualTo("VERIFIED");
        assertThat(row.get("photo_key")).isNotNull();
        assertThat(((Number) row.get("distance_to_boundary_m")).doubleValue()).isZero();
    }

    @Test
    void theFieldCheckNeedsAPhotoAndItsLocation() throws Exception {
        long id = pendingPlot(farmer, "Hillside", PLANTED);
        fieldCheck(agent, id, verifyAtPlot(id), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_PHOTO_REQUIRED"));
        fieldCheck(agent, id, Map.of("decision", "ACTIVE"), FAKE_JPEG)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_LOCATION_REQUIRED"));
    }

    @Test
    void aPhotoTakenAwayFromThePlotIsRefused() throws Exception {
        long id = pendingPlot(farmer, "Hillside", PLANTED);
        Map<String, String> away = new HashMap<>(verifyAtPlot(id));
        away.put("photoLng", String.valueOf(squareFor(id).get(1)[0] + 0.0005)); // about 55 m east of the edge

        // No distance limit yet: the strictest rule, inside the boundary.
        fieldCheck(agent, id, away, FAKE_JPEG)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_PHOTO_TOO_FAR"));
        setThreshold("verification_photo_max_distance_m", 30);
        fieldCheck(agent, id, away, FAKE_JPEG).andExpect(status().isBadRequest());
        setThreshold("verification_photo_max_distance_m", 100);
        fieldCheck(agent, id, away, FAKE_JPEG).andExpect(status().isOk());
    }

    @Test
    void aFlaggedPlotCantBeVerifiedUntilTheFlagIsResolved() throws Exception {
        long id = draftPlot(farmer, "Hillside", PLANTED);
        walk(farmer, id, squareFor(id)).andExpect(status().isOk()); // flagged: no accuracy limit
        fieldCheck(agent, id, verifyAtPlot(id), FAKE_JPEG)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_READY"));
    }

    @Test
    void aPlotWithoutADocumentCantBeVerified() throws Exception {
        limits();
        long id = draftPlot(farmer, "Hillside", PLANTED);
        walk(farmer, id, squareFor(id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        fieldCheck(agent, id, verifyAtPlot(id), FAKE_JPEG)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_READY"));
    }

    // ===== Financial actions need FIELD_VERIFIED =====

    @Test
    void noPayoutBelowFieldVerified() throws Exception {
        long id = pendingPlot(farmer, "Hillside", PLANTED); // DOCUMENT_SUPPORTED
        jdbc.update("INSERT INTO seasons (code, type) VALUES ('T1', 'A')");
        long season = jdbc.queryForObject("SELECT id FROM seasons WHERE code = 'T1'", Long.class);
        jdbc.update("""
                INSERT INTO claims (plot_id, season_id, trigger_date, status, amount, evidence_snapshot_json, model_version)
                VALUES (?, ?, CURRENT_DATE, 'APPROVED', 1000, '{}', 'test')
                """, id, season);
        long claim = jdbc.queryForObject("SELECT id FROM claims", Long.class);
        String officer = staffToken("+250789999996", "OFFICER", null);

        mvc.perform(post("/api/v1/claims/" + claim + "/payout").header("Authorization", "Bearer " + officer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_FIELD_VERIFIED"));

        fieldVerified(id);
        jdbc.update("UPDATE plots SET status = 'DISPUTED' WHERE id = ?", id);
        mvc.perform(post("/api/v1/claims/" + claim + "/payout").header("Authorization", "Bearer " + officer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_ACTIVE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payouts", Long.class)).isZero();
    }

    @Test
    void noCoverBelowFieldVerified() throws Exception {
        long id = pendingPlot(farmer, "Hillside", PLANTED);
        jdbc.update("INSERT INTO seasons (code, type) VALUES ('T1', 'A')");
        long season = jdbc.queryForObject("SELECT id FROM seasons WHERE code = 'T1'", Long.class);
        String admin = staffToken("+250789999997", "ADMIN", null);

        send(admin, post("/api/v1/admin/enrollments"), Map.of("plotId", id, "seasonId", season, "status", "COVERED"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_ACTIVE"));
    }

    @Test
    void onTheDocumentProviderAUpiLookupFindsNoParcelDetails() throws Exception {
        mvc.perform(get("/api/v1/land-registry/parcels").param("upi", "0/00/00/00/1001")
                        .header("Authorization", "Bearer " + farmer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.source").value("DOCUMENT"))
                .andExpect(jsonPath("$.isMock").value(false))
                .andExpect(jsonPath("$.notice").value(org.hamcrest.Matchers.containsString("e-title")));
    }
}
