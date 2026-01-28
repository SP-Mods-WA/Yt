package com.spmods.ytpro;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    
    private static final String UPDATE_URL = "https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/update.json";
    private Context context;
    
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
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONObject json = new JSONObject(response.toString());
                UpdateInfo info = new UpdateInfo();
                info.latestVersion = json.getString("version");
                info.versionCode = json.getInt("versionCode");
                info.downloadUrl = json.getString("downloadUrl");
                info.changelog = json.getString("changelog");
                info.forceUpdate = json.getBoolean("forceUpdate");
                
                return info;
                
            } catch (Exception e) {
                Log.e("UpdateChecker", "Error checking update: " + e.getMessage());
                return null;
            }
        }
        
        @Override
        protected void onPostExecute(UpdateInfo updateInfo) {
            if (updateInfo != null) {
                try {
                    int currentVersion = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0)
                        .versionCode;
                    
                    if (updateInfo.versionCode > currentVersion) {
                        showUpdateDialog(updateInfo);
                    }
                    
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void showUpdateDialog(UpdateInfo info) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("📱 Update Available");
        builder.setMessage("New version " + info.latestVersion + " is available!\n\n" +
                          "What's New:\n" + info.changelog);
        
        builder.setPositiveButton("Update Now", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(info.downloadUrl));
                context.startActivity(intent);
            }
        });
        
        if (!info.forceUpdate) {
            builder.setNegativeButton("Later", null);
        } else {
            builder.setCancelable(false);
        }
        
        builder.show();
    }
    
    private static class UpdateInfo {
        String latestVersion;
        int versionCode;
        String downloadUrl;
        String changelog;
        boolean forceUpdate;
    }
}
