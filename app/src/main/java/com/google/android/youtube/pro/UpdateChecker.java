package com.google.android.youtube.pro;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    
    private Context context;
    private static final String UPDATE_URL = "https://raw.githubusercontent.com/SP-Mods-WA/SP-Mods-WA/refs/heads/main/ytup_info.json";
    
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
                info.updateTitle = json.getString("updateTitle");
                info.updateMessage = json.getString("updateMessage");
                info.downloadUrl = json.getString("downloadUrl");
                info.forceUpdate = json.optBoolean("forceUpdate", false);
                
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
                if (updateInfo.latestVersionCode > currentVersion) {
                    showUpdateDialog(updateInfo);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(info.updateTitle);
        builder.setMessage(info.updateMessage);
        builder.setCancelable(!info.forceUpdate);
        
        builder.setPositiveButton("Update", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl));
            context.startActivity(intent);
        });
        
        if (!info.forceUpdate) {
            builder.setNegativeButton("Later", (dialog, which) -> dialog.dismiss());
        }
        
        builder.create().show();
    }
    
    private static class UpdateInfo {
        String latestVersion;
        int latestVersionCode;
        String updateTitle;
        String updateMessage;
        String downloadUrl;
        boolean forceUpdate;
    }
}




