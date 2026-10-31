package com.foffano.tclbypass;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Liga o serviço quando a TV termina de iniciar. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(a)) {
            ApplyService.start(context, "boot", 45000);
        }
    }
}
