package com.example.mapping;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ReminderAdapter extends RecyclerView.Adapter<ReminderAdapter.ViewHolder> {

    private List<StoredLocation> reminders;
    private String currentUserId;
    private Context context;

    public ReminderAdapter(List<StoredLocation> reminders, String currentUserId) {
        this.reminders = reminders;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_reminder, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StoredLocation reminder = reminders.get(position);

        holder.titleTextView.setText(reminder.title);
        holder.descriptionTextView.setText(reminder.description);

        // Set ownership indicator
        if (reminder.isCreatedBy(currentUserId)) {
            holder.ownerTextView.setText("Your reminder");
            holder.ownerTextView.setVisibility(View.VISIBLE);
        } else if (reminder.isShared()) {
            holder.ownerTextView.setText("Shared by: " + reminder.creatorId);
            holder.ownerTextView.setVisibility(View.VISIBLE);
        } else {
            holder.ownerTextView.setVisibility(View.GONE);
        }

        // Load image if available
        if (reminder.photo != null && !reminder.photo.isEmpty()) {
            Bitmap bitmap = ImageUtils.base64ToBitmap(reminder.photo);
            holder.reminderImageView.setImageBitmap(bitmap);
        } else {
            holder.reminderImageView.setImageResource(R.drawable.ic_default_image);
        }

        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ReminderDetailActivity.class);
            intent.putExtra("reminderId", reminder.getDocumentId()); // Use getter method
            context.startActivity(intent);
        });

        // Only show edit/delete for owned reminders
        if (reminder.isCreatedBy(currentUserId)) {
            holder.editButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setVisibility(View.VISIBLE);

            holder.editButton.setOnClickListener(v -> {
                Intent intent = new Intent(context, AddReminderActivity.class); // Changed from AddEditReminderActivity
                intent.putExtra("reminderId", reminder.getDocumentId());
                intent.putExtra("EDIT_MODE", true);
                context.startActivity(intent);
            });

            holder.deleteButton.setOnClickListener(v -> {
                ((ReminderListActivity)context).confirmDeleteReminder(reminder);
            });
        } else {
            holder.editButton.setVisibility(View.GONE);
            holder.deleteButton.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return reminders.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView descriptionTextView;
        TextView ownerTextView;
        ImageView reminderImageView;
        Button editButton;
        Button deleteButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.reminderTitle);
            descriptionTextView = itemView.findViewById(R.id.reminderDescription);
            ownerTextView = itemView.findViewById(R.id.reminderOwner);
            reminderImageView = itemView.findViewById(R.id.reminderImage);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}