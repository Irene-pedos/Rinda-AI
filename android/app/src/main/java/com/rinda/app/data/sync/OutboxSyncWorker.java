package com.rinda.app.data.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.RindaApiService;
import com.rinda.app.data.api.TokenManager;
import com.rinda.app.data.local.OutboxDao;
import com.rinda.app.data.local.OutboxEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.FieldVisitResponse;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.data.model.PlotVerifyRequest;
import com.rinda.app.data.model.ProblemReportResponse;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

/**
 * Background WorkManager worker that flushes queued offline actions from SQLite (Room)
 * to the Spring Boot server whenever network connectivity is restored.
 * Follows spec §4 offline architecture: server always wins, local Room outbox is drained safely.
 */
public class OutboxSyncWorker extends Worker {

    private static final String WORK_NAME_PERIODIC = "rinda_outbox_periodic_sync";
    private static final String WORK_NAME_ONETIME = "rinda_outbox_sync_now";
    private static final MediaType TEXT_PLAIN = MediaType.parse("text/plain");
    private static final MediaType IMAGE_JPEG = MediaType.parse("image/*");

    public OutboxSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        TokenManager tokenManager = TokenManager.getInstance(context);
        if (!tokenManager.isLoggedIn()) {
            return Result.success();
        }

        RindaDatabase db = RindaDatabase.getInstance(context);
        OutboxDao outboxDao = db.outboxDao();
        List<OutboxEntity> pendingItems = outboxDao.getPendingItems();

        if (pendingItems == null || pendingItems.isEmpty()) {
            return Result.success();
        }

        RindaApiService apiService = ApiClient.getInstance(context).getService();

        for (OutboxEntity item : pendingItems) {
            try {
                boolean synced = processItem(apiService, item);
                if (synced) {
                    outboxDao.deleteById(item.id);
                }
            } catch (Exception e) {
                item.retries++;
                if (item.retries >= 5) {
                    item.status = "FAILED";
                }
                outboxDao.update(item);
            }
        }

