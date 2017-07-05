/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.updater;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import org.json.JSONException;
import org.lineageos.updater.misc.Utils;

import java.io.File;
import java.io.IOException;

public class UpdatesCheckReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdatesCheckReceiver";

    private static final String LAST_UPDATES_CHECK_PREF = "last_update_check";

    private static final String DAILY_CHECK_ACTION = "daily_check_action";
    private static final String ONESHOT_CHECK_ACTION = "oneshot_check_action";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Set a repeating alarm on boot to check for new updates once per day
            scheduleRepeatingUpdatesCheck(context);
        }

        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        long lastCheck = preferences.getLong(LAST_UPDATES_CHECK_PREF, -1);
        final long currentMillis = System.currentTimeMillis();
        if (currentMillis > lastCheck + AlarmManager.INTERVAL_DAY) {
            if (!Utils.isNetworkAvailable(context)) {
                Log.d(TAG, "Network not available, scheduling new check");
                scheduleUpdatesCheck(context);
                return;
            }

            final File json = Utils.getCachedUpdateList(context);
            final File jsonNew = new File(json.getAbsolutePath() + ".tmp");
            String url = Utils.getServerURL(context);
            DownloadClient.downloadFile(url, jsonNew, new DownloadClient.DownloadCallback() {
                @Override
                public void onFailure(boolean cancelled) {
                    Log.e(TAG, "Could not download updates list, scheduling new check");
                    scheduleUpdatesCheck(context);
                }

                @Override
                public void onResponse(int statusCode, String url,
                        DownloadClient.Headers headers) {
                }

                @Override
                public void onSuccess(String response) {
                    try {
                        if (json.exists() && Utils.checkForNewUpdates(json, jsonNew)) {
                            showNotification(context);
                        }
                        jsonNew.renameTo(json);
                        preferences.edit().putLong(LAST_UPDATES_CHECK_PREF, currentMillis).apply();
                    } catch (IOException | JSONException e) {
                        Log.e(TAG, "Could not parse list, scheduling new check", e);
                        scheduleUpdatesCheck(context);
                    }
                }
            });
        }
    }

    private static void showNotification(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
        notificationBuilder.setSmallIcon(R.drawable.ic_system_update);
        Intent notificationIntent = new Intent(context, UpdatesActivity.class);
        PendingIntent intent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(intent);
        notificationBuilder.setContentTitle(context.getString(R.string.new_updates_found_title));
        notificationManager.notify(0, notificationBuilder.build());
    }

    private static void scheduleRepeatingUpdatesCheck(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, UpdatesCheckReceiver.class);
        intent.setAction(DAILY_CHECK_ACTION);
        PendingIntent updateCheckIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_DAY,
                AlarmManager.INTERVAL_DAY, updateCheckIntent);
    }

    private static void scheduleUpdatesCheck(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, UpdatesCheckReceiver.class);
        intent.setAction(ONESHOT_CHECK_ACTION);
        PendingIntent updateCheckIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        alarmMgr.cancel(updateCheckIntent);
        alarmMgr.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HOUR * 2,
                updateCheckIntent);
    }
}