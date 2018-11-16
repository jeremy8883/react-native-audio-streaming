package com.audioStreaming;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.TaskStackBuilder;
import android.widget.RemoteViews;

import static com.audioStreaming.Signal.BROADCAST_EXIT;
import static com.audioStreaming.Signal.BROADCAST_PLAYBACK_PLAY;

/**
 * Includes all of the functions required to show/hide/update the notification
 */

public class PlayerNotification {
    private Class<?> clsActivity;
    private static final int NOTIFY_ME_ID = 696969;
    private Notification.Builder notifyBuilder;
    private NotificationManager notifyManager = null;
    private Service service = null;
    private Context context = null;
    public static RemoteViews remoteViews;

    public PlayerNotification(Class<?> clsActivity, Context context, Service service) {
        this.clsActivity = clsActivity;
        this.service = service;
        this.context = context;
    }

    public void showNotification() {
        remoteViews = new RemoteViews(context.getPackageName(), R.layout.streaming_notification_player);

        String packageName = context.getPackageName();
        Intent openApp = context.getPackageManager().getLaunchIntentForPackage(packageName);
        // Prevent the app from launching a new instance
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        notifyBuilder = new Notification.Builder(this.context)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off) // TODO Use app icon instead
                .setContentText("")
                .setOngoing(true)
                .setContent(remoteViews)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                // Stops the playback when the notification is swiped away
                .setDeleteIntent(makePendingIntent(BROADCAST_EXIT))
                // Make it visible in the lock screen
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(
                        PendingIntent.getActivity(context, 0, openApp, PendingIntent.FLAG_CANCEL_CURRENT)
                );

        Intent resultIntent = new Intent(this.context, this.clsActivity);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this.context);
        stackBuilder.addParentStack(this.clsActivity);
        stackBuilder.addNextIntent(resultIntent);

        remoteViews.setOnClickPendingIntent(R.id.btn_streaming_notification_play, makePendingIntent(BROADCAST_PLAYBACK_PLAY));
        notifyManager = (NotificationManager) this.service.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "com.audioStreaming",
                    "Audio Streaming",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setShowBadge(false);
            channel.setSound(null, null);

            if (notifyManager != null) {
                notifyManager.createNotificationChannel(channel);
            }

            notifyBuilder.setChannelId("com.audioStreaming");
        }

        updateNotification(null, true);
    }

    private void updateNotificationBuilder(String streamTitle, boolean isPlaying) {
        if (notifyBuilder == null) return; // ie. notification hasn't been shown yet

        notifyBuilder.setOngoing(isPlaying);
        remoteViews.setTextViewText(
                R.id.song_name_notification,
                streamTitle == null ? "" : streamTitle
        );
        remoteViews.setImageViewResource(
                R.id.btn_streaming_notification_play,
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play
        );
        notifyBuilder.setContent(remoteViews);
    }

    /**
     * Note, `showNotification` must be called before this function will work.
     */
    public void updateNotification(String streamTitle, boolean isPlaying) {
        if (notifyBuilder == null) return; // ie. notification hasn't been shown yet
        if (notifyManager == null) return; // ie. onCreate hasn't been called yet. I don't think this is possible, but just incase

        updateNotificationBuilder(streamTitle, isPlaying);
        notifyManager.notify(NOTIFY_ME_ID, notifyBuilder.build());
    }

    private PendingIntent makePendingIntent(String broadcast) {
        Intent intent = new Intent(broadcast);
        return PendingIntent.getBroadcast(this.context, 0, intent, 0);
    }

    public void clearNotification() {
        if (notifyManager != null)
            notifyManager.cancel(NOTIFY_ME_ID);
    }

    public void destroy() {
        if (notifyManager != null) {
            notifyManager.cancelAll();
        }
        clearNotification();
        notifyBuilder = null;
        notifyManager = null;
    }

    public void cancelAllNotifications() {
        if (notifyManager != null) {
            notifyManager.cancelAll();
        }
    }
}
