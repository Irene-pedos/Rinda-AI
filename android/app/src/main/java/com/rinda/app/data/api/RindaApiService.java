package com.rinda.app.data.api;

import com.rinda.app.data.model.BaselineResponse;
import com.rinda.app.data.model.ClaimResponse;
import com.rinda.app.data.model.CooperativeResponse;
import com.rinda.app.data.model.EnrollmentResponse;
import com.rinda.app.data.model.FarmerSummaryResponse;
import com.rinda.app.data.model.FieldVisitResponse;
import com.rinda.app.data.model.HarvestForecastResponse;
import com.rinda.app.data.model.NotificationResponse;
import com.rinda.app.data.model.ObservationResponse;
import com.rinda.app.data.model.OtpRequest;
import com.rinda.app.data.model.OtpVerifyRequest;
import com.rinda.app.data.model.PageResponse;
import com.rinda.app.data.model.PayoutAccountRequest;
import com.rinda.app.data.model.PayoutAccountResponse;
import com.rinda.app.data.model.PayoutVerifyRequest;
import com.rinda.app.data.model.PlotHealthStatusResponse;
import com.rinda.app.data.model.PlotMonitoringResponse;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.data.model.ProblemReportResponse;
import com.rinda.app.data.model.RefreshRequest;
import com.rinda.app.data.model.ReviewRequestDto;
import com.rinda.app.data.model.TokenResponse;
import com.rinda.app.data.model.UserResponse;
import com.rinda.app.data.model.WeatherResponse;
import java.util.List;
import java.util.Map;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface RindaApiService {

    // === Authentication ===

    @POST("auth/otp/request")
    Call<Void> requestOtp(@Body OtpRequest request);

    @POST("auth/otp/verify")
    Call<TokenResponse> verifyOtp(@Body OtpVerifyRequest request);

    @POST("auth/refresh")
    Call<TokenResponse> refresh(@Body RefreshRequest request);

    @GET("auth/cooperatives")
    Call<List<CooperativeResponse>> getCooperatives();

    // === User & Profile ===

    @GET("me")
    Call<UserResponse> getMe();

    @PUT("me")
    Call<UserResponse> updateMe(@Body UserResponse user);

    @DELETE("me")
    Call<Void> deleteAccount();

    // === Plots Management ===

    @GET("plots")
    Call<PageResponse<PlotResponse>> getPlots(
            @Query("page") int page,
            @Query("size") int size
    );

    @GET("plots/{id}")
    Call<PlotResponse> getPlot(@Path("id") long id);

    /** Step 1 of a registration: details only. The boundary comes from {@link #walkPlot}. */
    @POST("plots")
    Call<PlotResponse> createPlot(@Body com.rinda.app.data.model.PlotRegistration.CreateRequest request);

    /** Name and planting date only: a boundary is refused. */
    @PUT("plots/{id}")
    Call<PlotResponse> updatePlot(@Path("id") long id,
                                  @Body com.rinda.app.data.model.PlotRegistration.UpdateRequest request);

    @GET("land-registry/parcels")
    Call<com.rinda.app.data.model.PlotRegistration.ParcelLookupResponse> lookupParcel(@Query("upi") String upi);

    @POST("plots/{id}/walk")
    Call<PlotResponse> walkPlot(@Path("id") long id,
                                @Body com.rinda.app.data.model.PlotRegistration.WalkRequest request);

    @Multipart
    @POST("plots/{id}/documents")
    Call<com.rinda.app.data.model.PlotRegistration.DocumentResponse> uploadPlotDocument(
            @Path("id") long id,
            @PartMap Map<String, RequestBody> fields,
            @Part MultipartBody.Part file
    );

    @DELETE("plots/{id}")
    Call<Void> deletePlot(@Path("id") long id);

    // === Plot Satellite & Weather Data ===

    @GET("plots/{id}/observations")
    Call<List<ObservationResponse>> getObservations(
            @Path("id") long id,
            @Query("from") String from,
            @Query("to") String to
    );

    @GET("plots/{id}/weather")
    Call<List<WeatherResponse>> getWeather(
            @Path("id") long id,
            @Query("from") String from,
            @Query("to") String to
    );

    @GET("plots/{id}/baseline")
    Call<BaselineResponse> getBaseline(@Path("id") long id);

    @GET("plots/{id}/status")
    Call<PlotHealthStatusResponse> getPlotHealthStatus(@Path("id") long id);

    /** Satellite monitoring state (NO_BOUNDARY until the plot is walked). */
    @GET("plots/{id}/monitoring")
    Call<PlotMonitoringResponse> getPlotMonitoring(@Path("id") long id);

    @GET("plots/{id}/forecast")
    Call<HarvestForecastResponse> getHarvestForecast(@Path("id") long id);

    @GET("plots/{id}/enrollment")
    Call<EnrollmentResponse> getEnrollment(@Path("id") long id);

    // === Notifications / Alerts ===

    @GET("notifications")
    Call<PageResponse<NotificationResponse>> getNotifications(
            @Query("page") int page,
            @Query("size") int size
    );

    @POST("notifications/{id}/read")
    Call<Void> markNotificationRead(@Path("id") long id);

    // === Claims ===

    @GET("claims")
    Call<PageResponse<ClaimResponse>> getClaims(
            @Query("page") int page,
            @Query("size") int size
    );

    @GET("claims/{id}")
    Call<ClaimResponse> getClaim(@Path("id") long id);

    @POST("claims/{id}/review-request")
    Call<Void> requestClaimReview(@Path("id") long id, @Body ReviewRequestDto request);

    // === Payout Account ===

    @GET("payout-account")
    Call<PayoutAccountResponse> getPayoutAccount();

    @POST("payout-account")
    Call<Void> savePayoutAccount(@Body PayoutAccountRequest request);

    @POST("payout-account/verify")
    Call<PayoutAccountResponse> verifyPayoutAccount(@Body PayoutVerifyRequest request);

    // === Agent Endpoints ===

    @GET("agent/farmers")
    Call<List<FarmerSummaryResponse>> getAgentFarmers();

    /**
     * Field check. Multipart: decision, confirmedPlantingDate, rejectionReason, and to verify a photo taken at
     * the plot in the app with photoLat, photoLng, photoAccuracyM, photoTakenAt (never typed in or defaulted).
     */
    @Multipart
    @POST("agent/plots/{id}/verify")
    Call<PlotResponse> verifyAgentPlot(
            @Path("id") long id,
            @PartMap Map<String, RequestBody> fields,
            @Part MultipartBody.Part photo
    );

    @Multipart
    @POST("agent/field-visits")
    Call<FieldVisitResponse> recordFieldVisit(
            @PartMap Map<String, RequestBody> partMap,
            @Part MultipartBody.Part photo
    );

    @GET("field-visits")
    Call<PageResponse<FieldVisitResponse>> getFieldVisits(
            @Query("plotId") Long plotId,
            @Query("claimId") Long claimId,
            @Query("page") int page,
            @Query("size") int size
    );

    // === Problem Reports ===

    @Multipart
    @POST("problem-reports")
    Call<ProblemReportResponse> submitProblemReport(
            @PartMap Map<String, RequestBody> partMap,
            @Part MultipartBody.Part photo
    );

    @GET("problem-reports")
    Call<PageResponse<ProblemReportResponse>> getProblemReports(
            @Query("plotId") Long plotId,
            @Query("claimId") Long claimId,
            @Query("page") int page,
            @Query("size") int size
    );
}
