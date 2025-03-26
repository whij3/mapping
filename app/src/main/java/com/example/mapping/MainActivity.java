package com.example.mapping;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private ActivityResultLauncher<String[]> locationPermissionRequest;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private final long minimumTimeBetweenUpdates = 10000; // 10 seconds
    private final float minimumDistanceBetweenUpdates = 0.5f; // 0.5 meters

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
        trackingSwitch.isChecked(); //true of checked; false if not

        IMapController mapController = mapView.getController();
        mapController.setZoom(6.0);
        GeoPoint startPoint = new GeoPoint(52.8583, -2.2944);
        mapController.setCenter(startPoint);


        trackingSwitch.setOnCheckedChangeListener(new
                                                          CompoundButton.OnCheckedChangeListener() {
                                                              @Override
                                                              public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//Check the state of the switch here and do something
                                                              }
                                                          });
        // Initialize location listener
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // Handle location updates
                GeoPoint currentLocation = new GeoPoint(
                        location.getLatitude(),
                        location.getLongitude());

                // Clear previous markers
                mapView.getOverlays().clear();

                // Create and add new marker
                Marker currentLocationMarker = new Marker(mapView);
                currentLocationMarker.setPosition(currentLocation);
                mapView.getOverlays().add(currentLocationMarker);

                // Center map on current location
                mapView.getController().animateTo(currentLocation);

                // Redraw map
                mapView.invalidate();

                Log.d("LocationUpdate", "Lat: " + location.getLatitude() + ", Lon: " + location.getLongitude());
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
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

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        locationPermissionRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        locationManager.removeUpdates(locationListener);
    }
}