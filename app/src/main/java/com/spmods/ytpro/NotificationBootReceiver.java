package com.spmods.ytpro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class NotificationBootReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed - starting notification service");
            
            Intent serviceIntent = new Intent(context, NotificationCheckService.class);
            context.startService(serviceIntent);
        }
    }
}
