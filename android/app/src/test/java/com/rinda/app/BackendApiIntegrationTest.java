package com.rinda.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rinda.app.data.api.RindaApiService;
import com.rinda.app.data.model.CooperativeResponse;
import com.rinda.app.data.model.OtpRequest;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Validates the Android Retrofit interface and DTO models directly against the running Spring Boot backend.
 */
public class BackendApiIntegrationTest {

    private RindaApiService apiService;

    @Before
    public void setUp() {
        OkHttpClient client = new OkHttpClient.Builder().build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://127.0.0.1:8080/api/v1/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(RindaApiService.class);
    }

    @Test
    public void testGetCooperativesAgainstRunningBackend() throws Exception {
        Response<List<CooperativeResponse>> response = apiService.getCooperatives().execute();
        assertTrue("Backend should respond 200 to /auth/cooperatives", response.isSuccessful());
        assertNotNull(response.body());
        assertFalse(response.body().isEmpty());

        CooperativeResponse coop = response.body().get(0);
        assertEquals(9001L, coop.getId());
        assertEquals("Demo Cooperative", coop.getName());
        assertEquals("TO_CONFIRM", coop.getDistrict());
        assertTrue(coop.isDemo());
    }

    @Test
    public void testRequestOtpAgainstRunningBackend() throws Exception {
        Response<Void> response = apiService.requestOtp(new OtpRequest("+250780000001")).execute();
        // 202 Accepted, or 429 if rate limited from previous call
        assertTrue(response.code() == 202 || response.code() == 429);
    }

    @Test
    public void testNotificationsEndpointRequiresAuth() throws Exception {
        Response<?> response = apiService.getNotifications(0, 20).execute();
        assertEquals("Unauthenticated call to /notifications should return 401", 401, response.code());
    }
}
