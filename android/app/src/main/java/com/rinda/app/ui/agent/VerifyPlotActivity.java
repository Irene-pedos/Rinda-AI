package com.rinda.app.ui.agent;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.ApiErrorParser;
import com.rinda.app.data.api.RindaApiService;
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.data.model.PlotStatus;
import com.rinda.app.databinding.ActivityVerifyPlotBinding;
import com.rinda.app.databinding.DialogTextInputBinding;
import com.rinda.app.ui.common.StatusUi;
import com.rinda.app.ui.common.Ui;
import com.rinda.app.util.DateTimeUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polygon;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Agent field check (POST /agent/plots/{id}/verify, docs/01-plot-verification.md).
 *
 * To verify, the agent takes a photo at the plot in the app; the app records the phone's GPS position and
 * time with it (never typed in or defaulted), and the backend checks it is inside the walked boundary.
 * The check is online only: it isn't queued, so the screen always shows the server's real answer.
 */
public class VerifyPlotActivity extends AppCompatActivity {

    public static final String EXTRA_PLOT_ID = "extra_plot_id";

    /** A GPS fix counts for the photo only if it is at most this old when the photo is taken. */
    private static final long FIX_MAX_AGE_MS = 30_000;
    /** Longest edge of the uploaded photo. Keeps it well under the server's 5 MB limit. */
    private static final int PHOTO_MAX_EDGE_PX = 1600;
    private static final MediaType TEXT = MediaType.parse("text/plain");
    private static final MediaType JPEG = MediaType.parse("image/jpeg");

    private ActivityVerifyPlotBinding binding;
    private RindaApiService apiService;
    private long plotId;
    private PlotResponse plot;
    private String selectedPlantingDate;

    private LocationManager locationManager;
    private boolean listening;
    @Nullable private Location latestFix;

    @Nullable private File photoFile;
    @Nullable private Uri pendingPhotoUri;
    @Nullable private File pendingPhotoFile;
    private long photoTakenAtMillis;
    @Nullable private Location photoFix;
    private boolean waitingForFix;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable fixTimeout = this::onFixTimeout;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            latestFix = location;
            if (waitingForFix) {
                // The first fix after the photo, inside the time window.
                attachFix(location);
            }
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            updatePhotoUi();
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            updatePhotoUi();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Needed below API 29.
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                startListening();
                if (hasPermission(Manifest.permission.CAMERA) && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    launchCamera();
                } else {
                    Ui.toast(binding.getRoot(), R.string.verify_photo_permission);
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), saved -> {
                if (Boolean.TRUE.equals(saved) && pendingPhotoFile != null) {
                    onPhotoTaken(pendingPhotoFile);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        binding = ActivityVerifyPlotBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getInstance(this).getService();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        plotId = getIntent().getLongExtra(EXTRA_PLOT_ID, -1);
        if (plotId <= 0) {
            finish();
            return;
        }

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK);
        binding.mapView.setMultiTouchControls(true);
        binding.btnChangeDate.setOnClickListener(v -> showDatePicker());
        binding.btnTakePhoto.setOnClickListener(v -> takePhoto());
        binding.btnVerify.setOnClickListener(v -> confirmVerification());
        binding.btnReject.setOnClickListener(v -> promptRejectionReason());

        // Warm up the GPS while the agent looks at the plot, so a fix is ready for the photo.
        startListening();
        updatePhotoUi();
        loadPlot();
    }

    // ===== Plot =====

    private void loadPlot() {
        setBusy(true);
        apiService.getPlot(plotId).enqueue(new Callback<PlotResponse>() {
            @Override
            public void onResponse(@NonNull Call<PlotResponse> call, @NonNull Response<PlotResponse> response) {
                setBusy(false);
                if (response.isSuccessful() && response.body() != null) {
                    plot = response.body();
                    displayPlot(plot);
                } else {
                    showProblem(response);
                }
            }

            @Override
            public void onFailure(@NonNull Call<PlotResponse> call, @NonNull Throwable t) {
                setBusy(false);
                Ui.error(binding.getRoot(), getString(R.string.error_network_action), () -> loadPlot());
            }
        });
    }

