package com.spmods.ytpro;

public class NotificationModel {
    private String id;
    private String title;
    private String message;
    private String type;
    private String priority;
    private String iconUrl;
    private String actionUrl;
    private long timestamp;
    private long showUntil;
    private boolean isDismissible;
    private boolean isViewed;

    public NotificationModel() {
    }

    public NotificationModel(String id, String title, String message, String type, 
                           String priority, String iconUrl, String actionUrl, 
                           long timestamp, long showUntil, boolean isDismissible) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.type = type;
        this.priority = priority;
        this.iconUrl = iconUrl;
        this.actionUrl = actionUrl;
        this.timestamp = timestamp;
        this.showUntil = showUntil;
        this.isDismissible = isDismissible;
        this.isViewed = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getShowUntil() {
        return showUntil;
    }

    public void setShowUntil(long showUntil) {
        this.showUntil = showUntil;
    }

    public boolean isDismissible() {
        return isDismissible;
    }

    public void setDismissible(boolean dismissible) {
        isDismissible = dismissible;
    }

    public boolean isViewed() {
        return isViewed;
    }

    public void setViewed(boolean viewed) {
        isViewed = viewed;
    }

    public boolean isValid() {
        long currentTime = System.currentTimeMillis();
        return currentTime <= showUntil;
    }

    public int getNotificationImportance() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            switch (priority.toLowerCase()) {
                case "high":
                    return android.app.NotificationManager.IMPORTANCE_HIGH;
                case "medium":
                    return android.app.NotificationManager.IMPORTANCE_DEFAULT;
                case "low":
                    return android.app.NotificationManager.IMPORTANCE_LOW;
                default:
                    return android.app.NotificationManager.IMPORTANCE_DEFAULT;
            }
        }
        return 0;
    }
}
