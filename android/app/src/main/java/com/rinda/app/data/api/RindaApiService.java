package com.rinda.app.data.api;

import com.rinda.app.data.model.BaselineResponse;
import com.rinda.app.data.model.ClaimResponse;
import com.rinda.app.data.model.CooperativeResponse;
import com.rinda.app.data.model.EnrollmentResponse;
import com.rinda.app.data.model.FarmerSummaryResponse;
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
import com.rinda.app.data.model.PlotRequest;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.data.model.PlotVerifyRequest;
import com.rinda.app.data.model.RefreshRequest;
import com.rinda.app.data.model.ReviewRequestDto;
import com.rinda.app.data.model.TokenResponse;
import com.rinda.app.data.model.UserResponse;
import com.rinda.app.data.model.WeatherResponse;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
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

    // === Plots Management ===

    @GET("plots")
    Call<PageResponse<PlotResponse>> getPlots(
            @Query("page") int page,
            @Query("size") int size
    );

    @GET("plots/{id}")
    Call<PlotResponse> getPlot(@Path("id") long id);

    @POST("plots")
    Call<PlotResponse> createPlot(@Body PlotRequest request);

    @PUT("plots/{id}")
    Call<PlotResponse> updatePlot(@Path("id") long id, @Body PlotRequest request);

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

    @POST("agent/plots/{id}/verify")
    Call<PlotResponse> verifyAgentPlot(@Path("id") long id, @Body PlotVerifyRequest request);
}