    private void displayPlot(PlotResponse p) {
        binding.tvPlotName.setText(p.getName());
        binding.tvPlotArea.setText(p.getAreaHa() != null
                ? getString(R.string.plot_area, p.getAreaHa().doubleValue())
                : getString(R.string.plot_area_not_walked));
        binding.tvVerificationLevel.setText(getString(R.string.reg_result_level,
                StatusUi.verificationLevelLabel(this, p.getVerificationLevel())));
        binding.tvVerificationLevel.setVisibility(View.VISIBLE);
        StatusUi.applyPill(binding.tvStatusTag, StatusUi.forPlot(p.getStatus()));

        // Never default the date to today: show it as not set and let the agent pick it.
        selectedPlantingDate = p.getPlantingDate();
        binding.tvPlantingDate.setText(selectedPlantingDate != null
                ? DateTimeUtils.formatDate(selectedPlantingDate)
                : getString(R.string.plot_planting_unconfirmed));

        int notReady = notReadyMessage(p.getStatus());
        binding.tvNotReady.setVisibility(notReady != 0 ? View.VISIBLE : View.GONE);
        if (notReady != 0) binding.tvNotReady.setText(notReady);
        binding.btnVerify.setEnabled(p.getStatus() == PlotStatus.PENDING_VERIFICATION);
        boolean checked = p.getStatus() == PlotStatus.ACTIVE || p.getStatus() == PlotStatus.REJECTED
                || p.getStatus() == PlotStatus.ARCHIVED;
        binding.btnReject.setEnabled(!checked);

        renderBoundary(p);
    }

    /** Why Verify is off, or 0 when the plot is waiting for its check. */
    private int notReadyMessage(@Nullable PlotStatus status) {
        if (status == null) return R.string.verify_not_ready_draft;
        switch (status) {
            case PENDING_VERIFICATION:
                return 0;
            case FLAGGED:
                return R.string.verify_not_ready_flagged;
            case DISPUTED:
                return R.string.verify_not_ready_disputed;
            case ACTIVE:
            case REJECTED:
            case ARCHIVED:
                return R.string.verify_not_ready_done;
            case DRAFT:
            default:
                return R.string.verify_not_ready_draft;
        }
    }

    private void renderBoundary(PlotResponse p) {
        binding.mapView.getOverlays().clear();
        boolean walked = p.getBoundary() != null && p.getBoundary().getCoordinates() != null
                && !p.getBoundary().getCoordinates().isEmpty();
        // No walk yet: no empty map box. The area line already says "Not walked yet".
        binding.mapView.setVisibility(walked ? View.VISIBLE : View.GONE);
        if (!walked) return;
        List<GeoPoint> points = new ArrayList<>();
        for (List<Double> c : p.getBoundary().getCoordinates().get(0)) {
            if (c.size() >= 2) points.add(new GeoPoint(c.get(1), c.get(0))); // GeoJSON is [lng, lat]
        }
        if (points.isEmpty()) return;
        Polygon polygon = new Polygon(binding.mapView);
        polygon.setPoints(points);
        polygon.getOutlinePaint().setColor(ContextCompat.getColor(this, R.color.rinda_primary));
        polygon.getOutlinePaint().setStrokeWidth(4f);
        int fill = ContextCompat.getColor(this, R.color.rinda_primary);
        polygon.getFillPaint().setColor(Color.argb(55, Color.red(fill), Color.green(fill), Color.blue(fill)));
        binding.mapView.getOverlays().add(polygon);
        BoundingBox box = BoundingBox.fromGeoPointsSafe(points);
        binding.mapView.post(() -> binding.mapView.zoomToBoundingBox(box.increaseByScale(1.4f), false));
        binding.mapView.invalidate();
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        if (selectedPlantingDate != null) {
            try {
                Date d = dateFormat.parse(selectedPlantingDate);
                if (d != null) cal.setTime(d);
            } catch (Exception ignored) {
                // Keep today as the picker's starting point only; nothing is saved until picked.
            }
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, day);
            selectedPlantingDate = dateFormat.format(picked.getTime());
            binding.tvPlantingDate.setText(DateTimeUtils.formatDate(selectedPlantingDate));
            Ui.toast(binding.getRoot(), R.string.planting_date_updated);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    // ===== Photo and GPS =====

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission") // Checked first.
    private void startListening() {
        if (listening || locationManager == null || !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return;
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,
                    locationListener, Looper.getMainLooper());
            listening = true;
        } catch (IllegalArgumentException | SecurityException e) {
            listening = false;
        }
        updatePhotoUi();
    }

