package com.rinda.app.ui.alerts;

import android.content.Context;
import androidx.annotation.Nullable;
import com.rinda.app.data.local.NotificationEntity;
import com.rinda.app.data.local.RindaDatabase;
import com.rinda.app.data.model.NotificationResponse;
import java.util.ArrayList;
import java.util.List;

/** Saves alerts from the server into the phone's offline copy. Home and the Alerts tab both read that copy. */
public final class NotificationCache {

    private NotificationCache() {
    }

    public static void save(Context context, @Nullable List<NotificationResponse> list) {
        if (list == null) return;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            List<NotificationEntity> entities = new ArrayList<>();
            for (NotificationResponse r : list) {
                NotificationEntity e = new NotificationEntity();
                e.id = r.getId();
                e.type = r.getType();
                e.code = r.getCode();
                e.title = r.getTitle();
                e.body = r.getBody();
                e.plotId = r.getPlotId();
                e.plotName = r.getPlotName();
                e.link = r.getLink();
                e.isRead = r.isRead();
                e.isDemo = r.isDemo();
                e.createdAt = r.getCreatedAt();
                e.cachedAt = System.currentTimeMillis();
                entities.add(e);
            }
            RindaDatabase.getInstance(app).notificationDao().insertAll(entities);
        }).start();
    }
}
