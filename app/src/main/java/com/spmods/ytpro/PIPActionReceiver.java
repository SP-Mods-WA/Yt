package com.spmods.ytpro;

import android.content.*;
import android.util.Log;
import android.widget.Toast;

public class PIPActionReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d("PIPActionReceiver", "Action received: " + action);
        
        // Send broadcast to main activity
        Intent broadcastIntent = new Intent("PIP_ACTION");
        broadcastIntent.putExtra("ACTION", action);
        context.sendBroadcast(broadcastIntent);
        
        // Show toast for debugging
        Toast.makeText(context, "PIP Action: " + action, Toast.LENGTH_SHORT).show();
    }
}