    private void stopListening() {
        if (listening && locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        listening = false;
    }

    private void takePhoto() {
        if (hasPermission(Manifest.permission.CAMERA) && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            launchCamera();
        } else {
            permissionLauncher.launch(new String[] {Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION});
        }
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "verify_photos");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("no photo folder");
            pendingPhotoFile = new File(dir, "check_" + plotId + "_" + System.currentTimeMillis() + ".jpg");
            pendingPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".files", pendingPhotoFile);
            cameraLauncher.launch(pendingPhotoUri);
        } catch (Exception e) {
            Ui.toast(binding.getRoot(), R.string.verify_photo_failed);
        }
    }

    private void onPhotoTaken(File file) {
        photoTakenAtMillis = System.currentTimeMillis();
        photoFix = null;
        // A fix taken just before the photo counts; otherwise wait briefly for the next one.
        if (isFresh(latestFix)) {
            attachFix(latestFix);
        } else {
            waitingForFix = true;
            handler.removeCallbacks(fixTimeout);
            handler.postDelayed(fixTimeout, FIX_MAX_AGE_MS);
        }
        new Thread(() -> {
            Bitmap preview = shrink(file);
            runOnUiThread(() -> {
                if (isFinishing() || binding == null) return;
                if (preview == null) {
                    photoFile = null;
                    Ui.toast(binding.getRoot(), R.string.verify_photo_failed);
                } else {
                    deleteOldPhoto(file);
                    photoFile = file;
                    binding.ivVerifyPhoto.setImageBitmap(preview);
                    binding.ivVerifyPhoto.setVisibility(View.VISIBLE);
                }
                updatePhotoUi();
            });
        }).start();
        updatePhotoUi();
    }

    private boolean isFresh(@Nullable Location fix) {
        if (fix == null) return false;
        long ageMs = (SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos()) / 1_000_000L;
        return ageMs >= 0 && ageMs <= FIX_MAX_AGE_MS;
    }

    private void attachFix(Location fix) {
        waitingForFix = false;
        handler.removeCallbacks(fixTimeout);
        photoFix = fix;
        updatePhotoUi();
    }

    private void onFixTimeout() {
        if (!waitingForFix) return;
        waitingForFix = false;
        photoFix = null;
        updatePhotoUi();
    }

    private void updatePhotoUi() {
        if (binding == null) return;
        boolean gpsOn = locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (photoFile == null && !waitingForFix) {
            binding.tvPhotoGps.setText(!gpsOn ? R.string.verify_gps_off : R.string.verify_photo_none);
        } else if (waitingForFix) {
            binding.tvPhotoGps.setText(R.string.verify_photo_waiting_gps);
        } else if (photoFix != null) {
            binding.tvPhotoGps.setText(getString(R.string.verify_photo_gps, Math.round(photoFix.getAccuracy())));
        } else {
            binding.tvPhotoGps.setText(R.string.verify_photo_no_gps);
        }
        binding.btnTakePhoto.setText(photoFile != null ? R.string.verify_btn_retake_photo : R.string.verify_btn_take_photo);
    }

    /** Scales the camera photo down, keeps it upright, and rewrites it as JPEG. Returns a preview, or null. */
    @Nullable
    private static Bitmap shrink(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_EDGE_PX) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (bitmap == null) return null;

            int rotation;
            switch (new ExifInterface(file.getAbsolutePath()).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90: rotation = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: rotation = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: rotation = 270; break;
                default: rotation = 0;
            }
            float scale = Math.min(1f, PHOTO_MAX_EDGE_PX / (float) Math.max(bitmap.getWidth(), bitmap.getHeight()));
            if (rotation != 0 || scale < 1f) {
                Matrix m = new Matrix();
                m.postScale(scale, scale);
                m.postRotate(rotation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
            return bitmap;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void deleteOldPhoto(File keep) {
        if (photoFile != null && !photoFile.equals(keep)) {
            //noinspection ResultOfMethodCallIgnored
            photoFile.delete();
        }
    }

    // ===== Decision =====

    private void confirmVerification() {
        if (photoFile == null || photoFix == null) {
            Ui.toast(binding.getRoot(), R.string.verify_photo_required);
            return;
        }
        if (selectedPlantingDate == null) {
            Ui.toast(binding.getRoot(), R.string.error_planting_date_required);
            return;
        }
        Ui.dialog(this)
                .setTitle(R.string.verify_plot_dialog_title)
                .setMessage(R.string.verify_plot_dialog_msg)
                .setPositiveButton(R.string.btn_approve_plot, (d, w) -> submit("ACTIVE", null))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void promptRejectionReason() {
        DialogTextInputBinding input = DialogTextInputBinding.inflate(getLayoutInflater());
        input.tilInput.setHint(getString(R.string.reject_plot_reason_hint));
        Ui.dialog(this)
                .setTitle(R.string.reject_plot_dialog_title)
                .setMessage(R.string.reject_plot_dialog_msg)
                .setView(input.getRoot())
                .setPositiveButton(R.string.btn_reject_plot, (d, w) -> {
                    String reason = input.etInput.getText() != null ? input.etInput.getText().toString().trim() : "";
                    if (reason.isEmpty()) {
                        // Never invent a reason: the farmer reads it.
                        Ui.toast(binding.getRoot(), R.string.verify_reason_required);
                        return;
                    }
                    submit("REJECTED", reason.length() > 500 ? reason.substring(0, 500) : reason);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void submit(String decision, @Nullable String reason) {
        if (!Ui.isOnline(this)) {
            Ui.toast(binding.getRoot(), R.string.verify_online_only);
            return;
        }
        Map<String, RequestBody> fields = new HashMap<>();
        fields.put("decision", text(decision));
        if (selectedPlantingDate != null) fields.put("confirmedPlantingDate", text(selectedPlantingDate));
        if (reason != null) fields.put("rejectionReason", text(reason));
        MultipartBody.Part photo = null;
        if ("ACTIVE".equals(decision)) {
            if (photoFile == null || photoFix == null) {
                Ui.toast(binding.getRoot(), R.string.verify_photo_required);
                return;
            }
            fields.put("photoLat", text(String.valueOf(photoFix.getLatitude())));
            fields.put("photoLng", text(String.valueOf(photoFix.getLongitude())));
            fields.put("photoAccuracyM", text(String.valueOf(photoFix.getAccuracy())));
            fields.put("photoTakenAt", text(isoUtc(photoTakenAtMillis)));
            photo = MultipartBody.Part.createFormData("photo", photoFile.getName(),
                    RequestBody.create(JPEG, photoFile));
        }

        setBusy(true);
        apiService.verifyAgentPlot(plotId, fields, photo).enqueue(new Callback<PlotResponse>() {
            @Override
            public void onResponse(@NonNull Call<PlotResponse> call, @NonNull Response<PlotResponse> response) {
                setBusy(false);
                if (!response.isSuccessful() || response.body() == null) {
                    showProblem(response);
                    return;
                }
                saveLocally(response.body());
                if (photoFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    photoFile.delete();
                }
                Ui.toast(binding.getRoot(), "ACTIVE".equals(decision)
                        ? R.string.plot_verified_success : R.string.plot_rejected_success);
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onFailure(@NonNull Call<PlotResponse> call, @NonNull Throwable t) {
                setBusy(false);
                Ui.error(binding.getRoot(), getString(R.string.error_network_action), () -> submit(decision, reason));
            }
        });
    }

    private static RequestBody text(String value) {
        return RequestBody.create(TEXT, value);
    }

    private static String isoUtc(long millis) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        return iso.format(new Date(millis));
    }

    private void setBusy(boolean busy) {
        binding.progressVerify.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        if (busy) {
            binding.btnVerify.setEnabled(false);
            binding.btnReject.setEnabled(false);
        } else if (plot != null) {
            displayPlot(plot);
        }
    }

    private void showProblem(Response<?> response) {
        Ui.dialog(this)
                .setTitle(R.string.error_generic)
                .setMessage(ApiErrorParser.getFriendlyMessage(this, ApiErrorParser.parse(response)))
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    private void saveLocally(PlotResponse p) {
        final Context app = getApplicationContext();
        new Thread(() -> {
            PlotEntity e = new PlotEntity();
            e.id = p.getId();
            e.farmerId = p.getFarmerId();
            e.name = p.getName();
            e.crop = p.getCrop();
            e.boundary = p.getBoundary();
            e.areaHa = p.getAreaHa();
            e.plantingDate = p.getPlantingDate();
            e.status = p.getStatus();
            e.dataStatus = p.getDataStatus();
            e.isDemo = p.isDemo();
            e.createdAt = p.getCreatedAt();
            e.updatedAt = p.getUpdatedAt();
            e.cachedAt = System.currentTimeMillis();
            RindaDatabase.getInstance(app).plotDao().insertOrUpdate(e);
        }).start();
    }

    // ===== Lifecycle =====

    @Override
    protected void onResume() {
        super.onResume();
        binding.mapView.onResume();
        startListening();
        updatePhotoUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(fixTimeout);
        stopListening();
        super.onDestroy();
    }
}