        return Result.success();
    }

    private boolean processItem(RindaApiService apiService, OutboxEntity item) throws Exception {
        if ("PLOT_VERIFY".equals(item.type)) {
            return syncPlotVerify(apiService, item);
        } else if ("FIELD_VISIT".equals(item.type)) {
            return syncFieldVisit(apiService, item);
        } else if ("PROBLEM_REPORT".equals(item.type)) {
            return syncProblemReport(apiService, item);
        }
        return false;
    }

    private boolean syncPlotVerify(RindaApiService apiService, OutboxEntity item) throws Exception {
        JsonObject obj = JsonParser.parseString(item.payloadJson).getAsJsonObject();
        long plotId = obj.get("plotId").getAsLong();
        String decision = obj.get("decision").getAsString();
        String date = obj.has("confirmedPlantingDate") && !obj.get("confirmedPlantingDate").isJsonNull()
                ? obj.get("confirmedPlantingDate").getAsString() : null;
        String reason = obj.has("rejectionReason") && !obj.get("rejectionReason").isJsonNull()
                ? obj.get("rejectionReason").getAsString() : null;

        PlotVerifyRequest req = new PlotVerifyRequest(decision, date, reason);
        Response<PlotResponse> response = apiService.verifyAgentPlot(plotId, req).execute();
        return response.isSuccessful() || response.code() == 409;
    }

    private boolean syncFieldVisit(RindaApiService apiService, OutboxEntity item) throws Exception {
        JsonObject obj = JsonParser.parseString(item.payloadJson).getAsJsonObject();
        Map<String, RequestBody> partMap = new HashMap<>();

        addPart(partMap, "plotId", obj.get("plotId").getAsString());
        addPart(partMap, "plantCondition", obj.get("plantCondition").getAsString());
        addPart(partMap, "observedCause", obj.get("observedCause").getAsString());
        addPart(partMap, "growthStage", obj.get("growthStage").getAsString());

        if (obj.has("moistureChecked")) addPart(partMap, "moistureChecked", obj.get("moistureChecked").getAsString());
        if (obj.has("standDensityChecked")) addPart(partMap, "standDensityChecked", obj.get("standDensityChecked").getAsString());
        if (obj.has("farmerPresent")) addPart(partMap, "farmerPresent", obj.get("farmerPresent").getAsString());
        if (obj.has("notes") && !obj.get("notes").isJsonNull()) addPart(partMap, "notes", obj.get("notes").getAsString());
        if (obj.has("lat") && !obj.get("lat").isJsonNull()) addPart(partMap, "lat", obj.get("lat").getAsString());
        if (obj.has("lng") && !obj.get("lng").isJsonNull()) addPart(partMap, "lng", obj.get("lng").getAsString());
        if (obj.has("visitedAt") && !obj.get("visitedAt").isJsonNull()) addPart(partMap, "visitedAt", obj.get("visitedAt").getAsString());
        if (obj.has("clientId") && !obj.get("clientId").isJsonNull()) addPart(partMap, "clientId", obj.get("clientId").getAsString());

        MultipartBody.Part photoPart = null;
        if (item.photoPath != null) {
            File photoFile = new File(item.photoPath);
            if (photoFile.exists()) {
                RequestBody photoBody = RequestBody.create(IMAGE_JPEG, photoFile);
                photoPart = MultipartBody.Part.createFormData("photo", photoFile.getName(), photoBody);
            }
        }

        Response<FieldVisitResponse> response = apiService.recordFieldVisit(partMap, photoPart).execute();
        return response.isSuccessful() || response.code() == 201 || response.code() == 409;
    }

    private boolean syncProblemReport(RindaApiService apiService, OutboxEntity item) throws Exception {
        JsonObject obj = JsonParser.parseString(item.payloadJson).getAsJsonObject();
        Map<String, RequestBody> partMap = new HashMap<>();

        addPart(partMap, "plotId", obj.get("plotId").getAsString());
        addPart(partMap, "reason", obj.get("reason").getAsString());
        if (obj.has("note") && !obj.get("note").isJsonNull()) addPart(partMap, "note", obj.get("note").getAsString());
        if (obj.has("agentVisitRequested")) addPart(partMap, "agentVisitRequested", obj.get("agentVisitRequested").getAsString());
        if (obj.has("clientId") && !obj.get("clientId").isJsonNull()) addPart(partMap, "clientId", obj.get("clientId").getAsString());
        if (obj.has("reportedAt") && !obj.get("reportedAt").isJsonNull()) addPart(partMap, "reportedAt", obj.get("reportedAt").getAsString());

        MultipartBody.Part photoPart = null;
        if (item.photoPath != null) {
            File photoFile = new File(item.photoPath);
            if (photoFile.exists()) {
                RequestBody photoBody = RequestBody.create(IMAGE_JPEG, photoFile);
                photoPart = MultipartBody.Part.createFormData("photo", photoFile.getName(), photoBody);
            }
        }

        Response<ProblemReportResponse> response = apiService.submitProblemReport(partMap, photoPart).execute();
        return response.isSuccessful() || response.code() == 201 || response.code() == 409;
    }

    private void addPart(Map<String, RequestBody> map, String key, String value) {
        if (value != null) {
            map.put(key, RequestBody.create(TEXT_PLAIN, value));
        }
    }

    /** Triggers an immediate one-time sync if network is available. */
    public static void syncNow(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(OutboxSyncWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONETIME,
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    /** Schedules periodic background synchronization every 15 minutes. */
    public static void schedulePeriodicSync(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest periodicRequest = new PeriodicWorkRequest.Builder(
                OutboxSyncWorker.class,
                15, TimeUnit.MINUTES
        ).setConstraints(constraints).build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
        );
    }
}
