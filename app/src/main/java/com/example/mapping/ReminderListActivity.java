package com.example.mapping;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ReminderListActivity extends AppCompatActivity {

    private RecyclerView remindersRecyclerView;
    private ReminderAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private TextView emptyStateText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder_list);

        remindersRecyclerView = findViewById(R.id.remindersRecyclerView);
        emptyStateText = findViewById(R.id.emptyStateText);
        remindersRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        loadReminders();
    }

    private void loadReminders() {
        String currentUserId = mAuth.getCurrentUser().getUid();

        // Get reminders created by current user
        db.collection("reminders")
                .whereEqualTo("creatorId", currentUserId)
                .get()
                .addOnCompleteListener(ownedTask -> {
                    if (ownedTask.isSuccessful()) {
                        List<StoredLocation> reminders = new ArrayList<>();

                        // Add owned reminders
                        for (QueryDocumentSnapshot document : ownedTask.getResult()) {
                            StoredLocation reminder = document.toObject(StoredLocation.class);
                            reminder.setId(document.getId());  // Use setter instead of direct access
                            reminder.isOwned = true;
                            reminders.add(reminder);
                        }

                        // Get reminders shared with current user
                        db.collection("reminders")
                                .whereArrayContains("sharedWith", currentUserId)
                                .get()
                                .addOnCompleteListener(sharedTask -> {
                                    if (sharedTask.isSuccessful()) {
                                        // Add shared reminders
                                        for (QueryDocumentSnapshot document : sharedTask.getResult()) {
                                            StoredLocation reminder = document.toObject(StoredLocation.class);
                                            reminder.setId(document.getId());  // Use setter instead of direct access
                                            reminder.isOwned = false;
                                            reminders.add(reminder);
                                        }

                                        updateUI(reminders);
                                    } else {
                                        Log.w("ReminderList", "Error getting shared reminders", sharedTask.getException());
                                        updateUI(reminders); // Still show owned reminders
                                    }
                                });
                    } else {
                        Log.w("ReminderList", "Error getting owned reminders", ownedTask.getException());
                        Toast.makeText(this, "Failed to load reminders", Toast.LENGTH_SHORT).show();
                        updateUI(new ArrayList<>());
                    }
                });
    }

    private void updateUI(List<StoredLocation> reminders) {
        if (reminders.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            remindersRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateText.setVisibility(View.GONE);
            remindersRecyclerView.setVisibility(View.VISIBLE);
            adapter = new ReminderAdapter(reminders, mAuth.getCurrentUser().getUid());
            remindersRecyclerView.setAdapter(adapter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReminders(); // Refresh when returning from other activities
    }

    public void confirmDeleteReminder(StoredLocation reminder) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Reminder")
                .setMessage("Are you sure you want to delete this reminder?")
                .setPositiveButton("Delete", (dialog, which) -> deleteReminder(reminder))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteReminder(StoredLocation reminder) {
        db.collection("reminders").document(reminder.getId())  // Use getId() instead of reminder.id
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Reminder deleted", Toast.LENGTH_SHORT).show();
                    loadReminders(); // Refresh the list
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete reminder", Toast.LENGTH_SHORT).show();
                    Log.e("ReminderList", "Error deleting reminder", e);
                });
    }
}