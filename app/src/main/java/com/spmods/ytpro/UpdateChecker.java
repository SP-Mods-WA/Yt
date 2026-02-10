package com.spmods.ytpro;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    
    private Context context;
    private static final String UPDATE_URL = "https://raw.githubusercontent.com/SP-Mods-WA/SP-Mods-WA/refs/heads/main/ytpro_update.json";
    
    // ⚠️ IMPORTANT: මෙහි minimum version එක hardcode කරන්න
    // JSON එකේ version එක මෙයට වඩා අඩු නම් ignore කරයි
    private static final int MINIMUM_TRUSTED_VERSION = 1; // Version 3.5.0
    
    // ⚠️ OPTIONAL: Maximum version එකක් දාන්න (too high versions block කරන්න)
    private static final int MAXIMUM_TRUSTED_VERSION = 9999; // Any reasonable max
    
    public UpdateChecker(Context context) {
        this.context = context;
    }
    
    public void checkForUpdate() {
        new CheckUpdateTask().execute();
    }
    
    private class CheckUpdateTask extends AsyncTask<Void, Void, UpdateInfo> {
        
        @Override
        protected UpdateInfo doInBackground(Void... voids) {
            try {
                URL url = new URL(UPDATE_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
                );
                
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONObject json = new JSONObject(response.toString());
                
                UpdateInfo info = new UpdateInfo();
                info.latestVersion = json.getString("latestVersion");
                info.latestVersionCode = json.getInt("latestVersionCode");
                info.updateTitle = json.optString("updateTitle", "Update Available!");
                info.updateMessage = json.optString("updateMessage", "New features and improvements are ready to download.");
                info.downloadUrl = json.getString("downloadUrl");
                info.forceUpdate = json.optBoolean("forceUpdate", false);
                info.updateSize = json.optString("updateSize", "5MB");
                
                // ✅ SECURITY CHECK: Verify version is within trusted range
                if (info.latestVersionCode < MINIMUM_TRUSTED_VERSION) {
                    Log.w("UpdateChecker", "Version too old - possibly tampered JSON. Ignoring.");
                    return null;
                }
                
                if (info.latestVersionCode > MAXIMUM_TRUSTED_VERSION) {
                    Log.w("UpdateChecker", "Version too high - possibly tampered JSON. Ignoring.");
                    return null;
                }
                
                return info;
                
            } catch (Exception e) {
                Log.e("UpdateChecker", "Error checking update: " + e.getMessage());
                return null;
            }
        }
        
        @Override
        protected void onPostExecute(UpdateInfo updateInfo) {
            if (updateInfo != null) {
                int currentVersion = getCurrentVersionCode();
                
                // Show update only if:
                // 1. JSON version is higher than current version
                // 2. JSON version passed security checks (done in doInBackground)
                if (updateInfo.latestVersionCode > currentVersion) {
                    showUpdateDialog(updateInfo);
                } else {
                    Log.d("UpdateChecker", "App is up to date. Current: " + currentVersion + ", Latest: " + updateInfo.latestVersionCode);
                }
            }
        }
    }
    
    private int getCurrentVersionCode() {
        try {
            PackageInfo pInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                return (int) pInfo.getLongVersionCode();
            } else {
                return pInfo.versionCode;
            }
        } catch (Exception e) {
            return 0;
        }
    }
    
    private void showUpdateDialog(final UpdateInfo info) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_update_compact);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setCancelable(!info.forceUpdate);
        
        // Window setup
        Window window = dialog.getWindow();
        window.setGravity(Gravity.CENTER);
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.88);
        window.setAttributes(layoutParams);
        window.getAttributes().windowAnimations = R.style.DialogAnimation;
        
        // Find views
        ImageView iconUpdate = dialog.findViewById(R.id.iconUpdate);
        TextView tvTitle = dialog.findViewById(R.id.tvUpdateTitle);
        TextView tvVersion = dialog.findViewById(R.id.tvUpdateVersion);
        TextView tvMessage = dialog.findViewById(R.id.tvUpdateMessage);
        TextView tvSize = dialog.findViewById(R.id.tvSize);
        TextView tvFeatures = dialog.findViewById(R.id.tvFeatures);
        LinearLayout featuresContainer = dialog.findViewById(R.id.featuresContainer);
        Button btnUpdate = dialog.findViewById(R.id.btnUpdate);
        Button btnLater = dialog.findViewById(R.id.btnLater);
        
        // Set data
        tvTitle.setText(info.updateTitle);
        tvVersion.setText("V" + info.latestVersion + " READY");
        tvMessage.setText(info.updateMessage);
        tvSize.setText(info.updateSize);
        
        // Start animations
        Animation iconPulse = AnimationUtils.loadAnimation(context, R.anim.pulse_animation);
        iconUpdate.startAnimation(iconPulse);
        
        Animation slideIn = AnimationUtils.loadAnimation(context, R.anim.slide_in_left);
        featuresContainer.startAnimation(slideIn);
        
        // Button visibility
        if (!info.forceUpdate) {
            btnLater.setVisibility(View.VISIBLE);
        } else {
            btnLater.setVisibility(View.GONE);
        }
        
        // Button clicks
        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl));
                context.startActivity(intent);
                dialog.dismiss();
            }
        });
        
        btnLater.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        
        dialog.show();
    }
    
    private static class UpdateInfo {
        String latestVersion;
        int latestVersionCode;
        String updateTitle;
        String updateMessage;
        String downloadUrl;
        String updateSize;
        boolean forceUpdate;
    }
}
