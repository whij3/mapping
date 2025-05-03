package com.example.mapping;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@IgnoreExtraProperties
public class StoredLocation implements Serializable {
    // Location properties
    public String locationName;
    public double latitude;
    public double longitude;

    // Notification settings
    public boolean notificationActive = false;
    public boolean notificationsRequired = true;

    // Reminder properties
    public boolean isReminder = false;

    // Firestore document fields
    public String title;
    public String description;
    public String photo;
    public String userId;
    public int triggerDistance = 100;
    public List<String> sharedWith = new ArrayList<>();
    public String creatorId;

    // Document ID management
    @Exclude
    private String id;  // Changed from documentId to id for consistency

    @Exclude
    public boolean isOwned;

    // Required no-argument constructor for Firestore
    public StoredLocation() {
    }

    // Constructor for basic location
    public StoredLocation(String locationName, double latitude, double longitude) {
        this.locationName = locationName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Constructor for locations with description and photo
    public StoredLocation(String locationName, double latitude, double longitude,
                          String description, String photo) {
        this.locationName = locationName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
        this.photo = photo;
    }

    // Constructor for reminders with all fields
    public StoredLocation(String title, String description, double latitude,
                          double longitude, String photo, String userId,
                          boolean isReminder) {
        this.title = title;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.photo = photo;
        this.userId = userId;
        this.isReminder = isReminder;
        this.creatorId = userId;
        this.isOwned = true;
    }

    // Document ID getter and setter
    @Exclude
    public String getId() {
        return id;
    }

    @Exclude
    public void setId(String id) {
        this.id = id;
    }

    // Ownership and sharing methods
    @Exclude
    public boolean isShared() {
        return sharedWith != null && !sharedWith.isEmpty();
    }

    @Exclude
    public boolean isCreatedBy(String userId) {
        return creatorId != null && creatorId.equals(userId);
    }
    @Exclude
    public void shareWith(String userId) {
        if (sharedWith == null) {
            sharedWith = new ArrayList<>();
        }
        if (!sharedWith.contains(userId)) {
            sharedWith.add(userId);
        }
    }

    @Exclude
    public void unshareWith(String userId) {
        if (sharedWith != null) {
            sharedWith.remove(userId);
        }
    }


    // Helper method to check if shared with specific user
    @Exclude
    public boolean isSharedWith(String userId) {
        return sharedWith != null && sharedWith.contains(userId);
    }

    // Compatibility method (if other code was using documentId)
    @Exclude
    public String getDocumentId() {
        return id;
    }

    @Exclude
    public void setDocumentId(String documentId) {
        this.id = documentId;
    }
}