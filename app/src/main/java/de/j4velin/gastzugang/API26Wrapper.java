package de.j4velin.gastzugang;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.O)
public class API26Wrapper {

    private final static String NOTIFICATION_CHANNEL_ID = "notifications";

    public static void createNotificationChannel(final Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(NOTIFICATION_CHANNEL_ID, "notification",
                            NotificationManager.IMPORTANCE_LOW);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = c.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static void setChannelId(Notification.Builder b) {
        b.setChannelId(NOTIFICATION_CHANNEL_ID);
    }
}
