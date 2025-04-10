package com.example.mapping;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.Map;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private Map<String, StoredLocation> storedLocations = new HashMap<String, StoredLocation>();
    private MapView mapView;
    private ActivityResultLauncher<String[]> locationPermissionRequest;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private final long minimumTimeBetweenUpdates = 10000; // 10 seconds
    private final float minimumDistanceBetweenUpdates = 0.5f; // 0.5 meters
    private NotificationManagerCompat notificationManager;
    private FirebaseFirestore db;
    private ListenerRegistration listenerRegistration;

    // Define notification key for notification channel
    static final String NOTIFICATION_KEY = "MyLocation";
    static final int NOTIFICATION_INTENT_CODE = 0;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        Switch trackingSwitch = findViewById(R.id.trackingSwitch);
        notificationManager = NotificationManagerCompat.from(getApplicationContext());
        createNotificationChannel();

        IMapController mapController = mapView.getController();
        mapController.setZoom(6.0);
        GeoPoint startPoint = new GeoPoint(52.8583, -2.2944);
        mapController.setCenter(startPoint);

        // Initialize locations and write to Firestore
        initializeLocations();

        trackingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    locationPermissionRequest.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.POST_NOTIFICATIONS
                    });
                } else {
                    if (locationManager != null) {
                        locationManager.removeUpdates(locationListener);
                    }
                }
            }
        });

        // Initialize location listener
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                GeoPoint currentLocation = new GeoPoint(
                        location.getLatitude(),
                        location.getLongitude());

                // Loop over each StoredLocation in the HashMap
                for (StoredLocation storedLocation : storedLocations.values()) {
                    GeoPoint geoPoint = new GeoPoint(storedLocation.latitude, storedLocation.longitude);
                    double distance = currentLocation.distanceToAsDouble(geoPoint);
                    double triggerDistance = 100; // 100 meters

                    if (distance < triggerDistance) {
                        Toast.makeText(getApplicationContext(),
                                "You are " + distance + " meters from " + storedLocation.locationName,
                                Toast.LENGTH_LONG).show();

                        if (!storedLocation.notificationActive && storedLocation.notificationsRequired) {
                            int notificationID = storedLocation.locationName.hashCode();
                            Notification notification = createNotification(storedLocation, distance);
                            notificationManager.notify(notificationID, notification);
                            storedLocation.notificationActive = true;
                        }
                    } else {
                        storedLocation.notificationActive = false;
                    }
                }

                mapView.getOverlays().clear();
                Marker currentLocationMarker = new Marker(mapView);
                currentLocationMarker.setPosition(currentLocation);
                mapView.getOverlays().add(currentLocationMarker);
                mapView.getController().animateTo(currentLocation);
                mapView.invalidate();

                Log.d("LocationUpdate", "Lat: " + location.getLatitude() + ", Lon: " + location.getLongitude());
            }
        };

        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                new ActivityResultCallback<Map<String, Boolean>>() {
                    @Override
                    public void onActivityResult(Map<String, Boolean> o) {
                        if (o.get(Manifest.permission.ACCESS_FINE_LOCATION)) {
                            Log.d("MyLocation", "Permission granted");
                            updateLocation();
                        } else {
                            Log.d("MyLocation", "Permission denied");
                        }
                    }
                });
    }

    private void initializeLocations() {
        StoredLocation location1 = new StoredLocation("City Campus", 52.1958, -2.2261);
        StoredLocation location2 = new StoredLocation("Upton Snodsbury", 52.1874, -2.0888);
        StoredLocation location3 = new StoredLocation("Beer", 50.69713, -3.10145);

        storedLocations.put(location1.locationName, location1);
        storedLocations.put(location2.locationName, location2);
        storedLocations.put(location3.locationName, location3);

        WriteBatch batch = db.batch();
        DocumentReference docRef1 = db.collection("locations").document(location1.locationName);
        batch.set(docRef1, location1);
        DocumentReference docRef2 = db.collection("locations").document(location2.locationName);
        batch.set(docRef2, location2);
        DocumentReference docRef3 = db.collection("locations").document(location3.locationName);
        batch.set(docRef3, location3);

        batch.commit()
                .addOnSuccessListener(aVoid -> Log.d("MyLocation", "Successfully stored locations to Firebase"))
                .addOnFailureListener(e -> Log.d("MyLocation", "Failed to store to Firebase", e));
    }

    @SuppressLint("MissingPermission")
    private void updateLocation() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
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
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_KEY,
                    "MyLocationChannel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("MyLocation updates");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(StoredLocation storedLocation, double distance) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(NOTIFICATION_KEY, storedLocation.locationName);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                NOTIFICATION_INTENT_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, NOTIFICATION_KEY)
                .setSmallIcon(R.drawable.marker_default_foreground)
                .setContentTitle("Near " + storedLocation.locationName)
                .setContentText("You are " + distance + " meters away")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String locationName = intent.getStringExtra(NOTIFICATION_KEY);
        if (locationName != null) {
            StoredLocation location = storedLocations.get(locationName);
            if (location != null) {
                showNotificationDialog(location);
            }
        }
    }

    private void showNotificationDialog(StoredLocation storedLocation) {
        new AlertDialog.Builder(this)
                .setTitle("Near " + storedLocation.locationName)
                .setMessage("Do you want to continue to receive notifications for this location in the future?")
                .setNegativeButton("NO", (dialog, which) -> {
                    storedLocation.notificationsRequired = false;
                    Toast.makeText(MainActivity.this,
                            "Notifications disabled for " + storedLocation.locationName,
                            Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("YES", (dialog, which) -> {
                    storedLocation.notificationsRequired = true;
                    Toast.makeText(MainActivity.this,
                            "Notifications enabled for " + storedLocation.locationName,
                            Toast.LENGTH_SHORT).show();
                })
                .create()
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
    }
}