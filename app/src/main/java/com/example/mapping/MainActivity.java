package com.example.mapping;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.EventListener;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements MapEventsReceiver, SensorEventListener {

    private Button logoutButton;

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int DEFAULT_TRIGGER_DISTANCE = 100; // meters

    private static final int REMINDER_TRIGGER_DISTANCE = 150; // meters

    private Set<String> notifiedReminders = new HashSet<>();
    private Map<String, StoredLocation> storedLocations = new HashMap<>();
    private List<StoredLocation> reminders = new ArrayList<>();
    private MapView mapView;
    private ActivityResultLauncher<String[]> locationPermissionRequest;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private final long minimumTimeBetweenUpdates = 10000; // 10 seconds
    private final float minimumDistanceBetweenUpdates = 0.5f; // 0.5 meters
    private NotificationManagerCompat notificationManager;
    private FirebaseFirestore db;
    private ListenerRegistration locationsListener;
    private ListenerRegistration remindersListener;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> addReminderLauncher;
    private ImageView previewImageView;
    private Bitmap capturedImage;
    static final String NOTIFICATION_KEY = "MyLocation";
    static final int NOTIFICATION_INTENT_CODE = 0;

    private Set<String> shownReminderDialogs = new HashSet<>();

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        logoutButton = findViewById(R.id.logoutButton);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();



        // Initialize UI components
        initializeUI();
        initializeMap();
        initializeLocationTracking();
        initializeFirestoreListeners();
        initializeActivityLaunchers();


        initializeLogoutButton();

        // Clear all notifications when app opens
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }

        initializeLogoutButton(); // Call the method here

    }

    private void initializeLogoutButton() {
        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            // Sign out from Firebase
            mAuth.signOut();

            // Sign out from Google
            GoogleSignIn.getClient(this,
                            new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build())
                    .signOut()
                    .addOnCompleteListener(task -> {
                        // Redirect to LoginActivity
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                    });
        });
    }

    private void initializeUI() {
        Button viewRemindersButton = findViewById(R.id.viewRemindersButton);
        viewRemindersButton.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ReminderListActivity.class));
        });

        notificationManager = NotificationManagerCompat.from(this);
        createNotificationChannel();
    }

    private void initializeMap() {
        mapView = findViewById(R.id.map);
        if (mapView != null) {
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setMultiTouchControls(true);
            mapView.getOverlays().add(0, new MapEventsOverlay(this));

            IMapController mapController = mapView.getController();
            mapController.setZoom(6.0);
            mapController.setCenter(new GeoPoint(52.8583, -2.2944));
        }
    }

    private void initializeLocationTracking() {
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (mapView == null) return;

                GeoPoint currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                updateUserLocationOnMap(currentLocation);
                checkProximityToLocations(currentLocation);
                checkProximityToReminders(currentLocation);
            }
        };

        Switch trackingSwitch = findViewById(R.id.trackingSwitch);
        if (trackingSwitch != null) {
            trackingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    requestLocationPermissions();
                } else if (locationManager != null) {
                    locationManager.removeUpdates(locationListener);
                }
            });
        }
    }

    private void initializeFirestoreListeners() {
        currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Listen for reminder changes (both owned and shared)
        remindersListener = db.collection("reminders")
                .whereArrayContains("sharedWith", currentUser.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("MainActivity", "Reminders listener error", error);
                        return;
                    }

                    if (value != null) {
                        List<StoredLocation> allReminders = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) {
                            StoredLocation reminder = doc.toObject(StoredLocation.class);
                            reminder.setId(doc.getId());
                            allReminders.add(reminder);
                        }

                        // Also get reminders where creatorId matches current user
                        db.collection("reminders")
                                .whereEqualTo("creatorId", currentUser.getUid())
                                .get()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        for (QueryDocumentSnapshot doc : task.getResult()) {
                                            StoredLocation reminder = doc.toObject(StoredLocation.class);
                                            reminder.setId(doc.getId());
                                            if (!allReminders.contains(reminder)) {
                                                allReminders.add(reminder);
                                            }
                                        }
                                        reminders = allReminders;
                                        updateMapWithReminders();
                                    }
                                });
                    }
                });
    }


    private void addLogoutButton() {
        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            mAuth.signOut();
            GoogleSignIn.getClient(this,
                            new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build())
                    .signOut()
                    .addOnCompleteListener(task -> {
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                    });
        });
    }

    private void initializeActivityLaunchers() {
        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))) {
                        updateLocation();
                    }
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        if (extras != null) {
                            capturedImage = (Bitmap) extras.get("data");
                            if (previewImageView != null) {
                                previewImageView.setImageBitmap(capturedImage);
                                previewImageView.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                });

        addReminderLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, "Reminder added!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleLocationsUpdate(QuerySnapshot value, FirebaseFirestoreException error) {
        if (error != null) {
            Log.e("MainActivity", "Locations listener error", error);
            return;
        }

        if (value != null && mapView != null) {
            storedLocations.clear();
            for (QueryDocumentSnapshot doc : value) {
                StoredLocation location = doc.toObject(StoredLocation.class);
                storedLocations.put(location.locationName, location);
            }
            updateMapWithLocations();
        }
    }

    private void handleRemindersUpdate(QuerySnapshot value, FirebaseFirestoreException error) {
        if (error != null) {
            Log.e("MainActivity", "Reminders listener error", error);
            return;
        }

        if (value != null) {
            reminders = value.toObjects(StoredLocation.class);
            updateMapWithReminders();
        }
    }

    private void updateMapWithLocations() {
        if (mapView == null) return;

        // Add new location markers
        for (StoredLocation location : storedLocations.values()) {
            GeoPoint point = new GeoPoint(location.latitude, location.longitude);
            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setTitle(location.locationName);
            marker.setIcon(getResources().getDrawable(R.drawable.marker_default_foreground));
            mapView.getOverlays().add(marker);
        }
        mapView.invalidate();
    }

    private void updateMapWithReminders() {
        if (mapView == null) return;

        // Clear existing reminder markers
        mapView.getOverlays().removeIf(overlay -> {
            if (overlay instanceof Marker) {
                Marker marker = (Marker) overlay;
                return marker.getTitle() != null && marker.getTitle().startsWith("REMINDER:");
            }
            return false;
        });

        // Add all reminder markers
        for (StoredLocation reminder : reminders) {
            GeoPoint point = new GeoPoint(reminder.latitude, reminder.longitude);
            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setTitle("REMINDER:" + reminder.title);
            marker.setSnippet(reminder.description);

            // Set default red icon
            marker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_reminder_marker));
            marker.getIcon().setTint(Color.RED);

            mapView.getOverlays().add(marker);
        }
        mapView.invalidate();
    }
    private void updateUserLocationOnMap(GeoPoint currentLocation) {
        if (mapView == null) return;

        // Clear existing user location marker
        mapView.getOverlays().removeIf(overlay -> {
            if (overlay instanceof Marker) {
                Marker marker = (Marker) overlay;
                return marker.getTitle() != null && marker.getTitle().equals("CURRENT_LOCATION");
            }
            return false;
        });

        // Add new user location marker
        Marker currentLocationMarker = new Marker(mapView);
        currentLocationMarker.setPosition(currentLocation);
        currentLocationMarker.setTitle("CURRENT_LOCATION");
        currentLocationMarker.setIcon(getResources().getDrawable(R.drawable.ic_current_location));
        mapView.getOverlays().add(currentLocationMarker);
        mapView.getController().animateTo(currentLocation);
        mapView.invalidate();
    }

    private void checkProximityToLocations(GeoPoint currentLocation) {
        for (StoredLocation location : storedLocations.values()) {
            GeoPoint locationPoint = new GeoPoint(location.latitude, location.longitude);
            double distance = currentLocation.distanceToAsDouble(locationPoint);

            if (distance < DEFAULT_TRIGGER_DISTANCE) {
                showLocationNotification(location, distance);
            }
        }
    }


    //checks the proximity to a reminder

    private void checkProximityToReminders(GeoPoint currentLocation) {
        for (StoredLocation reminder : reminders) {
            GeoPoint reminderPoint = new GeoPoint(reminder.latitude, reminder.longitude);
            double distance = currentLocation.distanceToAsDouble(reminderPoint);
            int triggerDistance = reminder.triggerDistance > 0 ? reminder.triggerDistance : REMINDER_TRIGGER_DISTANCE;

            for (Overlay overlay : mapView.getOverlays()) {
                if (overlay instanceof Marker) {
                    Marker marker = (Marker) overlay;
                    if (marker.getTitle() != null &&
                            marker.getTitle().equals("REMINDER:" + reminder.title)) {

                        if (distance < triggerDistance) {
                            // Change to light blue when in range
                            marker.getIcon().setTint(Color.parseColor("#ADD8E6"));

                            if (!notifiedReminders.contains(reminder.getId())) {
                                showReminderNotification(reminder, distance);
                                showReminderDialog(reminder, distance);
                                notifiedReminders.add(reminder.getId());
                            }
                        } else {
                            // Change back to red when out of range
                            marker.getIcon().setTint(Color.RED);
                            notifiedReminders.remove(reminder.getId());
                        }
                        break;
                    }
                }
            }
        }
    }

    private void showReminderDialog(StoredLocation reminder, double distance) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("⏰ Reminder: " + reminder.title);

            // Custom dialog layout with image
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_reminder_alert, null);
            TextView distanceText = dialogView.findViewById(R.id.distanceText);
            TextView descriptionText = dialogView.findViewById(R.id.descriptionText);
            ImageView reminderImage = dialogView.findViewById(R.id.reminderImage);

            distanceText.setText("You're " + (int)distance + "m away");
            descriptionText.setText(reminder.description);

            // Load image if available
            if (reminder.photo != null && !reminder.photo.isEmpty()) {
                try {
                    Bitmap bitmap = ImageUtils.base64ToBitmap(reminder.photo);
                    reminderImage.setImageBitmap(bitmap);
                } catch (Exception e) {
                    reminderImage.setVisibility(View.GONE);
                }
            } else {
                reminderImage.setVisibility(View.GONE);
            }

            builder.setView(dialogView)
                    .setPositiveButton("OK", null)
                    .setOnDismissListener(dialog -> {
                        // Optional: Add any cleanup here
                    });

            // Prevent dismissing when touching outside
            AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        });
    }

    private void updateLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions();
            return;
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            try {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        minimumTimeBetweenUpdates,
                        minimumDistanceBetweenUpdates,
                        locationListener
                );

                Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocation != null) {
                    locationListener.onLocationChanged(lastKnownLocation);
                }
            } catch (SecurityException e) {
                Log.e("MainActivity", "Location permission not granted", e);
            }
        }
    }

    private void showLocationNotification(StoredLocation location, double distance) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_KEY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Near " + location.locationName)
                .setContentText("You are " + distance + " meters away")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();

        notificationManager.notify(location.locationName.hashCode(), notification);
    }

    private void showReminderNotification(StoredLocation reminder, double distance) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Create an intent to open when notification is tapped
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("zoom_to_reminder", reminder.getId());
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_KEY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Reminder: " + reminder.title)
                .setContentText("You're " + (int) distance + "m away - " + reminder.description)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Add image if available
        if (reminder.photo != null && !reminder.photo.isEmpty()) {
            try {
                Bitmap bitmap = ImageUtils.base64ToBitmap(reminder.photo);
                builder.setStyle(new NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setBigContentTitle("Reminder: " + reminder.title)
                        .setSummaryText(reminder.description));
            } catch (Exception e) {
                Log.e("Notification", "Error loading reminder image", e);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_KEY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Reminder: " + reminder.locationName)
                .setContentText("You're " + distance + "m away - " + reminder.description)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        notificationManager.notify(("REMINDER_" + reminder.locationName).hashCode(), notification);
    }

    private void requestLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            });
        } else {
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_KEY,
                    "Reminder Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for reminder proximity alerts");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public boolean longPressHelper(GeoPoint p) {
        showLocationReminderChoiceDialog(p);
        return true;
    }

    private void showLocationReminderChoiceDialog(GeoPoint point) {
        new AlertDialog.Builder(this)
                .setTitle("Create New")
                .setItems(new String[]{"Location", "Reminder"}, (dialog, which) -> {
                    if (which == 0) {
                        showAddLocationDialog(point);
                    } else {
                        launchAddReminderActivity(point);
                    }
                })
                .show();
    }

    private void launchAddReminderActivity(GeoPoint point) {
        Intent intent = new Intent(this, AddReminderActivity.class);
        intent.putExtra("latitude", point.getLatitude());
        intent.putExtra("longitude", point.getLongitude());
        addReminderLauncher.launch(intent);
    }

    private void showAddLocationDialog(GeoPoint geoPoint) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_location, null);
        EditText titleEditText = dialogView.findViewById(R.id.titleEditText);
        EditText descriptionEditText = dialogView.findViewById(R.id.descriptionEditText);
        Button addPhotoButton = dialogView.findViewById(R.id.addPhotoButton);
        previewImageView = dialogView.findViewById(R.id.reminderImageView);

        addPhotoButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCameraIntent();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_IMAGE_CAPTURE);
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("Create New Location")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String title = titleEditText.getText().toString();
                    String description = descriptionEditText.getText().toString();

                    if (!title.isEmpty() && !description.isEmpty()) {
                        saveNewLocation(geoPoint, title, description);
                    } else {
                        Toast.makeText(this, "Title and description are required", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveNewLocation(GeoPoint geoPoint, String title, String description) {
        StoredLocation newLocation = new StoredLocation(
                title,
                geoPoint.getLatitude(),
                geoPoint.getLongitude(),
                description,
                capturedImage != null ? ImageUtils.bitmapToBase64(capturedImage) : null
        );

        if (currentUser != null) {
            newLocation.userId = currentUser.getUid();
        }

        db.collection("locations").document(newLocation.locationName)
                .set(newLocation)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Location saved", Toast.LENGTH_SHORT).show();
                    clearCapturedImage();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save location", Toast.LENGTH_SHORT).show();
                    Log.e("MainActivity", "Error saving location", e);
                });
    }

    private void clearCapturedImage() {
        capturedImage = null;
        if (previewImageView != null) {
            previewImageView.setVisibility(View.GONE);
        }
    }

    private void launchCameraIntent() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_IMAGE_CAPTURE);
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(takePictureIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCameraIntent();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void signInAnonymously() {
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        currentUser = mAuth.getCurrentUser();
                        initializeFirestoreListeners();
                    } else {
                        Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ... (other existing methods like onResume, onPause, etc.)

    @Override
    protected void onResume() {
        super.onResume();

        shownReminderDialogs.clear(); // Allow dialogs to show again
        notifiedReminders.clear(); // Allow notifications to show again

        if (getIntent() != null && getIntent().hasExtra("zoom_to_reminder")) {
            String reminderId = getIntent().getStringExtra("zoom_to_reminder");
            zoomToReminder(reminderId);
            getIntent().removeExtra("zoom_to_reminder");
        }

        if (mapView != null) mapView.onResume();
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

    }


    private void zoomToReminder(String reminderId) {
        for (StoredLocation reminder : reminders) {
            if (reminder.getId().equals(reminderId)) {
                GeoPoint point = new GeoPoint(reminder.latitude, reminder.longitude);
                mapView.getController().animateTo(point);
                mapView.getController().setZoom(18.0);
                break;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (locationManager != null) locationManager.removeUpdates(locationListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationsListener != null) locationsListener.remove();
        if (remindersListener != null) remindersListener.remove();
    }

    @Override
    public boolean singleTapConfirmedHelper(GeoPoint p) {
        return false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Light sensor handling
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Sensor accuracy handling
    }
}