package com.foffano.tclbypass;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/** Roda o Applier fora da tela, em primeiro plano (exigido para rodar logo após o boot). */
public class ApplyService extends Service {
    private static final String CHANNEL = "tclbypass";
    private static volatile boolean running;

    public static void start(Context c, String reason, long delayMs) {
        Intent i = new Intent(c, ApplyService.class).putExtra("reason", reason).putExtra("delay", delayMs);
        c.startForegroundService(i);
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "TCL Bypass", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("TCL Bypass")
                .setContentText("Desativando HDR...")
                .build();
        startForeground(1, n);

        if (running) return START_NOT_STICKY;
        running = true;
        final String reason = intent == null ? "?" : intent.getStringExtra("reason");
        final long delay = intent == null ? 0 : intent.getLongExtra("delay", 0);
        final Context self = this;
        new Thread(new Runnable() { @Override public void run() {
            try {
                if (delay > 0) Thread.sleep(delay); // deixa a tela inicial terminar de carregar
                new Applier(self).run(reason);
            } catch (InterruptedException ignored) {
            } finally {
                running = false;
                stopForeground(true);
                stopSelf();
            }
        }}).start();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
