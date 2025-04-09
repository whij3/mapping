package com.example.mapping;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

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

    // Define notification key for notification channel
    static final String NOTIFICATION_KEY = "MyLocation";

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        Switch trackingSwitch = findViewById(R.id.trackingSwitch);
        trackingSwitch.isChecked(); //true if checked; false if not
        notificationManager = NotificationManagerCompat.from(getApplicationContext());

        IMapController mapController = mapView.getController();
        mapController.setZoom(6.0);
        GeoPoint startPoint = new GeoPoint(52.8583, -2.2944);
        mapController.setCenter(startPoint);

        trackingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Handle the tracking switch state change
            }
        });

        // Initialize interesting locations (only once)
        StoredLocation location1 = new StoredLocation("Upton Snodsbury", 52.1874, -2.0888);
        StoredLocation location2 = new StoredLocation("City Campus", 52.1958, -2.2261);
        StoredLocation location3 = new StoredLocation("Beer", 50.69713, -3.10145);

        // Add them to the HashMap (key:locationName, value:StoredLocation)
        storedLocations.put(location1.locationName, location1);
        storedLocations.put(location2.locationName, location2);
        storedLocations.put(location3.locationName, location3);

        // Initialize location listener
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                GeoPoint currentLocation = new GeoPoint(
                        location.getLatitude(),
                        location.getLongitude());

                // Loop over each StoredLocation in the HashMap
                for (StoredLocation storedLocation : storedLocations.values()) {
                    // Calculate the distance between current location and stored location
                    GeoPoint geoPoint = new GeoPoint(storedLocation.latitude, storedLocation.longitude);
                    double distance = currentLocation.distanceToAsDouble(geoPoint);

                    // Define the trigger distance (e.g., 100 meters)
                    double triggerDistance = 100; // 100 meters

                    if (distance < triggerDistance) {
                        // Show a toast if the user is within the trigger distance
                        Toast.makeText(getApplicationContext(),
                                "You are " + distance + " meters from " + storedLocation.locationName,
                                Toast.LENGTH_LONG).show();

                        // Only generate a notification for this StoredLocation IF
                        // there is not already a notification active for this storedLocation
                        // AND notifications are required for this storedLocation
                        if (!storedLocation.notificationActive && storedLocation.notificationsRequired) {
                            // Create a unique ID code for this notification (hash of the location name)
                            int notificationID = storedLocation.locationName.hashCode();

                            // Call the createNotification function to build a notification
                            Notification notification = createNotification(storedLocation, distance);

                            // Notify the notification manager about the new notification
                            notificationManager.notify(notificationID, notification);

                            // Mark the StoredLocation to show the notification is active.
                            storedLocation.notificationActive = true;
                        }
                    } else {
                        // If the StoredLocation is not close enough, mark it as inactive
                        storedLocation.notificationActive = false;
                    }
                }

                // Clear previous markers
                mapView.getOverlays().clear();

                // Create and add a new marker for the current location
                Marker currentLocationMarker = new Marker(mapView);
                currentLocationMarker.setPosition(currentLocation);
                mapView.getOverlays().add(currentLocationMarker);

                // Center the map on the current location
                mapView.getController().animateTo(currentLocation);

                // Redraw the map
                mapView.invalidate();

                Log.d("LocationUpdate", "Lat: " + location.getLatitude() + ", Lon: " + location.getLongitude());
            }
        };

            locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                new ActivityResultCallback<Map<String, Boolean>>() {
                    @Override
                    public void onActivityResult(Map<String, Boolean> o) {
                        boolean fineLocationAllowed = o.get(Manifest.permission.ACCESS_FINE_LOCATION);
                        if (fineLocationAllowed) {
                            Log.d("MyLocation", "Permission granted");
                            updateLocation();
                        } else {
                            Log.d("MyLocation", "Permission denied");
                        }
                    }
                });
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

            // Get last known location immediately
            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) {
                locationListener.onLocationChanged(lastKnownLocation);
            }
        }
    }


    private void createNotificationChannel() {
        String channel_name = "MyLocationChannel";
        int channel_importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel;
        channel = new NotificationChannel(NOTIFICATION_KEY, channel_name,channel_importance);
        channel.setDescription("MyLocation updates");
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        locationPermissionRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    // Create a notification to show when the user is near a stored location
    private Notification createNotification(StoredLocation storedLocation, double distance) {
        return new NotificationCompat.Builder(this, NOTIFICATION_KEY)
                .setSmallIcon(R.drawable.marker_default_foreground) // Use your own drawable
                .setContentTitle("MyLocation update: " + storedLocation.locationName)
                .setContentText("You are " + distance + " meters from " + storedLocation.locationName)
                .setAutoCancel(true)
                .build();
    }
}
