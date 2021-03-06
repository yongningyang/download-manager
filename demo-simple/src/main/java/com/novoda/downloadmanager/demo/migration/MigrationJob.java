package com.novoda.downloadmanager.demo.migration;

import android.annotation.SuppressLint;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.util.Log;

import com.novoda.downloadmanager.CompletedDownloadBatch;
import com.novoda.downloadmanager.DownloadManager;
import com.novoda.downloadmanager.FileSizeExtractor;
import com.novoda.downloadmanager.SqlDatabaseWrapper;
import com.novoda.downloadmanager.StorageRoot;

import java.io.File;
import java.util.List;

public class MigrationJob implements Runnable {

    @SuppressLint("SdCardPath")
    private static final String V1_BASE_PATH = "/data/data/com.novoda.downloadmanager.demo.simple/files/Pictures/";

    private final File databaseFile;
    private final StorageRoot primaryStorageWithDownloadsSubpackage;
    private final StorageRoot primaryStorageWithPicturesSubpackage;
    private final DownloadManager downloadManager;
    private final Handler callbackHandler;
    private final MigrationJobCallback migrationJobCallback;

    public interface MigrationJobCallback {
        void onUpdate(String message);
    }

    public MigrationJob(File databaseFile,
                        StorageRoot primaryStorageWithDownloadsSubpackage,
                        StorageRoot primaryStorageWithPicturesSubpackage,
                        DownloadManager downloadManager,
                        Handler callbackHandler,
                        MigrationJobCallback migrationJobCallback) {
        this.databaseFile = databaseFile;
        this.primaryStorageWithDownloadsSubpackage = primaryStorageWithDownloadsSubpackage;
        this.primaryStorageWithPicturesSubpackage = primaryStorageWithPicturesSubpackage;
        this.downloadManager = downloadManager;
        this.callbackHandler = callbackHandler;
        this.migrationJobCallback = migrationJobCallback;
    }

    @Override
    public void run() {
        SQLiteDatabase sqLiteDatabase = SQLiteDatabase.openDatabase(databaseFile.getAbsolutePath(), null, 0);
        SqlDatabaseWrapper database = new SqlDatabaseWrapper(sqLiteDatabase);
        PartialDownloadBatchesExtractor partialDownloadMigrationExtractor = new PartialDownloadBatchesExtractor(
                database,
                primaryStorageWithDownloadsSubpackage
        );

        FileSizeExtractor fileSizeExtractor = new FileSizeExtractor();
        CompletedDownloadBatchesExtractor migrationExtractor = new CompletedDownloadBatchesExtractor(
                database,
                V1_BASE_PATH,
                fileSizeExtractor,
                primaryStorageWithPicturesSubpackage
        );

        onUpdate("Extracting V1 downloads");
        List<VersionOnePartialDownloadBatch> partialDownloadBatches = partialDownloadMigrationExtractor.extractMigrations();
        List<CompletedDownloadBatch> completeDownloadBatches = migrationExtractor.extractMigrations();

        onUpdate("Queuing Partial Downloads");
        for (VersionOnePartialDownloadBatch partialDownloadBatch : partialDownloadBatches) {
            downloadManager.download(partialDownloadBatch.batch());

            for (String originalFileLocation : partialDownloadBatch.originalFileLocations()) {
                deleteVersionOneFile(originalFileLocation);
            }
        }

        onUpdate("Migrating Complete Downloads");
        for (CompletedDownloadBatch completeDownloadBatch : completeDownloadBatches) {
            downloadManager.addCompletedBatch(completeDownloadBatch);
        }

        onUpdate("Deleting V1 Database");
        database.deleteDatabase();
        onUpdate("Completed Migration");
    }

    private void deleteVersionOneFile(String originalFileLocation) {
        if (originalFileLocation != null && !originalFileLocation.isEmpty()) {
            File file = new File(originalFileLocation);
            boolean deleted = file.delete();
            if (!deleted) {
                String message = String.format("Could not delete File or Directory: %s", file.getPath());
                Log.e(getClass().getSimpleName(), message);
            }
        }
    }

    private void onUpdate(String message) {
        callbackHandler.post(() -> migrationJobCallback.onUpdate(message));
    }
}
