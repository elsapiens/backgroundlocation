# Setup Guide - Elsapiens Background Location Plugin

This guide provides step-by-step instructions for integrating the Elsapiens Background Location Plugin into your Capacitor application.

## Prerequisites

- Capacitor 4.0+ application
- Android Studio (for Android development)
- Node.js 16+ and npm/yarn
- Android SDK with API 23+ support

## Installation Steps

### 1. Install the Plugin

```bash
npm install elsapiens-background-location
npx cap sync
```

### 2. Android Configuration

#### 2.1 Add Permissions to AndroidManifest.xml

Open `android/app/src/main/AndroidManifest.xml` and add the following permissions:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <!-- Your existing application configuration -->
    </application>

    <!-- Location Permissions -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

    <!-- Service Permissions -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- Network Permissions (for work hour tracking) -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Optional: For better location accuracy -->
    <uses-feature 
        android:name="android.hardware.location" 
        android:required="false" />
    <uses-feature 
        android:name="android.hardware.location.gps" 
        android:required="false" />
</manifest>
```

#### 2.2 Configure Google Play Services (if not already configured)

Add to `android/app/build.gradle`:

```gradle
dependencies {
    implementation 'com.google.android.gms:play-services-location:21.0.1'
    // Your other dependencies
}
```

#### 2.3 Proguard Configuration (if using)

Add to `android/app/proguard-rules.pro`:

```proguard
# Google Play Services Location
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Elsapiens Background Location Plugin
-keep class com.elsapiens.backgroundlocation.** { *; }
```

### 3. TypeScript/JavaScript Setup

#### 3.1 Import the Plugin

```typescript
import { BackgroundLocation } from 'elsapiens-background-location';
```

#### 3.2 Add Type Definitions (if using TypeScript)

The plugin includes TypeScript definitions. If you need custom types:

```typescript
// types/location.ts
export interface CustomLocationData {
  id: string;
  taskId: string;
  coordinates: {
    lat: number;
    lng: number;
  };
  timestamp: Date;
  accuracy: number;
}

export interface TrackingConfig {
  taskId: string;
  highAccuracy: boolean;
  updateInterval: number;
  minimumDistance: number;
}
```

## Integration Examples

### Example 1: Basic Task Tracking

```typescript
// services/location.service.ts
import { BackgroundLocation, LocationData } from 'elsapiens-background-location';

export class LocationService {
  private isTracking = false;
  
  async startTaskTracking(taskId: string): Promise<void> {
    // Check permissions first
    const permissions = await BackgroundLocation.checkPermissions();
    if (permissions.location !== 'granted') {
      const result = await BackgroundLocation.requestPermissions();
      if (result.location !== 'granted') {
        throw new Error('Location permissions required');
      }
    }

    // Set up location listener
    BackgroundLocation.addListener('locationUpdate', this.handleLocationUpdate);

    // Start tracking
    await BackgroundLocation.startTracking({
      reference: taskId,
      interval: 5000,        // 5 seconds
      minDistance: 15,       // 15 meters
      highAccuracy: true
    });

    this.isTracking = true;
    console.log(`Started tracking task: ${taskId}`);
  }

  async stopTaskTracking(): Promise<LocationData[]> {
    if (!this.isTracking) return [];

    await BackgroundLocation.stopTracking();
    this.isTracking = false;

    // Get all stored locations
    const result = await BackgroundLocation.getStoredLocations({
      reference: this.currentTaskId
    });

    return result.locations;
  }

  private handleLocationUpdate = (location: LocationData) => {
    console.log('Location update:', {
      lat: location.latitude,
      lng: location.longitude,
      accuracy: location.accuracy,
      distance: location.totalDistance
    });

    // Save to your local storage or send to backend
    this.saveLocationData(location);
  };

  private saveLocationData(location: LocationData) {
    // Implement your data persistence logic
    localStorage.setItem(`location_${Date.now()}`, JSON.stringify(location));
  }
}
```

### Example 2: Work Hour Tracking with Server Integration

```typescript
// services/work-hour-tracking.service.ts
import { BackgroundLocation, WorkHourLocationData } from 'elsapiens-background-location';

export class WorkHourTrackingService {
  private authToken: string = '';
  private engineerId: string = '';
  private serverUrl: string = '';

  constructor(config: {
    authToken: string;
    engineerId: string;
    serverUrl: string;
  }) {
    this.authToken = config.authToken;
    this.engineerId = config.engineerId;
    this.serverUrl = config.serverUrl;
  }

