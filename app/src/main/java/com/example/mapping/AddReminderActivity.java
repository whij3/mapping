package com.example.mapping;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AddReminderActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_GALLERY = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 101;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private EditText titleEditText;
    private EditText descriptionEditText;
    private ImageView reminderImageView;
    private Bitmap reminderImage;

    private boolean isEditMode = false;
    private String reminderId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_reminder);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        titleEditText = findViewById(R.id.titleEditText);
        descriptionEditText = findViewById(R.id.descriptionEditText);
        Button addPhotoButton = findViewById(R.id.addPhotoButton);
        Button saveReminderButton = findViewById(R.id.saveReminderButton);
        reminderImageView = findViewById(R.id.reminderImageView);

        addPhotoButton.setOnClickListener(v -> {
            // Check camera permission first
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION);
            } else {
                showImagePickerOptions();
            }
        });

        // In onCreate():
        if (getIntent().hasExtra("EDIT_MODE") && getIntent().hasExtra("reminderId")) {
            // Load existing reminder data
            String reminderId = getIntent().getStringExtra("reminderId");
            loadReminderData(reminderId);
        }

        // Check if we're in edit mode
        if (getIntent().getBooleanExtra("EDIT_MODE", false)) {
            isEditMode = true;
            reminderId = getIntent().getStringExtra("reminderId");
            titleEditText.setText(getIntent().getStringExtra("title"));
            descriptionEditText.setText(getIntent().getStringExtra("description"));

            String photoBase64 = getIntent().getStringExtra("photo");
            if (photoBase64 != null && !photoBase64.isEmpty()) {
                reminderImage = ImageUtils.base64ToBitmap(photoBase64);
                reminderImageView.setImageBitmap(reminderImage);
            }
        }

        saveReminderButton.setOnClickListener(v -> saveReminder());
    }

    private void showImagePickerOptions() {
        CharSequence[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Photo");
        builder.setItems(options, (dialog, which) -> {
            if (options[which].equals("Take Photo")) {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                } else {
                    Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
                }
            } else if (options[which].equals("Choose from Gallery")) {
                Intent pickPhoto = new Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(pickPhoto, REQUEST_IMAGE_GALLERY);
            }
        });
        builder.show();
    }

    private void saveReminder() {
        String title = titleEditText.getText().toString().trim();
        String description = descriptionEditText.getText().toString().trim();
        double latitude = getIntent().getDoubleExtra("latitude", 0);
        double longitude = getIntent().getDoubleExtra("longitude", 0);
        String userId = mAuth.getCurrentUser().getUid();

        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }

        if (description.isEmpty()) {
            Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show();
            return;
        }

        if (reminderImage == null) {
            Toast.makeText(this, "Please add a photo", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> reminder = new HashMap<>();
        reminder.put("title", title);
        reminder.put("description", description);
        reminder.put("latitude", latitude);
        reminder.put("longitude", longitude);
        reminder.put("photo", ImageUtils.bitmapToBase64(reminderImage));
        reminder.put("timestamp", System.currentTimeMillis());
        reminder.put("userId", userId);
        reminder.put("creatorId", userId);
        reminder.put("triggerDistance", 100);

        if (isEditMode) {
            // Update existing reminder
            db.collection("reminders").document(reminderId)
                    .update(reminder)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Reminder updated!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update reminder: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        } else {
            // Create new reminder
            String newReminderId = userId + "_" + latitude + "_" + longitude + "_" + System.currentTimeMillis();
            reminder.put("sharedWith", new ArrayList<String>()); // Initialize empty shared list

            db.collection("reminders").document(newReminderId)
                    .set(reminder)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Reminder saved!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to save reminder: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                Bundle extras = data.getExtras();
                if (extras != null) {
                    reminderImage = (Bitmap) extras.get("data");
                    reminderImageView.setImageBitmap(reminderImage);
                }
            } else if (requestCode == REQUEST_IMAGE_GALLERY) {
                try {
                    Uri imageUri = data.getData();
                    InputStream imageStream = getContentResolver().openInputStream(imageUri);
                    reminderImage = BitmapFactory.decodeStream(imageStream);
                    reminderImageView.setImageBitmap(reminderImage);
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showImagePickerOptions();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadReminderData(String reminderId) {
        db.collection("reminders").document(reminderId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Get the reminder data from Firestore
                        String title = documentSnapshot.getString("title");
                        String description = documentSnapshot.getString("description");
                        String photo = documentSnapshot.getString("photo");
                        double latitude = documentSnapshot.getDouble("latitude");
                        double longitude = documentSnapshot.getDouble("longitude");

                        // Update the UI with the existing reminder data
                        titleEditText.setText(title);
                        descriptionEditText.setText(description);

                        // Load the photo if it exists
                        if (photo != null && !photo.isEmpty()) {
                            reminderImage = ImageUtils.base64ToBitmap(photo);
                            reminderImageView.setImageBitmap(reminderImage);
                        }

                        // Update the intent with the location data
                        getIntent().putExtra("latitude", latitude);
                        getIntent().putExtra("longitude", longitude);
                    } else {
                        Toast.makeText(this, "Reminder not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load reminder", Toast.LENGTH_SHORT).show();
                    Log.e("AddReminderActivity", "Error loading reminder", e);
                    finish();
                });
    }
}