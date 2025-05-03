package com.example.mapping;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ReminderDetailActivity extends AppCompatActivity {

    private TextView titleTextView;
    private TextView descriptionTextView;
    private TextView locationTextView;
    private TextView creatorTextView;
    private ImageView reminderImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder_detail);

        // Initialize views
        titleTextView = findViewById(R.id.reminderTitle);
        descriptionTextView = findViewById(R.id.reminderDescription);
        locationTextView = findViewById(R.id.reminderLocation);
        creatorTextView = findViewById(R.id.reminderCreator);
        reminderImageView = findViewById(R.id.reminderImage);

        // Get reminder ID from intent
        String reminderId = getIntent().getStringExtra("reminderId");
        if (reminderId != null) {
            loadReminderDetails(reminderId);
        }
    }

    private void loadReminderDetails(String reminderId) {
        FirebaseFirestore.getInstance()
                .collection("reminders")
                .document(reminderId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        StoredLocation reminder = documentSnapshot.toObject(StoredLocation.class);
                        if (reminder != null) {
                            displayReminder(reminder);
                        }
                    }
                });
    }

    private void displayReminder(StoredLocation reminder) {
        titleTextView.setText(reminder.title);
        descriptionTextView.setText(reminder.description);
        locationTextView.setText(String.format("Location: %.6f, %.6f",
                reminder.latitude, reminder.longitude));

        if (reminder.isCreatedBy(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
            creatorTextView.setText("Created by you");
        } else {
            creatorTextView.setText(String.format("Created by: %s", reminder.creatorId));
        }

        if (reminder.photo != null && !reminder.photo.isEmpty()) {
            // Using Glide for better image loading
            Glide.with(this)
                    .load(reminder.photo)
                    .placeholder(R.drawable.ic_default_image)
                    .into(reminderImageView);
        }
    }
}