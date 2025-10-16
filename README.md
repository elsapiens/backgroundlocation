# Elsapiens Background Location Plugin

A comprehensive Capacitor plugin for background location tracking with support for both task-based tracking and work hour monitoring. Features intelligent location management, offline queuing, and battery optimization.

**Developed by [Elsapiens](https://elsapiens.com)** - Innovative mobile solutions for enterprise location tracking and workforce management.

## 📚 Documentation

- **[Setup Guide](SETUP_GUIDE.md)** - Complete installation and integration guide
- **[API Documentation](API_DOCUMENTATION.md)** - Detailed API reference with examples  
- **[Developer Guide](DEVELOPER_GUIDE.md)** - Architecture overview and development guide
- **[Contributing Guide](CONTRIBUTING.md)** - How to contribute to the project

## ✨ Features

- **🎯 Task-Based Tracking**: Detailed location tracking for specific tasks or routes
- **⏰ Work Hour Tracking**: Periodic location uploads during work hours
- **🔋 Battery Optimized**: Intelligent coordination to minimize battery drain
- **📱 Offline Support**: Queue locations when offline, upload when connected
- **🔒 Permission Management**: Comprehensive Android permission handling
- **📊 Real-time Events**: Live location updates via Capacitor events
- **💾 Local Storage**: SQLite database for reliable data persistence

## 🚀 Quick Reference

| Use Case | Tracking Mode | Key Features | Best For |
|----------|---------------|--------------|----------|
| **Delivery Routes** | Task-Based | High accuracy, route recording, distance calculation | Short-term detailed tracking |
| **Field Service** | Task-Based | Real-time updates, offline storage, complete route history | Service calls, repairs |
| **Employee Monitoring** | Work Hour | Periodic uploads, battery optimized, offline queue | All-day location monitoring |
| **Fleet Management** | Both | Combined detailed + periodic tracking | Comprehensive vehicle tracking |
| **Time & Attendance** | Work Hour | Clock in/out locations, compliance tracking | Workforce management |

## Installation

```bash
npm install elsapiens-background-location
npx cap sync
```

### Android Configuration

Add the following permissions to your `android/app/src/main/AndroidManifest.xml`:

```xml
<!-- Location permissions -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Service permissions -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- Network permissions for work hour tracking -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Quick Start

### Basic Task Tracking

```typescript
import { BackgroundLocation } from 'elsapiens-background-location';

async function startTaskTracking() {
  // Check and request permissions
  const permissions = await BackgroundLocation.checkPermissions();
  if (permissions.location !== 'granted') {
    await BackgroundLocation.requestPermissions();
  }

  // Listen for location updates
  BackgroundLocation.addListener('locationUpdate', (location) => {
    console.log('New location:', location.latitude, location.longitude);
    console.log('Distance traveled:', location.totalDistance, 'meters');
  });

  // Start tracking
  await BackgroundLocation.startTracking({
    reference: 'task_123',
    interval: 3000,        // Update every 3 seconds
    minDistance: 10,       // Minimum 10 meters movement
    highAccuracy: true     // Use GPS for high accuracy
  });
}

async function stopTaskTracking() {
  await BackgroundLocation.stopTracking();
  
  // Retrieve stored locations
  const result = await BackgroundLocation.getStoredLocations({
    reference: 'task_123'
  });
  console.log('Total locations:', result.locations.length);
}
```

### Work Hour Tracking

```typescript
async function startWorkDay() {
  // Listen for work hour location updates
  BackgroundLocation.addListener('workHourLocationUpdate', (location) => {
    console.log('Work location captured:', location);
  });

  // Start work hour tracking
  await BackgroundLocation.startWorkHourTracking({
    engineerId: 'engineer_123',
    serverUrl: 'https://api.company.com/work-locations',
    uploadInterval: 300000,  // Upload every 5 minutes
    authToken: 'your-auth-token',
    enableOfflineQueue: true
  });
}

async function endWorkDay() {
  await BackgroundLocation.stopWorkHourTracking();
  
  // Check for any queued locations
  const queued = await BackgroundLocation.getQueuedWorkHourLocations();
  if (queued.locations.length > 0) {
    console.log('Queued locations will be uploaded when online');
  }
}
```

## Core Concepts

### Task-Based Tracking
- **Purpose**: Detailed route tracking for specific tasks
- **Data Storage**: Local SQLite database
- **Use Cases**: Delivery routes, service calls, field work
- **Features**: High-precision GPS, distance calculation, complete route history

### Work Hour Tracking  
- **Purpose**: Periodic location monitoring during work hours
- **Data Storage**: Server uploads with offline queuing
- **Use Cases**: Employee monitoring, time tracking, compliance
- **Features**: Battery-optimized intervals, automatic uploads, offline support

### Intelligent Coordination
The plugin uses a sophisticated coordination system to manage multiple tracking modes:
- **Single Location Source**: All tracking modes share one GPS request
- **Optimal Parameters**: Automatically selects best accuracy and interval settings
- **Battery Optimization**: Minimizes power consumption through intelligent batching
- **Conflict Prevention**: Prevents multiple services from interfering with each other

## Advanced Usage

### Complete Service Implementation

```typescript
import { BackgroundLocation } from 'elsapiens-background-location';

export class LocationTrackingService {
  private isTracking = false;
  private currentTaskId: string | null = null;

  async initialize(): Promise<boolean> {
    // Check if location services are enabled
    const serviceStatus = await BackgroundLocation.isLocationServiceEnabled();
    if (!serviceStatus.enabled) {
      console.log('Location services disabled');
      await BackgroundLocation.openLocationSettings();
      return false;
    }

    // Check and request permissions
    const permissions = await BackgroundLocation.checkPermissions();
    if (permissions.location !== 'granted' || permissions.backgroundLocation !== 'granted') {
      const newPermissions = await BackgroundLocation.requestPermissions();
      if (newPermissions.location !== 'granted') {
        throw new Error('Location permissions are required');
      }
    }

    // Set up event listeners
    this.setupEventListeners();
    return true;
  }

  private setupEventListeners() {
    // Task location updates
    BackgroundLocation.addListener('locationUpdate', (location) => {
      this.handleLocationUpdate(location);
    });

    // Work hour location updates
    BackgroundLocation.addListener('workHourLocationUpdate', (location) => {
      this.handleWorkHourUpdate(location);
    });

    // Location service status changes
    BackgroundLocation.addListener('locationStatus', (status) => {
      if (!status.enabled && this.isTracking) {
        this.handleLocationServiceDisabled();
      }
    });
  }

  async startTaskTracking(taskId: string, options?: {
    interval?: number;
    minDistance?: number;
    highAccuracy?: boolean;
  }) {
    if (!await this.initialize()) {
      throw new Error('Failed to initialize location services');
    }

    this.currentTaskId = taskId;
    this.isTracking = true;

    await BackgroundLocation.startTracking({
      reference: taskId,
      interval: options?.interval || 3000,
      minDistance: options?.minDistance || 10,
      highAccuracy: options?.highAccuracy ?? true
    });

    console.log(`Started tracking task: ${taskId}`);
  }

  async stopTaskTracking(): Promise<LocationData[]> {
    if (!this.isTracking || !this.currentTaskId) {
      return [];
    }

    await BackgroundLocation.stopTracking();
    
    // Retrieve all stored locations
    const result = await BackgroundLocation.getStoredLocations({
      reference: this.currentTaskId
    });

    this.isTracking = false;
    this.currentTaskId = null;

    console.log(`Stopped tracking. Captured ${result.locations.length} locations`);
    return result.locations;
  }

  async startWorkHourTracking(engineerId: string, serverUrl: string) {
    if (!await this.initialize()) {
      throw new Error('Failed to initialize location services');
    }

    await BackgroundLocation.startWorkHourTracking({
      engineerId,
      serverUrl,
      uploadInterval: 300000, // 5 minutes
      authToken: await this.getAuthToken(),
      enableOfflineQueue: true
    });

    // Start monitoring location service status
    await BackgroundLocation.startLocationStatusTracking();
    
    console.log(`Started work hour tracking for ${engineerId}`);
  }

  async stopWorkHourTracking() {
    await BackgroundLocation.stopWorkHourTracking();
    await BackgroundLocation.stopLocationStatusTracking();
    
    console.log('Stopped work hour tracking');
  }

  private handleLocationUpdate(location: LocationData) {
    console.log(`Task location update:`, {
      lat: location.latitude,
      lng: location.longitude,
      accuracy: location.accuracy,
      distance: location.totalDistance
    });

    // Save to local storage or send to your backend
    this.saveLocationUpdate(location);
  }

  private handleWorkHourUpdate(location: WorkHourLocationData) {
    console.log(`Work hour location:`, {
      lat: location.latitude,
      lng: location.longitude,
      engineerId: location.engineerId,
      timestamp: new Date(location.timestamp)
    });
  }

  private handleLocationServiceDisabled() {
    console.warn('Location services disabled during tracking');
    // Handle gracefully - perhaps notify user
    this.notifyLocationServiceDisabled();
  }

  private async getAuthToken(): Promise<string> {
    // Implement your authentication logic
    return localStorage.getItem('authToken') || '';
  }

  private saveLocationUpdate(location: LocationData) {
    // Implement your local storage logic
    const stored = JSON.parse(localStorage.getItem('taskLocations') || '[]');
    stored.push(location);
    localStorage.setItem('taskLocations', JSON.stringify(stored));
  }

  private notifyLocationServiceDisabled() {
    // Implement user notification
    console.warn('Please enable location services to continue tracking');
  }
}
```

### Error Handling Best Practices

```typescript
async function robustLocationTracking() {
  try {
    await BackgroundLocation.startTracking({
      reference: 'task_123',
      interval: 3000,
      minDistance: 10,
      highAccuracy: true
    });
  } catch (error) {
    console.error('Failed to start tracking:', error);
    
    // Handle specific error cases
    if (error.message.includes('permission')) {
      // Guide user to grant permissions
      const permissions = await BackgroundLocation.requestPermissions();
      if (permissions.location !== 'granted') {
        // Show user-friendly permission explanation
        showPermissionExplanation();
      }
    } else if (error.message.includes('location service')) {
      // Guide user to enable location services
      await BackgroundLocation.openLocationSettings();
    } else {
      // Handle other errors
      showGenericError(error.message);
    }
  }
}
```

### Performance Optimization

```typescript
// Optimize for different use cases
const trackingConfigs = {
  // High precision for detailed mapping
  highPrecision: {
    interval: 1000,      // 1 second
    minDistance: 1,      // 1 meter
    highAccuracy: true
  },
  
  // Balanced for general tracking
  balanced: {
    interval: 3000,      // 3 seconds
    minDistance: 10,     // 10 meters
    highAccuracy: true
  },
  
  // Battery saving for long-term tracking
  batterySaver: {
    interval: 15000,     // 15 seconds
    minDistance: 25,     // 25 meters
    highAccuracy: false
  }
};

// Use appropriate config based on requirements
await BackgroundLocation.startTracking({
  reference: 'task_123',
  ...trackingConfigs.balanced
});
```

## Troubleshooting

### Common Issues

#### 1. "Permission denied" errors
**Problem**: Location permissions not granted or insufficient permissions.

**Solutions**:
- Check all required permissions are declared in AndroidManifest.xml
- Ensure background location permission is granted for work hour tracking
- Test permission flow on different Android versions (API 23+, 29+)

```typescript
// Check specific permission status
const permissions = await BackgroundLocation.checkPermissions();
console.log('Permissions:', permissions);

if (permissions.backgroundLocation !== 'granted') {
  console.log('Background location permission required for work hour tracking');
}
```

#### 2. "Location service disabled" errors
**Problem**: Device location services are turned off.

**Solutions**:
- Guide users to device settings
- Check location service status before starting tracking
- Monitor status changes during tracking

```typescript
const status = await BackgroundLocation.isLocationServiceEnabled();
if (!status.enabled) {
  await BackgroundLocation.openLocationSettings();
}
```

#### 3. No location updates received
**Problem**: Tracking started but no location events received.

**Solutions**:
- Verify device has GPS signal (test outdoors)
- Check interval and distance settings aren't too restrictive
- Ensure event listeners are set up before starting tracking
- Test with different accuracy settings

```typescript
// Debug location updates
let updateCount = 0;
BackgroundLocation.addListener('locationUpdate', (location) => {
  updateCount++;
  console.log(`Update #${updateCount}:`, location);
});
```

#### 4. Work hour locations not uploading
**Problem**: Locations captured but not reaching server.

**Solutions**:
- Verify server URL is accessible and accepts POST requests
- Check authentication token is valid and not expired
- Review server logs for request errors
- Test network connectivity

```typescript
// Check queued locations
const queued = await BackgroundLocation.getQueuedWorkHourLocations();
console.log(`${queued.locations.length} locations queued for upload`);

// Clear queue if needed (for testing)
// await BackgroundLocation.clearQueuedWorkHourLocations();
```

### Performance Issues

#### High Battery Usage
- **Reduce update frequency**: Increase interval between location updates
- **Lower accuracy**: Use balanced power mode instead of high accuracy
- **Increase minimum distance**: Only update when moved significant distance
- **Verify cleanup**: Ensure tracking is properly stopped when not needed

#### Slow Performance
- **Database cleanup**: Regularly clear old location data
- **Memory management**: Remove event listeners when not needed
- **Batch operations**: Group database operations where possible

### Testing Guidelines

#### Local Testing
```typescript
// Test permission flow
async function testPermissions() {
  console.log('Initial permissions:', await BackgroundLocation.checkPermissions());
  
  const requested = await BackgroundLocation.requestPermissions();
  console.log('After request:', requested);
}

// Test location accuracy
async function testLocationAccuracy() {
  const current = await BackgroundLocation.getCurrentLocation();
  console.log('Current location accuracy:', current.accuracy, 'meters');
}

// Test offline queue
async function testOfflineQueue() {
  // Start work hour tracking
  await BackgroundLocation.startWorkHourTracking({
    engineerId: 'test_engineer',
    serverUrl: 'http://invalid-url.com', // Intentionally invalid
    uploadInterval: 10000, // 10 seconds for testing
    enableOfflineQueue: true
  });
  
  // Wait and check queue
  setTimeout(async () => {
    const queued = await BackgroundLocation.getQueuedWorkHourLocations();
    console.log('Queued locations:', queued.locations.length);
  }, 30000);
}
```

## Platform Support

| Platform | Support Status | Notes |
|----------|---------------|-------|
| **Android** | ✅ Full Support | All features available |
| **iOS** | ⏳ Planned | Not currently implemented |
| **Web** | 🔧 Development Only | Stub implementation for testing |

### Android Requirements
- **Minimum SDK**: API 23 (Android 6.0)
- **Target SDK**: API 34+ recommended
- **Google Play Services**: Location services required
- **Permissions**: Multiple location permissions required

### Android Version Compatibility

| Android Version | API Level | Background Location | Foreground Service | Notes |
|----------------|-----------|-------------------|-------------------|-------|
| 6.0 - 7.1 | 23-25 | ✅ Available | ✅ Available | Runtime permissions required |
| 8.0 - 8.1 | 26-27 | ✅ Available | ✅ Available | Background service limitations |
| 9.0 | 28 | ✅ Available | ✅ Available | Additional battery optimizations |
| 10.0+ | 29+ | ⚠️ Restricted | ✅ Available | Background location requires user approval |
| 11.0+ | 30+ | ⚠️ Restricted | ✅ Available | One-time permissions, scoped storage |
| 12.0+ | 31+ | ⚠️ Restricted | ✅ Available | Approximate location option |
| 13.0+ | 33+ | ⚠️ Restricted | ✅ Available | Runtime notification permissions |
| 14.0+ | 34+ | ⚠️ Restricted | ✅ Available | Enhanced privacy features |

## Architecture Overview

The plugin is built with a modular architecture for maintainability and extensibility:

```
BackgroundLocationPlugin (Main API)
├── LocationPermissionManager (Permission Handling)
├── LocationDataManager (Data Processing)
├── LocationTrackingManager (Tracking Coordination)
├── LocationCoordinator (Service Management)
└── WorkHourLocationUploader (Server Communication)
```

### Key Components

- **LocationPermissionManager**: Handles all Android location permission requests and validation
- **LocationDataManager**: Processes location data, performs validation, and manages SQLite storage
- **LocationTrackingManager**: Coordinates different tracking modes and manages their lifecycle
- **LocationCoordinator**: Singleton that manages FusedLocationProviderClient to prevent conflicts
- **WorkHourLocationUploader**: Handles background uploads to server with offline queue support

## Configuration

### Default Settings
```typescript
const defaultConfig = {
  // Task tracking defaults
  interval: 3000,           // 3 seconds
  minDistance: 10,          // 10 meters
  highAccuracy: true,       // Use GPS
  
  // Work hour tracking defaults
  uploadInterval: 300000,   // 5 minutes
  enableOfflineQueue: true, // Enable offline support
  maxQueueSize: 1000,      // Maximum queued locations
  
  // Database settings
  maxStoredLocations: 10000, // Auto-cleanup threshold
  locationValidityTime: 30000, // 30 seconds
};
```

### Environment Configuration
For different deployment environments:

```typescript
const configs = {
  development: {
    uploadInterval: 10000,    // 10 seconds for testing
    serverUrl: 'http://localhost:3000/api/locations',
    enableDebugLogging: true
  },
  
  staging: {
    uploadInterval: 60000,    // 1 minute for staging
    serverUrl: 'https://staging-api.company.com/locations',
    enableDebugLogging: true
  },
  
  production: {
    uploadInterval: 300000,   // 5 minutes for production
    serverUrl: 'https://api.company.com/locations',
    enableDebugLogging: false
  }
};
```

## Security Considerations

### Data Protection
- **Local Storage**: All location data stored in app-private SQLite database
- **Network Security**: Use HTTPS for all server communications
- **Authentication**: Support for token-based authentication
- **Data Minimization**: Only collect necessary location data
- **Retention Policies**: Implement data cleanup for old locations

### Privacy Best Practices
1. **User Consent**: Always obtain explicit user consent before tracking
2. **Transparency**: Clearly explain what data is collected and why
3. **User Control**: Provide easy start/stop controls
4. **Data Access**: Allow users to view and delete their data
5. **Compliance**: Follow GDPR, CCPA, and other applicable regulations

### Permissions Security
```typescript
// Always check permissions before sensitive operations
async function secureLocationAccess() {
  const permissions = await BackgroundLocation.checkPermissions();
  
  if (permissions.location !== 'granted') {
    // Explain why permissions are needed
    showPermissionExplanation();
    const result = await BackgroundLocation.requestPermissions();
    
    if (result.location !== 'granted') {
      throw new Error('Location access denied by user');
    }
  }
}
```

## Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) and [Developer Guide](DEVELOPER_GUIDE.md) for detailed information about:

- Setting up development environment
- Code style and conventions
- Testing requirements
- Pull request guidelines
- Architecture documentation

### Quick Development Setup
```bash
# Clone the repository
git clone https://github.com/elsapiens/backgroundlocation.git
cd backgroundlocation

# Install dependencies
npm install

# Build the plugin
npm run build

# Run tests
npm test
```

## License

This project is licensed under the [MIT License](LICENSE).

## Support

- **Documentation**: [API Documentation](API_DOCUMENTATION.md) | [Developer Guide](DEVELOPER_GUIDE.md) | [Setup Guide](SETUP_GUIDE.md)
- **Issues**: [GitHub Issues](https://github.com/elsapiens/backgroundlocation/issues)
- **Company**: [Elsapiens](https://elsapiens.com) - Enterprise mobile solutions
- **Author**: Dawn Dharmishtan

---
```

## API

<docgen-index>

* [`checkPermissions()`](#checkpermissions)
* [`requestPermissions()`](#requestpermissions)
* [`isLocationServiceEnabled()`](#islocationserviceenabled)
* [`openLocationSettings()`](#openlocationsettings)
* [`startTracking(...)`](#starttracking)
* [`stopTracking()`](#stoptracking)
* [`getStoredLocations(...)`](#getstoredlocations)
* [`getCurrentLocation()`](#getcurrentlocation)
* [`clearStoredLocations()`](#clearstoredlocations)
* [`addListener('locationUpdate', ...)`](#addlistenerlocationupdate-)
* [`addListener('locationStatus', ...)`](#addlistenerlocationstatus-)
* [`addListener('workHourLocationUpdate', ...)`](#addlistenerworkhourlocationupdate-)
* [`addListener('workHourLocationUploaded', ...)`](#addlistenerworkhourlocationuploaded-)
* [`getLastLocation(...)`](#getlastlocation)
* [`startLocationStatusTracking()`](#startlocationstatustracking)
* [`stopLocationStatusTracking()`](#stoplocationstatustracking)
* [`startWorkHourTracking(...)`](#startworkhourtracking)
* [`stopWorkHourTracking()`](#stopworkhourtracking)
* [`isWorkHourTrackingActive()`](#isworkhourtrackingactive)
* [`getQueuedWorkHourLocations()`](#getqueuedworkhourlocations)
* [`clearQueuedWorkHourLocations()`](#clearqueuedworkhourlocations)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### checkPermissions()

```typescript
checkPermissions() => Promise<PermissionStatus>
```

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### requestPermissions()

```typescript
requestPermissions() => Promise<PermissionStatus>
```

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### isLocationServiceEnabled()

```typescript
isLocationServiceEnabled() => Promise<{ enabled: boolean; }>
```

**Returns:** <code>Promise&lt;{ enabled: boolean; }&gt;</code>

--------------------


### openLocationSettings()

```typescript
openLocationSettings() => Promise<void>
```

--------------------


### startTracking(...)

```typescript
startTracking({ reference, highAccuracy, minDistance, interval }: { reference: string; highAccuracy: boolean; minDistance: number; interval: number; }) => Promise<void>
```

| Param     | Type                                                                                              |
| --------- | ------------------------------------------------------------------------------------------------- |
| **`__0`** | <code>{ reference: string; highAccuracy: boolean; minDistance: number; interval: number; }</code> |

--------------------


### stopTracking()

```typescript
stopTracking() => Promise<void>
```

--------------------


### getStoredLocations(...)

```typescript
getStoredLocations({ reference }: { reference: string; }) => Promise<{ locations: LocationData[]; }>
```

| Param     | Type                                |
| --------- | ----------------------------------- |
| **`__0`** | <code>{ reference: string; }</code> |

**Returns:** <code>Promise&lt;{ locations: LocationData[]; }&gt;</code>

--------------------


### getCurrentLocation()

```typescript
getCurrentLocation() => Promise<{ latitude: number; longitude: number; accuracy: number; altitude?: number; speed?: number; heading?: number; timestamp: number; }>
```

**Returns:** <code>Promise&lt;{ latitude: number; longitude: number; accuracy: number; altitude?: number; speed?: number; heading?: number; timestamp: number; }&gt;</code>

--------------------


### clearStoredLocations()

```typescript
clearStoredLocations() => Promise<void>
```

--------------------


### addListener('locationUpdate', ...)

```typescript
addListener(eventName: 'locationUpdate', listenerFunc: (data: LocationData) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                     |
| ------------------ | ------------------------------------------------------------------------ |
| **`eventName`**    | <code>'locationUpdate'</code>                                            |
| **`listenerFunc`** | <code>(data: <a href="#locationdata">LocationData</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('locationStatus', ...)

```typescript
addListener(eventName: 'locationStatus', listenerFunc: (status: { enabled: boolean; }) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                    |
| ------------------ | ------------------------------------------------------- |
| **`eventName`**    | <code>'locationStatus'</code>                           |
| **`listenerFunc`** | <code>(status: { enabled: boolean; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('workHourLocationUpdate', ...)

```typescript
addListener(eventName: 'workHourLocationUpdate', listenerFunc: (data: WorkHourLocationData) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                     |
| ------------------ | ---------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'workHourLocationUpdate'</code>                                                    |
| **`listenerFunc`** | <code>(data: <a href="#workhourlocationdata">WorkHourLocationData</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('workHourLocationUploaded', ...)

```typescript
addListener(eventName: 'workHourLocationUploaded', listenerFunc: (data: { success: boolean; location: WorkHourLocationData; error?: string; }) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                                                                      |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'workHourLocationUploaded'</code>                                                                                                   |
| **`listenerFunc`** | <code>(data: { success: boolean; location: <a href="#workhourlocationdata">WorkHourLocationData</a>; error?: string; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### getLastLocation(...)

```typescript
getLastLocation({ reference }: { reference: string; }) => Promise<void>
```

| Param     | Type                                |
| --------- | ----------------------------------- |
| **`__0`** | <code>{ reference: string; }</code> |

--------------------


### startLocationStatusTracking()

```typescript
startLocationStatusTracking() => Promise<void>
```

--------------------


### stopLocationStatusTracking()

```typescript
stopLocationStatusTracking() => Promise<void>
```

--------------------


### startWorkHourTracking(...)

```typescript
startWorkHourTracking(options: WorkHourTrackingOptions) => Promise<void>
```

| Param         | Type                                                                        |
| ------------- | --------------------------------------------------------------------------- |
| **`options`** | <code><a href="#workhourtrackingoptions">WorkHourTrackingOptions</a></code> |

--------------------


### stopWorkHourTracking()

```typescript
stopWorkHourTracking() => Promise<void>
```

--------------------


### isWorkHourTrackingActive()

```typescript
isWorkHourTrackingActive() => Promise<{ active: boolean; }>
```

**Returns:** <code>Promise&lt;{ active: boolean; }&gt;</code>

--------------------


### getQueuedWorkHourLocations()

```typescript
getQueuedWorkHourLocations() => Promise<{ locations: WorkHourLocationData[]; }>
```

**Returns:** <code>Promise&lt;{ locations: WorkHourLocationData[]; }&gt;</code>

--------------------


### clearQueuedWorkHourLocations()

```typescript
clearQueuedWorkHourLocations() => Promise<void>
```

--------------------


### Interfaces


#### PermissionStatus

| Prop                     | Type                                           |
| ------------------------ | ---------------------------------------------- |
| **`location`**           | <code>'prompt' \| 'denied' \| 'granted'</code> |
| **`backgroundLocation`** | <code>'prompt' \| 'denied' \| 'granted'</code> |
| **`foregroundService`**  | <code>'prompt' \| 'denied' \| 'granted'</code> |


#### LocationData

| Prop                   | Type                |
| ---------------------- | ------------------- |
| **`reference`**        | <code>string</code> |
| **`index`**            | <code>number</code> |
| **`latitude`**         | <code>number</code> |
| **`longitude`**        | <code>number</code> |
| **`altitude`**         | <code>number</code> |
| **`speed`**            | <code>number</code> |
| **`heading`**          | <code>number</code> |
| **`accuracy`**         | <code>number</code> |
| **`altitudeAccuracy`** | <code>number</code> |
| **`totalDistance`**    | <code>number</code> |
| **`timestamp`**        | <code>number</code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### WorkHourLocationData

| Prop                 | Type                |
| -------------------- | ------------------- |
| **`latitude`**       | <code>number</code> |
| **`longitude`**      | <code>number</code> |
| **`accuracy`**       | <code>number</code> |
| **`altitude`**       | <code>number</code> |
| **`speed`**          | <code>number</code> |
| **`heading`**        | <code>number</code> |
| **`timestamp`**      | <code>number</code> |
| **`engineerId`**     | <code>string</code> |
| **`uploadAttempts`** | <code>number</code> |


#### WorkHourTrackingOptions

| Prop                     | Type                 |
| ------------------------ | -------------------- |
| **`engineerId`**         | <code>string</code>  |
| **`uploadInterval`**     | <code>number</code>  |
| **`serverUrl`**          | <code>string</code>  |
| **`authToken`**          | <code>string</code>  |
| **`enableOfflineQueue`** | <code>boolean</code> |

</docgen-api>