  async startWorkDay(): Promise<void> {
    // Check permissions
    await this.ensurePermissions();

    // Set up event listeners
    this.setupEventListeners();

    // Start work hour tracking
    await BackgroundLocation.startWorkHourTracking({
      engineerId: this.engineerId,
      serverUrl: this.serverUrl,
      uploadInterval: 300000, // 5 minutes
      authToken: this.authToken,
      enableOfflineQueue: true
    });

    // Start monitoring location service status
    await BackgroundLocation.startLocationStatusTracking();

    console.log(`Started work day tracking for ${this.engineerId}`);
  }

  async endWorkDay(): Promise<void> {
    await BackgroundLocation.stopWorkHourTracking();
    await BackgroundLocation.stopLocationStatusTracking();

    // Check for any remaining queued locations
    const queued = await BackgroundLocation.getQueuedWorkHourLocations();
    if (queued.locations.length > 0) {
      console.log(`${queued.locations.length} locations still queued for upload`);
    }

    console.log('Ended work day tracking');
  }

  private async ensurePermissions(): Promise<void> {
    const permissions = await BackgroundLocation.checkPermissions();
    
    if (permissions.location !== 'granted' || permissions.backgroundLocation !== 'granted') {
      const result = await BackgroundLocation.requestPermissions();
      
      if (result.location !== 'granted') {
        throw new Error('Location permissions are required for work hour tracking');
      }
      
      if (result.backgroundLocation !== 'granted') {
        console.warn('Background location permission not granted. Some features may be limited.');
      }
    }
  }

  private setupEventListeners(): void {
    // Work hour location updates
    BackgroundLocation.addListener('workHourLocationUpdate', (location: WorkHourLocationData) => {
      console.log('Work hour location captured:', location);
      this.onWorkHourLocationUpdate(location);
    });

    // Location service status changes
    BackgroundLocation.addListener('locationStatus', (status: { enabled: boolean }) => {
      if (!status.enabled) {
        console.warn('Location services disabled during work hours');
        this.handleLocationServiceDisabled();
      }
    });

    // Upload status (if your server provides feedback)
    BackgroundLocation.addListener('workHourLocationUploaded', (event) => {
      if (event.success) {
        console.log('Location uploaded successfully');
      } else {
        console.error('Location upload failed:', event.error);
      }
    });
  }

  private onWorkHourLocationUpdate(location: WorkHourLocationData) {
    // Custom handling for work hour location updates
    // e.g., update UI, store locally for backup, etc.
  }

  private handleLocationServiceDisabled() {
    // Handle location service disabled during work hours
    // e.g., show notification to user, log event, etc.
  }
}
```

### Example 3: Angular Service Integration

```typescript
// location-tracking.service.ts
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { BackgroundLocation, LocationData, WorkHourLocationData } from 'elsapiens-background-location';

export interface TrackingState {
  isTaskTracking: boolean;
  isWorkHourTracking: boolean;
  currentTaskId: string | null;
  lastLocation: LocationData | null;
  totalDistance: number;
}

@Injectable({
  providedIn: 'root'
})
export class LocationTrackingService {
  private trackingState = new BehaviorSubject<TrackingState>({
    isTaskTracking: false,
    isWorkHourTracking: false,
    currentTaskId: null,
    lastLocation: null,
    totalDistance: 0
  });

  public trackingState$ = this.trackingState.asObservable();

  constructor() {
    this.initializeEventListeners();
  }

  async startTaskTracking(taskId: string, config?: {
    interval?: number;
    minDistance?: number;
    highAccuracy?: boolean;
  }): Promise<void> {
    try {
      await this.checkPermissions();

      await BackgroundLocation.startTracking({
        reference: taskId,
        interval: config?.interval || 3000,
        minDistance: config?.minDistance || 10,
        highAccuracy: config?.highAccuracy ?? true
      });

      this.updateTrackingState({
        isTaskTracking: true,
        currentTaskId: taskId,
        totalDistance: 0
      });

    } catch (error) {
      console.error('Failed to start task tracking:', error);
      throw error;
    }
  }

