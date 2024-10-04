package com.secrethq.utils;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.ParcelFileDescriptor;

import androidx.multidex.BuildConfig;

import java.io.IOException;

public class BBSyncAgentHelper extends BackupAgentHelper {
    // The name of the SharedPreferences file
    public static final String PREFS = BuildConfig.APPLICATION_ID + "bb3_user_progress";

    // A key to uniquely identify the set of backup data
    public  static final String PREFS_BACKUP_KEY = "progress";

    // Allocate a helper and add it to the backup agent
    @Override
    public void onCreate() {
        SharedPreferencesBackupHelper helper =  new SharedPreferencesBackupHelper(this, PREFS);
        addHelper(PREFS_BACKUP_KEY, helper);
    }

}