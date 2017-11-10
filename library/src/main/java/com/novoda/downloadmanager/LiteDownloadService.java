package com.novoda.downloadmanager;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LiteDownloadService extends Service implements DownloadService {

    private static final String WAKELOCK_TAG = "WakelockTag";

    private ExecutorService executor;
    private IBinder binder;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        executor = Executors.newSingleThreadExecutor();
        binder = new DownloadServiceBinder();

        super.onCreate();
    }

    class DownloadServiceBinder extends Binder {
        DownloadService getService() {
            return LiteDownloadService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void download(final DownloadBatch downloadBatch, final DownloadBatchCallback callback) {
        callback.onUpdate(downloadBatch.status());

        downloadBatch.setCallback(callback);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                acquireCpuWakeLock();
                downloadBatch.download();
                releaseCpuWakeLock();
            }
        });
    }

    @Override
    public void updateNotification(NotificationInformation notificationInformation) {
        startForeground(notificationInformation.getId(), notificationInformation.getNotification());
    }

    @Override
    public void makeNotificationDismissible(NotificationInformation notificationInformation) {
        stopForeground(true);

        showFinalDownloadedNotification(notificationInformation);
    }

    private void showFinalDownloadedNotification(NotificationInformation notificationInformation) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            Notification notification = notificationInformation.getNotification();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.notify(notification.getChannelId(), notificationInformation.getId(), notification);
            } else {
                notificationManager.notify(notificationInformation.getId(), notification);
            }
        }
    }

    private void acquireCpuWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
            wakeLock.acquire();
        }
    }

    private void releaseCpuWakeLock() {
        wakeLock.release();
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }
}