  async stopTaskTracking(): Promise<LocationData[]> {
    const currentState = this.trackingState.value;
    
    if (!currentState.isTaskTracking || !currentState.currentTaskId) {
      return [];
    }

    try {
      await BackgroundLocation.stopTracking();

      const result = await BackgroundLocation.getStoredLocations({
        reference: currentState.currentTaskId
      });

      this.updateTrackingState({
        isTaskTracking: false,
        currentTaskId: null,
        lastLocation: null,
        totalDistance: 0
      });

      return result.locations;

    } catch (error) {
      console.error('Failed to stop task tracking:', error);
      throw error;
    }
  }

  async startWorkHourTracking(engineerId: string, serverUrl: string, authToken: string): Promise<void> {
    try {
      await this.checkPermissions();

      await BackgroundLocation.startWorkHourTracking({
        engineerId,
        serverUrl,
        authToken,
        uploadInterval: 300000,
        enableOfflineQueue: true
      });

      await BackgroundLocation.startLocationStatusTracking();

      this.updateTrackingState({
        isWorkHourTracking: true
      });

    } catch (error) {
      console.error('Failed to start work hour tracking:', error);
      throw error;
    }
  }

  async stopWorkHourTracking(): Promise<void> {
    try {
      await BackgroundLocation.stopWorkHourTracking();
      await BackgroundLocation.stopLocationStatusTracking();

      this.updateTrackingState({
        isWorkHourTracking: false
      });

    } catch (error) {
      console.error('Failed to stop work hour tracking:', error);
      throw error;
    }
  }

  private async checkPermissions(): Promise<void> {
    const permissions = await BackgroundLocation.checkPermissions();
    
    if (permissions.location !== 'granted') {
      const result = await BackgroundLocation.requestPermissions();
      
      if (result.location !== 'granted') {
        throw new Error('Location permissions are required');
      }
    }
  }

  private initializeEventListeners(): void {
    BackgroundLocation.addListener('locationUpdate', (location: LocationData) => {
      this.updateTrackingState({
        lastLocation: location,
        totalDistance: location.totalDistance || 0
      });
    });

    BackgroundLocation.addListener('workHourLocationUpdate', (location: WorkHourLocationData) => {
      console.log('Work hour location update:', location);
    });

    BackgroundLocation.addListener('locationStatus', (status: { enabled: boolean }) => {
      if (!status.enabled) {
        console.warn('Location services disabled');
      }
    });
  }

  private updateTrackingState(updates: Partial<TrackingState>): void {
    const currentState = this.trackingState.value;
    this.trackingState.next({ ...currentState, ...updates });
  }

  // Utility methods
  async getCurrentLocation(): Promise<any> {
    return await BackgroundLocation.getCurrentLocation();
  }

  async isLocationServiceEnabled(): Promise<boolean> {
    const result = await BackgroundLocation.isLocationServiceEnabled();
    return result.enabled;
  }

  async getQueuedWorkHourLocations(): Promise<WorkHourLocationData[]> {
    const result = await BackgroundLocation.getQueuedWorkHourLocations();
    return result.locations;
  }
}
```

## Configuration Options

### Task Tracking Configuration

```typescript
interface TaskTrackingConfig {
  reference: string;        // Unique identifier for the tracking session
  interval?: number;        // Update interval in milliseconds (default: 3000)
  minDistance?: number;     // Minimum distance in meters (default: 10)
  highAccuracy?: boolean;   // Use high accuracy GPS (default: true)
}

// Examples for different use cases
const configs = {
  // High-precision mapping
  surveying: {
    interval: 1000,      // 1 second
    minDistance: 1,      // 1 meter
    highAccuracy: true
  },
  
  // General delivery tracking
  delivery: {
    interval: 3000,      // 3 seconds
    minDistance: 10,     // 10 meters
    highAccuracy: true
  },
  
  // Battery-efficient long-distance
  longHaul: {
    interval: 15000,     // 15 seconds
    minDistance: 50,     // 50 meters
    highAccuracy: false
  }
};
```

### Work Hour Tracking Configuration

```typescript
interface WorkHourTrackingConfig {
  engineerId: string;           // Unique engineer/employee identifier
  serverUrl: string;            // Server endpoint for location uploads
  uploadInterval?: number;      // Upload frequency in milliseconds (default: 300000)
  authToken?: string;           // Authentication token for server requests
  enableOfflineQueue?: boolean; // Enable offline queueing (default: true)
}

