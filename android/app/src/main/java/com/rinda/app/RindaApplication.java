package com.rinda.app;

import android.app.Application;
import com.rinda.app.data.api.ApiClient;
import com.rinda.app.data.api.TokenManager;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.sync.OutboxSyncWorker;
import org.osmdroid.config.Configuration;

public class RindaApplication extends Application {

    private static RindaApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Configure osmdroid user agent to follow OpenStreetMap tile usage policies
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Warm up local SQLite Room database and security preferences
        RindaDatabase.getInstance(this);
        TokenManager.getInstance(this);
        ApiClient.getInstance(this);

        // Schedule offline SQLite outbox sync via WorkManager
        OutboxSyncWorker.schedulePeriodicSync(this);
    }

    public static RindaApplication getInstance() {
        return instance;
    }
}
