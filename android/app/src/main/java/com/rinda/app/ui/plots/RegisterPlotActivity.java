package com.rinda.app.ui.plots;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.rinda.app.R;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.ApiErrorParser;
import com.rinda.app.data.local.PlotEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.GeoJsonPolygon;
import com.rinda.app.data.model.PlotRegistration;
import com.rinda.app.data.model.PlotResponse;
import com.rinda.app.data.model.PlotStatus;
import com.rinda.app.databinding.ActivityRegisterPlotBinding;
import com.rinda.app.databinding.ViewDetailRowBinding;
import com.rinda.app.ui.common.InfoSheet;
import com.rinda.app.ui.common.StatusUi;
import com.rinda.app.ui.common.Ui;
import com.rinda.app.util.DateTimeUtils;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Add a plot (docs/01-plot-verification.md). There is no drawing: the boundary comes only from the GPS walk, and
 * the server builds it from the walked points.
 * <ol>
 *   <li>UPI: type it and see the parcel the registry provider knows. A guide only; mock data is labelled Demo.</li>
 *   <li>Details: name, planting date, relationship to the plot, declaration. Creates the plot (DRAFT).</li>
 *   <li>Walk: at each corner the farmer taps "I'm at this corner" and the current GPS fix is recorded.</li>
 *   <li>E-title or portal screenshot with its typed area; a lease or the owner's consent for non-owners.</li>
 * </ol>
 * Then a result with the status, the verification level and every check. Open with {@link #EXTRA_PLOT_ID} to
 * continue an unfinished registration from the walk.
 *
 * <p>Walk points are personal data: they are never logged and never stored on the phone after sending.
 */
public class RegisterPlotActivity extends AppCompatActivity {

    public static final String EXTRA_PLOT_ID = "extra_plot_id";

    private static final int STEP_UPI = 1;
    private static final int STEP_DETAILS = 2;
    private static final int STEP_WALK = 3;
    private static final int STEP_DOCUMENT = 4;
    private static final int STEP_RESULT = 5;
    private static final int TOTAL_STEPS = 4;
    private static final int MIN_CORNERS = 3;
    private static final int MAX_POINTS = 499;
    /** A fix older than this is not used for a corner: the farmer may have moved since. */
    private static final long MAX_FIX_AGE_MS = 10_000;
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final Pattern UPI_PATTERN = Pattern.compile("^\\d{1,3}/\\d{1,3}/\\d{1,3}/\\d{1,3}/\\d{1,8}$");

    private ActivityRegisterPlotBinding binding;
    private int step = STEP_UPI;

    // Step 1
    private String lookedUpUpi;
    @Nullable private PlotRegistration.Parcel parcel;
    private boolean parcelIsMock;

    // Step 2
    private final Calendar calendar = Calendar.getInstance();
    private String selectedPlantingDate;

    // The plot, once created (or when continuing one)
    private long plotId = -1;
    @Nullable private PlotResponse plot;

    // Step 3: walk
    private LocationManager locationManager;
    private boolean listening;
    @Nullable private Location latestFix;
    private final List<Location> corners = new ArrayList<>();
    private final List<Marker> cornerMarkers = new ArrayList<>();
    @Nullable private Polygon walkPolygon;
    @Nullable private Polyline walkLine;
    @Nullable private Marker herePosition;
    private boolean centredOnFix;

    // Step 4: documents
    @Nullable private Uri proofUri;
    @Nullable private Uri tenureUri;
    private boolean pickingTenure;

    private final LocationListener locationListener = this::onFix;

    private final ActivityResultLauncher<String> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startListening();
                } else {
                    binding.tvGpsStatus.setText(R.string.reg_walk_permission);
                }
            });

    private final ActivityResultLauncher<String[]> pickDocument = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                String name = displayName(uri);
                if (pickingTenure) {
                    tenureUri = uri;
                    binding.tvTenureFile.setText(getString(R.string.reg_document_picked, name));
                } else {
                    proofUri = uri;
                    binding.tvDocumentFile.setText(getString(R.string.reg_document_picked, name));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        binding = ActivityRegisterPlotBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        setupUpiStep();
        setupDetailsStep();
        setupWalkStep();
        setupDocumentStep();

        binding.btnNext.setOnClickListener(v -> onNext());
        binding.btnBack.setOnClickListener(v -> onBack());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBack();
            }
        });

        long existing = getIntent().getLongExtra(EXTRA_PLOT_ID, -1);
        if (existing > 0) {
            plotId = existing;
            loadExistingPlot();
        } else {
            goTo(STEP_UPI);
        }
    }

    // ===== Navigation =====

    private void onNext() {
        switch (step) {
            case STEP_UPI:
                if (lookedUpUpi == null || !lookedUpUpi.equals(upiText())) {
                    Ui.toast(binding.getRoot(), R.string.reg_lookup_first);
                    return;
                }
                goTo(STEP_DETAILS);
                break;
            case STEP_DETAILS:
                if (validateDetails()) {
                    createPlot();
                }
                break;
            case STEP_WALK:
                submitWalk();
                break;
            case STEP_DOCUMENT:
                uploadDocuments();
                break;
            case STEP_RESULT:
            default:
                finish();
                break;
        }
    }

    /**
     * Back never undoes a saved plot. From the e-title step it goes back to the walk (walking again replaces the
     * boundary); from the walk it closes, and the plot stays unfinished until the farmer continues it.
     */
    private void onBack() {
        if (step == STEP_DETAILS) {
            goTo(STEP_UPI);
        } else if (step == STEP_DOCUMENT) {
            goTo(STEP_WALK);
        } else {
            finish();
        }
    }

    private void goTo(int newStep) {
        if (step == STEP_WALK && newStep != STEP_WALK) {
            stopListening();
        }
        step = newStep;
        show(binding.stepUpi, step == STEP_UPI);
        show(binding.stepDetails, step == STEP_DETAILS);
        show(binding.stepWalk, step == STEP_WALK);
        show(binding.stepDocument, step == STEP_DOCUMENT);
        show(binding.stepResult, step == STEP_RESULT);

        if (step == STEP_RESULT) {
            binding.stepHeader.setVisibility(View.GONE);
            binding.btnBack.setVisibility(View.GONE);
            binding.btnNext.setText(R.string.reg_btn_done);
            binding.btnNext.setIcon(null);
            binding.btnNext.setEnabled(true);
            return;
        }

        binding.stepHeader.setVisibility(View.VISIBLE);
        binding.stepProgress.setProgressCompat(step, true);
        binding.tvStepLabel.setText(getString(R.string.step_of, step, TOTAL_STEPS));
        binding.tvStepTitle.setText(step == STEP_UPI ? R.string.reg_step_upi
                : step == STEP_DETAILS ? R.string.reg_step_details
                : step == STEP_WALK ? R.string.reg_step_walk : R.string.reg_step_document);
        // Once the plot exists, going back to the UPI or details would not change it, so Back closes instead.
        binding.btnBack.setVisibility(step == STEP_DETAILS || step == STEP_DOCUMENT ? View.VISIBLE : View.GONE);
        binding.btnNext.setEnabled(true);
        binding.btnNext.setIconResource(R.drawable.ic_arrow_forward);
        switch (step) {
            case STEP_DETAILS:
                binding.btnNext.setText(R.string.reg_btn_create);
                break;
            case STEP_WALK:
                binding.btnNext.setText(R.string.reg_btn_finish_walk);
                binding.btnNext.setIconResource(R.drawable.ic_check);
                enterWalk();
                break;
            case STEP_DOCUMENT:
                binding.btnNext.setText(R.string.reg_btn_upload);
                binding.cardTenure.setVisibility(isNonOwner() ? View.VISIBLE : View.GONE);
                break;
            default:
                binding.btnNext.setText(R.string.btn_next);
                break;
        }
    }

    private void show(View view, boolean visible) {
        if (visible) {
            Ui.reveal(view);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    // ===== Continue an unfinished registration =====

    private void loadExistingPlot() {
        setLoading(true);
        ApiClient.getInstance(this).getService().getPlot(plotId).enqueue(new Callback<PlotResponse>() {
            @Override
            public void onResponse(@NonNull Call<PlotResponse> call, @NonNull Response<PlotResponse> response) {
                setLoading(false);
                if (!response.isSuccessful() || response.body() == null) {
                    showProblem(response);
                    return;
                }
                plot = response.body();
                if (plot.getUpi() != null) {
                    lookUp(plot.getUpi(), false);
                }
                boolean needsWalk = !plotWalked() || plot.getStatus() == PlotStatus.REJECTED
                        || hasOpenFlag("LEGACY_DRAWN_BOUNDARY");
                goTo(needsWalk ? STEP_WALK : STEP_DOCUMENT);
            }

            @Override
            public void onFailure(@NonNull Call<PlotResponse> call, @NonNull Throwable t) {
                setLoading(false);
                Ui.error(binding.getRoot(), getString(R.string.error_network_action),
                        RegisterPlotActivity.this::loadExistingPlot);
            }
        });
    }

    private boolean plotWalked() {
        return plot != null && plot.checkRan("GPS_WALK") && plot.getWalkedAreaM2() != null;
    }

    private boolean hasOpenFlag(String type) {
        if (plot == null) return false;
        for (PlotRegistration.Flag f : plot.getFlags()) {
            if (type.equals(f.type) && f.resolvedAt == null) return true;
        }
        return false;
    }

    private boolean isNonOwner() {
        if (plot != null && plot.getRelationshipToPlot() != null) {
            return !"OWNER".equals(plot.getRelationshipToPlot());
        }
        return !"OWNER".equals(selectedRelationship()) && selectedRelationship() != null;
    }

    // ===== Step 1: UPI =====

    private void setupUpiStep() {
        binding.mapParcel.setMultiTouchControls(true);
        binding.btnLookup.setOnClickListener(v -> {
            String upi = upiText();
            binding.tilUpi.setError(null);
            if (upi.isEmpty()) {
                binding.tilUpi.setError(getString(R.string.reg_upi_required));
            } else if (!UPI_PATTERN.matcher(upi).matches()) {
                binding.tilUpi.setError(getString(R.string.reg_upi_format));
            } else {
                lookUp(upi, true);
            }
        });
        binding.etUpi.setOnEditorActionListener((v, actionId, event) -> {
            binding.btnLookup.performClick();
            return true;
        });
    }

    private String upiText() {
        return binding.etUpi.getText() != null ? binding.etUpi.getText().toString().trim() : "";
    }

    private void lookUp(String upi, boolean showCard) {
        if (showCard) setLoading(true);
        ApiClient.getInstance(this).getService().lookupParcel(upi)
                .enqueue(new Callback<PlotRegistration.ParcelLookupResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<PlotRegistration.ParcelLookupResponse> call,
                                           @NonNull Response<PlotRegistration.ParcelLookupResponse> response) {
                        if (showCard) setLoading(false);
                        if (!response.isSuccessful() || response.body() == null) {
                            if (showCard) showProblem(response);
                            return;
                        }
                        PlotRegistration.ParcelLookupResponse result = response.body();
                        lookedUpUpi = upi;
                        parcel = result.found ? result.parcel : null;
                        parcelIsMock = result.isMock;
                        if (showCard) {
                            showParcel(result);
                        }
                        if (step == STEP_WALK) {
                            // Resuming: the lookup can finish after the walk step opened.
                            drawParcelReference(binding.mapWalk);
                            if (parcel != null && parcel.boundary != null && corners.isEmpty()) {
                                zoomTo(binding.mapWalk, ring(parcel.boundary));
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<PlotRegistration.ParcelLookupResponse> call,
                                         @NonNull Throwable t) {
                        if (showCard) {
                            setLoading(false);
                            Ui.error(binding.getRoot(), getString(R.string.error_network_action),
                                    () -> lookUp(upi, true));
                        }
                    }
                });
    }

    private void showParcel(PlotRegistration.ParcelLookupResponse result) {
        binding.cardParcel.setVisibility(View.VISIBLE);
        binding.tvParcelNotice.setText(result.notice);
        boolean found = result.found && result.parcel != null;
        binding.tvParcelDemo.setVisibility(found && result.isMock ? View.VISIBLE : View.GONE);
        binding.mapParcel.setVisibility(found && result.parcel.boundary != null ? View.VISIBLE : View.GONE);
        binding.tvParcelArea.setVisibility(found && result.parcel.areaHa != null ? View.VISIBLE : View.GONE);
        binding.tvParcelLocation.setVisibility(found && result.parcel.location != null ? View.VISIBLE : View.GONE);
        binding.tvParcelLandUse.setVisibility(found && result.parcel.landUse != null ? View.VISIBLE : View.GONE);
        if (!found) return;
        if (result.parcel.areaHa != null) {
            binding.tvParcelArea.setText(getString(R.string.reg_parcel_area,
                    result.parcel.areaHa.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()));
        }
        if (result.parcel.location != null) {
            binding.tvParcelLocation.setText(getString(R.string.reg_parcel_location, result.parcel.location));
        }
        if (result.parcel.landUse != null) {
            binding.tvParcelLandUse.setText(getString(R.string.reg_parcel_land_use, result.parcel.landUse));
        }
        if (result.parcel.boundary != null) {
            binding.mapParcel.getOverlays().clear();
            drawParcelReference(binding.mapParcel);
            zoomTo(binding.mapParcel, ring(result.parcel.boundary));
        }
    }

    /** The parcel from the registry, as a gray dashed guide. Never the plot's boundary. */
    private void drawParcelReference(MapView map) {
        if (parcel == null || parcel.boundary == null) return;
        Polygon reference = new Polygon(map);
        reference.setPoints(ring(parcel.boundary));
        int outline = ContextCompat.getColor(this, R.color.rinda_text_tertiary);
        reference.getOutlinePaint().setColor(outline);
        reference.getOutlinePaint().setStrokeWidth(5f);
        reference.getOutlinePaint().setPathEffect(new android.graphics.DashPathEffect(new float[] {20f, 14f}, 0f));
        reference.getFillPaint().setColor((outline & 0x00FFFFFF) | 0x22000000);
        reference.setTitle(getString(parcelIsMock ? R.string.reg_parcel_demo : R.string.reg_parcel_title));
        map.getOverlays().add(0, reference);
        map.invalidate();
    }

    private static List<GeoPoint> ring(GeoJsonPolygon polygon) {
        List<GeoPoint> points = new ArrayList<>();
        if (polygon.getCoordinates() == null || polygon.getCoordinates().isEmpty()) return points;
        for (List<Double> c : polygon.getCoordinates().get(0)) {
            points.add(new GeoPoint(c.get(1), c.get(0))); // GeoJSON is [lng, lat]
        }
        return points;
    }

    private static void zoomTo(MapView map, List<GeoPoint> points) {
        if (points.isEmpty()) return;
        BoundingBox box = BoundingBox.fromGeoPointsSafe(points);
        map.post(() -> map.zoomToBoundingBox(box.increaseByScale(1.6f), false));
    }

    // ===== Step 2: details =====

    private void setupDetailsStep() {
        binding.etPlantingDate.setOnClickListener(v -> showDatePicker());
        binding.tilPlantingDate.setEndIconOnClickListener(v ->
                InfoSheet.show(this, R.drawable.ic_calendar, R.string.help_planting_title, R.string.help_planting_body));
        binding.rgRelationship.setOnCheckedChangeListener((group, checkedId) -> {
            binding.tvRelationshipError.setVisibility(View.GONE);
            binding.tvRelationshipNote.setVisibility(
                    checkedId != -1 && checkedId != R.id.rbOwner ? View.VISIBLE : View.GONE);
        });
        binding.cbDeclaration.setOnCheckedChangeListener((b, checked) -> {
            if (checked) binding.tvDeclarationError.setVisibility(View.GONE);
        });
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            calendar.set(year, month, day);
            selectedPlantingDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
            binding.etPlantingDate.setText(DateTimeUtils.formatDate(selectedPlantingDate));
            binding.tilPlantingDate.setError(null);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis()); // can't be in the future
        dialog.show();
    }

    @Nullable
    private String selectedRelationship() {
        int id = binding.rgRelationship.getCheckedRadioButtonId();
        if (id == R.id.rbOwner) return "OWNER";
        if (id == R.id.rbFamily) return "FAMILY_MEMBER";
        if (id == R.id.rbTenant) return "TENANT";
        return null;
    }

    private String plotName() {
        return binding.etPlotName.getText() != null ? binding.etPlotName.getText().toString().trim() : "";
    }

    private boolean validateDetails() {
        binding.tilPlotName.setError(null);
        binding.tilPlantingDate.setError(null);
        boolean ok = true;
        if (plotName().isEmpty()) {
            binding.tilPlotName.setError(getString(R.string.error_plot_name_required));
            ok = false;
        }
        if (selectedPlantingDate == null) {
            binding.tilPlantingDate.setError(getString(R.string.error_planting_date_required));
            ok = false;
        }
        if (selectedRelationship() == null) {
            binding.tvRelationshipError.setVisibility(View.VISIBLE);
            ok = false;
        }
        if (!binding.cbDeclaration.isChecked()) {
            binding.tvDeclarationError.setVisibility(View.VISIBLE);
            ok = false;
        }
        return ok;
    }

    private void createPlot() {
        PlotRegistration.CreateRequest request = new PlotRegistration.CreateRequest(plotName(), lookedUpUpi,
                selectedPlantingDate, selectedRelationship(), binding.cbDeclaration.isChecked());
        setLoading(true);
        ApiClient.getInstance(this).getService().createPlot(request).enqueue(new Callback<PlotResponse>() {
            @Override
            public void onResponse(@NonNull Call<PlotResponse> call, @NonNull Response<PlotResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    plot = response.body();
                    plotId = plot.getId();
                    savePlotLocally(plot);
                    goTo(STEP_WALK);
                } else {
                    showProblem(response);
                }
            }

            @Override
            public void onFailure(@NonNull Call<PlotResponse> call, @NonNull Throwable t) {
                setLoading(false);
                Ui.error(binding.getRoot(), getString(R.string.error_network_action), RegisterPlotActivity.this::createPlot);
            }
        });
    }

    // ===== Step 3: GPS walk =====

    private void setupWalkStep() {
        binding.mapWalk.setMultiTouchControls(true);
        binding.mapWalk.getController().setZoom(17.0);
        binding.mapWalk.getController().setCenter(new GeoPoint(-1.95, 30.06));
        binding.btnMarkCorner.setOnClickListener(v -> markCorner());
        binding.btnUndoCorner.setOnClickListener(v -> undoCorner());
        binding.btnRestartWalk.setOnClickListener(v -> restartWalk());
        binding.btnWalkHelp.setOnClickListener(v ->
                InfoSheet.show(this, R.drawable.ic_map_pin, R.string.reg_help_walk_title, R.string.reg_help_walk_body));
    }

    private void enterWalk() {
        binding.mapWalk.getOverlays().clear();
        cornerMarkers.clear();
        walkPolygon = null;
        walkLine = null;
        herePosition = null;
        drawParcelReference(binding.mapWalk);
        if (parcel != null && parcel.boundary != null && corners.isEmpty()) {
            zoomTo(binding.mapWalk, ring(parcel.boundary));
        }
        for (int i = 0; i < corners.size(); i++) {
            addCornerMarker(corners.get(i), i + 1);
        }
        redrawWalk();
        updateWalkUi();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            binding.tvGpsStatus.setText(R.string.reg_walk_permission);
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission") // Checked by the caller.
    private void startListening() {
        if (listening || locationManager == null) return;
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            binding.tvGpsStatus.setText(R.string.reg_walk_gps_off);
            return;
        }
        binding.tvGpsStatus.setText(R.string.reg_walk_waiting_gps);
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener,
                    Looper.getMainLooper());
            listening = true;
        } catch (SecurityException e) {
            binding.tvGpsStatus.setText(R.string.reg_walk_permission);
        }
    }

    private void stopListening() {
        if (listening && locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        listening = false;
    }

    private void onFix(@NonNull Location location) {
        latestFix = location;
        int accuracy = location.hasAccuracy() ? Math.round(location.getAccuracy()) : -1;
        binding.tvGpsStatus.setText(accuracy >= 0 ? getString(R.string.reg_walk_gps_ready, accuracy)
                : getString(R.string.reg_walk_waiting_gps));
        binding.tvMockWarning.setVisibility(isMock(location) ? View.VISIBLE : View.GONE);
        GeoPoint here = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (herePosition == null) {
            herePosition = new Marker(binding.mapWalk);
            herePosition.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            herePosition.setIcon(ContextCompat.getDrawable(this, R.drawable.bg_unread_dot));
            binding.mapWalk.getOverlays().add(herePosition);
        }
        herePosition.setPosition(here);
        if (!centredOnFix) {
            binding.mapWalk.getController().animateTo(here);
            centredOnFix = true;
        }
        binding.mapWalk.invalidate();
        updateWalkUi();
    }

    private static boolean isMock(Location location) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? location.isMock() : location.isFromMockProvider();
    }

    private boolean fixIsFresh(Location location) {
        long ageMs = (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1_000_000;
        return ageMs <= MAX_FIX_AGE_MS;
    }

    /** Records the current GPS fix as the next corner. Only a fresh, new reading is ever used. */
    private void markCorner() {
        Location fix = latestFix;
        if (fix == null || !fixIsFresh(fix)) {
            Ui.toast(binding.getRoot(), R.string.reg_walk_no_fix);
            return;
        }
        // The server needs each point later than the one before, so a reading can't be used twice.
        if (!corners.isEmpty() && fix.getTime() <= corners.get(corners.size() - 1).getTime()) {
            Ui.toast(binding.getRoot(), R.string.reg_walk_new_reading);
            return;
        }
        if (corners.size() >= MAX_POINTS) return;
        corners.add(new Location(fix));
        addCornerMarker(fix, corners.size());
        binding.mapWalk.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        redrawWalk();
        updateWalkUi();
    }

    private void addCornerMarker(Location fix, int number) {
        Marker marker = new Marker(binding.mapWalk);
        marker.setPosition(new GeoPoint(fix.getLatitude(), fix.getLongitude()));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(getString(R.string.reg_walk_corner_title, number));
        binding.mapWalk.getOverlays().add(marker);
        cornerMarkers.add(marker);
    }

    private void undoCorner() {
        if (corners.isEmpty()) return;
        corners.remove(corners.size() - 1);
        binding.mapWalk.getOverlays().remove(cornerMarkers.remove(cornerMarkers.size() - 1));
        redrawWalk();
        updateWalkUi();
    }

    private void restartWalk() {
        corners.clear();
        for (Marker m : cornerMarkers) {
            binding.mapWalk.getOverlays().remove(m);
        }
        cornerMarkers.clear();
        redrawWalk();
        updateWalkUi();
    }

    private void redrawWalk() {
        if (walkPolygon != null) binding.mapWalk.getOverlays().remove(walkPolygon);
        if (walkLine != null) binding.mapWalk.getOverlays().remove(walkLine);
        walkPolygon = null;
        walkLine = null;
        List<GeoPoint> points = new ArrayList<>();
        for (Location c : corners) {
            points.add(new GeoPoint(c.getLatitude(), c.getLongitude()));
        }
        int primary = ContextCompat.getColor(this, R.color.rinda_primary);
        if (points.size() >= MIN_CORNERS) {
            walkPolygon = new Polygon(binding.mapWalk);
            List<GeoPoint> closed = new ArrayList<>(points);
            closed.add(points.get(0));
            walkPolygon.setPoints(closed);
            walkPolygon.getOutlinePaint().setColor(primary);
            walkPolygon.getOutlinePaint().setStrokeWidth(6f);
            int fill = ContextCompat.getColor(this, R.color.rinda_primary_container);
            walkPolygon.getFillPaint().setColor((fill & 0x00FFFFFF) | 0x66000000);
            binding.mapWalk.getOverlays().add(walkPolygon);
        } else if (points.size() == 2) {
            walkLine = new Polyline(binding.mapWalk);
            walkLine.setPoints(points);
            walkLine.getOutlinePaint().setColor(primary);
            walkLine.getOutlinePaint().setStrokeWidth(6f);
            binding.mapWalk.getOverlays().add(walkLine);
        }
        binding.mapWalk.invalidate();
    }

    private void updateWalkUi() {
        int count = corners.size();
        boolean ready = count >= MIN_CORNERS;
        binding.tvCornerCount.setText(ready ? getString(R.string.reg_walk_corners, count)
                : getString(R.string.reg_walk_corners_needed, MIN_CORNERS - count));
        binding.tvWalkArea.setText(ready ? getString(R.string.reg_walk_area_estimate, approxAreaHa())
                : getString(R.string.reg_walk_area_empty));
        binding.btnUndoCorner.setEnabled(count > 0);
        binding.btnRestartWalk.setEnabled(count > 0);
        binding.btnMarkCorner.setEnabled(latestFix != null);
        if (step == STEP_WALK) binding.btnNext.setEnabled(ready);
    }

    /** Planar estimate for feedback only. The server measures the real area. */
    private double approxAreaHa() {
        double area = 0;
        int n = corners.size();
        for (int i = 0; i < n; i++) {
            Location a = corners.get(i);
            Location b = corners.get((i + 1) % n);
            double x1 = Math.toRadians(a.getLongitude()) * 6378137.0 * Math.cos(Math.toRadians(a.getLatitude()));
            double y1 = Math.toRadians(a.getLatitude()) * 6378137.0;
            double x2 = Math.toRadians(b.getLongitude()) * 6378137.0 * Math.cos(Math.toRadians(b.getLatitude()));
            double y2 = Math.toRadians(b.getLatitude()) * 6378137.0;
            area += x1 * y2 - x2 * y1;
        }
        return Math.abs(area) / 20000.0;
    }

    private void submitWalk() {
        if (corners.size() < MIN_CORNERS) return;
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        List<PlotRegistration.WalkPoint> points = new ArrayList<>();
        for (Location c : corners) {
            points.add(new PlotRegistration.WalkPoint(c.getLatitude(), c.getLongitude(),
                    c.hasAccuracy() ? (double) c.getAccuracy() : null, iso.format(new Date(c.getTime())), isMock(c)));
        }
        setLoading(true);
        ApiClient.getInstance(this).getService().walkPlot(plotId, new PlotRegistration.WalkRequest(points))
                .enqueue(new Callback<PlotResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<PlotResponse> call, @NonNull Response<PlotResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            plot = response.body();
                            savePlotLocally(plot);
                            corners.clear(); // The points are on the server now; nothing stays on the phone.
                            goTo(STEP_DOCUMENT);
                        } else {
                            showProblem(response);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<PlotResponse> call, @NonNull Throwable t) {
                        setLoading(false);
                        Ui.error(binding.getRoot(), getString(R.string.error_network_action),
                                RegisterPlotActivity.this::submitWalk);
                    }
                });
    }

    // ===== Step 4: documents =====

    private void setupDocumentStep() {
        String[] types = {"image/jpeg", "image/png", "application/pdf"};
        binding.btnPickDocument.setOnClickListener(v -> {
            pickingTenure = false;
            pickDocument.launch(types);
        });
        binding.btnPickTenure.setOnClickListener(v -> {
            pickingTenure = true;
            pickDocument.launch(types);
        });
    }

    private boolean proofAlreadyUploaded() {
        return plot != null && plot.checkRan("PROOF_DOCUMENT");
    }

    private void uploadDocuments() {
        binding.tilDocumentArea.setError(null);
        if (proofUri == null && tenureUri == null) {
            if (proofAlreadyUploaded()) {
                refreshPlotAndShowResult();
            } else {
                Ui.toast(binding.getRoot(), R.string.reg_document_required);
            }
            return;
        }
        BigDecimal area = null;
        if (proofUri != null) {
            String typed = binding.etDocumentArea.getText() != null
                    ? binding.etDocumentArea.getText().toString().trim() : "";
            try {
                area = typed.isEmpty() ? null : new BigDecimal(typed);
            } catch (NumberFormatException e) {
                area = null;
            }
            if (area == null || area.signum() <= 0) {
                binding.tilDocumentArea.setError(getString(R.string.reg_document_area_required));
                return;
            }
        }
        String proofType = binding.rgDocumentType.getCheckedRadioButtonId() == R.id.rbScreenshot
                ? "PORTAL_SCREENSHOT" : "E_TITLE";
        String tenureType = binding.rgTenureType.getCheckedRadioButtonId() == R.id.rbOwnerConsent
                ? "OWNER_CONSENT" : "LEASE";
        if (proofUri != null) {
            Uri next = tenureUri;
            upload(proofUri, proofType, area, () -> {
                proofUri = null;
                if (next != null) {
                    upload(next, tenureType, null, () -> {
                        tenureUri = null;
                        refreshPlotAndShowResult();
                    });
                } else {
                    refreshPlotAndShowResult();
                }
            });
        } else {
            upload(tenureUri, tenureType, null, () -> {
                tenureUri = null;
                refreshPlotAndShowResult();
            });
        }
    }

    private void upload(Uri uri, String type, @Nullable BigDecimal areaM2, Runnable then) {
        byte[] bytes;
        String mime = getContentResolver().getType(uri);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("no stream");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_FILE_BYTES) {
                    Ui.toast(binding.getRoot(), R.string.reg_document_too_big);
                    return;
                }
            }
            bytes = out.toByteArray();
        } catch (Exception e) {
            Ui.toast(binding.getRoot(), R.string.reg_document_read_failed);
            return;
        }
        MediaType text = MediaType.parse("text/plain");
        Map<String, RequestBody> fields = new HashMap<>();
        fields.put("type", RequestBody.create(type, text));
        if (areaM2 != null) {
            fields.put("documentAreaM2", RequestBody.create(areaM2.toPlainString(), text));
        }
        MultipartBody.Part file = MultipartBody.Part.createFormData("file", displayName(uri),
                RequestBody.create(bytes, MediaType.parse(mime != null ? mime : "application/octet-stream")));
        setLoading(true);
        ApiClient.getInstance(this).getService().uploadPlotDocument(plotId, fields, file)
                .enqueue(new Callback<PlotRegistration.DocumentResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<PlotRegistration.DocumentResponse> call,
                                           @NonNull Response<PlotRegistration.DocumentResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful()) {
                            Ui.toast(binding.getRoot(), R.string.reg_document_uploaded);
                            then.run();
                        } else {
                            showProblem(response);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<PlotRegistration.DocumentResponse> call,
                                         @NonNull Throwable t) {
                        setLoading(false);
                        Ui.error(binding.getRoot(), getString(R.string.error_network_action),
                                () -> upload(uri, type, areaM2, then));
                    }
                });
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) return name;
            }
        } catch (Exception ignored) {
            // Falls back to a generic name.
        }
        return "document";
    }

    // ===== Result =====

    private void refreshPlotAndShowResult() {
        setLoading(true);
        ApiClient.getInstance(this).getService().getPlot(plotId).enqueue(new Callback<PlotResponse>() {
            @Override
            public void onResponse(@NonNull Call<PlotResponse> call, @NonNull Response<PlotResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    plot = response.body();
                    savePlotLocally(plot);
                }
                showResult();
            }

            @Override
            public void onFailure(@NonNull Call<PlotResponse> call, @NonNull Throwable t) {
                setLoading(false);
                showResult();
            }
        });
    }

    private void showResult() {
        if (plot == null) {
            finish();
            return;
        }
        binding.tvResultBody.setText(getString(R.string.reg_result_body, plot.getName()));
        StatusUi.Spec spec = StatusUi.forPlot(plot.getStatus());
        StatusUi.applyPill(binding.tvResultStatus, spec);
        binding.tvResultStatusMeaning.setText(spec.meaningRes);
        binding.tvResultLevel.setText(getString(R.string.reg_result_level, levelLabel(plot.getVerificationLevel())));

        binding.llChecks.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (PlotRegistration.Check check : plot.getChecks()) {
            ViewDetailRowBinding row = ViewDetailRowBinding.inflate(inflater, binding.llChecks, false);
            row.ivRowIcon.setImageResource(checkIcon(check.result));
            row.tvRowLabel.setText(checkLabel(check.check));
            row.tvRowValue.setText(checkResultLabel(check.result) + (check.note != null && !check.note.isEmpty()
                    ? "\n" + check.note : ""));
            binding.llChecks.addView(row.getRoot());
        }
        goTo(STEP_RESULT);
    }

    private String levelLabel(@Nullable String level) {
        return StatusUi.verificationLevelLabel(this, level);
    }

    private static int checkIcon(@Nullable String result) {
        if ("PASSED".equals(result) || "REVIEWED".equals(result)) return R.drawable.ic_status_healthy;
        if ("FLAGGED".equals(result)) return R.drawable.ic_status_watch;
        if ("PENDING".equals(result)) return R.drawable.ic_status_pending;
        return R.drawable.ic_status_no_data;
    }

    private String checkResultLabel(@Nullable String result) {
        if (result == null) return "-";
        switch (result) {
            case "PASSED":
                return getString(R.string.reg_check_passed);
            case "FLAGGED":
                return getString(R.string.reg_check_flagged);
            case "REVIEWED":
                return getString(R.string.reg_check_reviewed);
            case "PENDING":
                return getString(R.string.reg_check_pending);
            default:
                return getString(R.string.reg_check_not_run);
        }
    }

    /** "GPS_ACCURACY" -> "Gps accuracy". The server's note carries the detail. */
    private static String checkLabel(@Nullable String check) {
        if (check == null || check.isEmpty()) return "-";
        String words = check.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    // ===== Helpers =====

    private void showProblem(Response<?> response) {
        Ui.dialog(this)
                .setTitle(R.string.error_generic)
                .setMessage(ApiErrorParser.getFriendlyMessage(this, ApiErrorParser.parse(response)))
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    private void savePlotLocally(PlotResponse p) {
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

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnNext.setEnabled(!loading && (step != STEP_WALK || corners.size() >= MIN_CORNERS));
        binding.btnBack.setEnabled(!loading);
        binding.btnLookup.setEnabled(!loading);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.mapParcel.onResume();
        binding.mapWalk.onResume();
        if (step == STEP_WALK && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.mapParcel.onPause();
        binding.mapWalk.onPause();
        stopListening();
    }
}