// Environment-specific configurations
const environments = {
  development: {
    serverUrl: 'http://localhost:3000/api/locations',
    uploadInterval: 10000,  // 10 seconds for testing
    enableOfflineQueue: true
  },
  
  production: {
    serverUrl: 'https://api.company.com/work-locations',
    uploadInterval: 300000, // 5 minutes
    enableOfflineQueue: true
  }
};
```

## Testing Your Implementation

### 1. Permission Testing

```typescript
async function testPermissions() {
  console.log('=== Permission Testing ===');
  
  // Check initial permissions
  const initial = await BackgroundLocation.checkPermissions();
  console.log('Initial permissions:', initial);
  
  // Request permissions
  const requested = await BackgroundLocation.requestPermissions();
  console.log('After request:', requested);
  
  // Check location service
  const serviceStatus = await BackgroundLocation.isLocationServiceEnabled();
  console.log('Location service enabled:', serviceStatus.enabled);
}
```

### 2. Location Accuracy Testing

```typescript
async function testLocationAccuracy() {
  console.log('=== Location Accuracy Testing ===');
  
  // Test current location
  const current = await BackgroundLocation.getCurrentLocation();
  console.log('Current location accuracy:', current.accuracy, 'meters');
  
  // Test with different accuracy settings
  const testConfigs = [
    { highAccuracy: true, name: 'High Accuracy (GPS)' },
    { highAccuracy: false, name: 'Balanced Power' }
  ];
  
  for (const config of testConfigs) {
    console.log(`Testing ${config.name}...`);
    // Implement accuracy comparison logic
  }
}
```

### 3. Offline Queue Testing

```typescript
async function testOfflineQueue() {
  console.log('=== Offline Queue Testing ===');
  
  // Start work hour tracking with invalid URL
  await BackgroundLocation.startWorkHourTracking({
    engineerId: 'test_engineer',
    serverUrl: 'http://invalid-url-for-testing.com',
    uploadInterval: 5000, // 5 seconds for quick testing
    enableOfflineQueue: true
  });
  
  // Wait and check queue size
  setTimeout(async () => {
    const queued = await BackgroundLocation.getQueuedWorkHourLocations();
    console.log('Queued locations:', queued.locations.length);
    
    // Clear test data
    await BackgroundLocation.clearQueuedWorkHourLocations();
    await BackgroundLocation.stopWorkHourTracking();
  }, 30000);
}
```

## Troubleshooting Common Setup Issues

### Issue 1: Build Errors After Installation

**Error**: `Cannot resolve symbol 'BackgroundLocationPlugin'`

**Solution**: 
```bash
# Clean and rebuild
npx cap clean android
npx cap copy android
npx cap sync android

# Open in Android Studio and sync project
npx cap open android
```

### Issue 2: Permission Request Not Working

**Error**: Permissions always return 'denied'

**Solutions**:
1. Check AndroidManifest.xml has all required permissions
2. Ensure target SDK version supports runtime permissions (API 23+)
3. Test on physical device (permissions may behave differently on emulator)

### Issue 3: Location Updates Not Received

**Error**: No 'locationUpdate' events fired

**Solutions**:
1. Verify event listeners are set up before calling `startTracking()`
2. Test outdoors with clear GPS signal
3. Check device location services are enabled
4. Verify interval and minDistance settings aren't too restrictive

### Issue 4: Background Tracking Stops

**Error**: Tracking stops when app is backgrounded

**Solutions**:
1. Ensure `ACCESS_BACKGROUND_LOCATION` permission is granted
2. Check battery optimization settings exclude your app
3. Verify foreground service is properly configured
4. Test on different Android versions (behavior varies)

## Best Practices

1. **Always request permissions before starting tracking**
2. **Set up event listeners before starting any tracking**
3. **Handle permission denial gracefully**
4. **Clean up resources when tracking is no longer needed**
5. **Test thoroughly on different devices and Android versions**
6. **Implement proper error handling for all async operations**
7. **Consider battery impact when choosing tracking parameters**
8. **Provide clear user feedback about tracking status**

## Next Steps

After completing the setup:

1. **Read the [API Documentation](API_DOCUMENTATION.md)** for detailed method references
2. **Review the [Developer Guide](DEVELOPER_GUIDE.md)** for architecture insights
3. **Check the [Contributing Guide](CONTRIBUTING.md)** if you plan to contribute
4. **Join our discussions** for community support and feature requests

## Support

If you encounter issues during setup:

1. Check this guide and the troubleshooting section
2. Review the [API Documentation](API_DOCUMENTATION.md)
3. Search existing [GitHub Issues](https://github.com/your-org/elsapiens-background-location/issues)
4. Create a new issue with detailed information about your setup and the problem