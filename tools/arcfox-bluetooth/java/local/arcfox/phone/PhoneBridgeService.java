package local.arcfox.phone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** Dudu-hosted owner of the DiPlay HFP lease; independent of launcher open/close actions. */
public final class PhoneBridgeService extends Service {
    private static final String ACTION = "arcfox.phone.lease.v1";
    private static final String CHANNEL = "arcfox_phone_bridge";
    private static final int NOTIFICATION = 416;
    private PhoneLeaseBinder lease;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "CarPlay phone connection", NotificationManager.IMPORTANCE_MIN));
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle("CarPlay phone connection")
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION, notification);
        lease = new PhoneLeaseBinder(this);
        lease.start();
        // A bind-only service would be destroyed when DiPlay is force-stopped.
        // Retain a started lifetime so Binder death can restore HFP.
        startService(new Intent(this, PhoneBridgeService.class));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) {
        return intent != null && ACTION.equals(intent.getAction()) ? lease : null;
    }

    @Override public void onDestroy() {
        if (lease != null) lease.stop();
        super.onDestroy();
    }
}
