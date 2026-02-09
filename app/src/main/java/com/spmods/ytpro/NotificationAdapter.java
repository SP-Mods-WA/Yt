package com.spmods.ytpro;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
    
    private Context context;
    private List<NotificationModel> notifications = new ArrayList<>();
    private NotificationPreferences prefs;
    
    public NotificationAdapter(Context context, NotificationPreferences prefs) {
        this.context = context;
        this.prefs = prefs;
    }
    
    public void setNotifications(List<NotificationModel> notifications) {
        this.notifications = notifications;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationModel notification = notifications.get(position);
        
        holder.titleText.setText(notification.getTitle());
        holder.messageText.setText(notification.getMessage());
        
        // Time ago text
        CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
            notification.getTimestamp(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        );
        holder.timeText.setText(timeAgo);
        
        // Priority badge
        setPriorityBadge(holder, notification.getPriority());
        
        // Type icon
        setTypeIcon(holder, notification.getType());
        
        // Click action
        if (notification.getActionUrl() != null && !notification.getActionUrl().isEmpty()) {
            holder.cardView.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(notification.getActionUrl()));
                    context.startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }
    
    private void setPriorityBadge(ViewHolder holder, String priority) {
        if (priority == null) {
            holder.priorityBadge.setVisibility(View.GONE);
            return;
        }
        
        switch (priority.toLowerCase()) {
            case "high":
                holder.priorityBadge.setVisibility(View.VISIBLE);
                holder.priorityBadge.setText("Important");
                holder.priorityBadge.setBackgroundColor(Color.parseColor("#FF0000"));
                holder.priorityBadge.setTextColor(Color.WHITE);
                break;
            case "medium":
                holder.priorityBadge.setVisibility(View.VISIBLE);
                holder.priorityBadge.setText("New");
                holder.priorityBadge.setBackgroundColor(Color.parseColor("#FFA500"));
                holder.priorityBadge.setTextColor(Color.WHITE);
                break;
            default:
                holder.priorityBadge.setVisibility(View.GONE);
                break;
        }
    }
    
    private void setTypeIcon(ViewHolder holder, String type) {
        if (type == null) {
            holder.typeIcon.setVisibility(View.GONE);
            return;
        }
        
        holder.typeIcon.setVisibility(View.VISIBLE);
        switch (type.toLowerCase()) {
            case "update":
                holder.typeIcon.setText("🔄");
                break;
            case "warning":
                holder.typeIcon.setText("⚠️");
                break;
            case "feature":
                holder.typeIcon.setText("✨");
                break;
            case "announcement":
                holder.typeIcon.setText("📢");
                break;
            default:
                holder.typeIcon.setText("ℹ️");
                break;
        }
    }
    
    @Override
    public int getItemCount() {
        return notifications.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView typeIcon;
        TextView titleText;
        TextView messageText;
        TextView timeText;
        TextView priorityBadge;
        
        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            typeIcon = itemView.findViewById(R.id.typeIcon);
            titleText = itemView.findViewById(R.id.titleText);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            priorityBadge = itemView.findViewById(R.id.priorityBadge);
        }
    }
}
