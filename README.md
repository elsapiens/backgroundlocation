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

The plugin's own manifest declares everything it needs (permissions, the two
foreground services, and the restart receiver) and Android merges it into your app
automatically — **no manual manifest edits are required**.

For reference, the merged permissions are:

```xml
<!-- Location permissions -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Foreground service (Android 14+ requires the typed permission) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

<!-- Network access for work hour uploads -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

If your app previously declared `com.elsapiens.backgroundlocation.LocationBroadcastReceiver`
or `LocationStateReceiver` in its own manifest, remove those entries — the plugin
registers them at runtime, and duplicate manifest registration causes duplicate events.

## Quick Start

### Permission model (read this first)

Android has **two independent location permission tiers** and they must be requested
in order:

1. **Foreground ("While using the app")** — enough to start tracking. A location
   foreground service started while the app is visible keeps receiving fixes after
   the app is backgrounded.
2. **Background ("Allow all the time")** — needed only so tracking can *recover*
   when the system restarts it while the app is not visible (device reboot,
   watchdog restart, app swiped away). Android grants it on a separate settings
   screen, never in the first dialog.

The plugin **never crashes the app** over permissions: calls reject with a typed
`error.code`, and asynchronous problems (permission revoked mid-session, GPS
switched off) arrive on the `error` event.

### Basic Task Tracking

```typescript
import { BackgroundLocation } from 'elsapiens-background-location';

async function startTaskTracking() {
  // 1. Foreground permission first.
  let status = await BackgroundLocation.checkPermissions();
  if (status.location !== 'granted') {
    status = await BackgroundLocation.requestPermissions({ permissions: ['location'] });
    if (status.location !== 'granted') {
      // 'denied' means Android will not show the dialog again — send the user to settings.
      await BackgroundLocation.openLocationSettings();
      return;
    }
  }

  // 2. Listen for updates and errors.
  BackgroundLocation.addListener('locationUpdate', (location) => {
    console.log('New location:', location.latitude, location.longitude);
    console.log('Distance traveled:', location.totalDistance, 'km');
  });
  BackgroundLocation.addListener('error', (err) => {
    if (err.code === 'BACKGROUND_PERMISSION_DENIED' && !err.fatal) {
      // Tracking still runs — explain, then let the user pick "Allow all the time".
      askUserForAlwaysPermission();
    } else if (err.code === 'LOCATION_SERVICES_DISABLED') {
      BackgroundLocation.openDeviceLocationSettings();
    } else if (err.fatal) {
      console.error('Tracking stopped:', err.message);
    }
  });

  // 3. Start tracking — works with "While using the app" alone.
  const result = await BackgroundLocation.startTracking({
    reference: 'task_123',
    interval: 3000,        // Update every 3 seconds
    minDistance: 10,       // Minimum 10 meters movement
    highAccuracy: true,    // Use GPS for high accuracy
    maxAccuracy: 30,       // Discard fixes worse than 30 m
    notificationTitle: 'Tracking your route',
  });

  // 4. Upgrade to background permission when the user agrees.
  if (!result.backgroundLocationGranted) {
    askUserForAlwaysPermission();
  }
}

async function askUserForAlwaysPermission() {
  // Show your own explanation UI first (required by Play policy), then:
  const status = await BackgroundLocation.requestPermissions({ permissions: ['backgroundLocation'] });
  if (status.backgroundLocation !== 'granted') {
    // Android opens the settings screen; if the user backed out, offer it again later.
  }
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

### Current location with live accuracy refinement

When you need one good fix (e.g. stamping a form), request a target accuracy: the
plugin streams every fix as a `currentLocation` event so the UI can show the
position refining live, and resolves as soon as the target is met.

```typescript
const handle = await BackgroundLocation.addListener('currentLocation', (fix) => {
  updateMapPin(fix.latitude, fix.longitude, fix.accuracy); // live, isFinal=false
});

try {
  // Resolves with the first fix at <= 15 m accuracy; on timeout resolves the best
  // fix seen with timedOut=true; rejects LOCATION_UNAVAILABLE if nothing arrived.
  const fix = await BackgroundLocation.getCurrentLocation({ targetAccuracy: 15, timeout: 20000 });
  console.log('Final fix:', fix.latitude, fix.longitude, `±${fix.accuracy}m`, fix.timedOut);
} finally {
  handle.remove();
}
```

### Error codes

Branch on `error.code` (rejections) and the `error` event — never on message text:

| Code | Meaning | Recommended reaction |
|------|---------|----------------------|
| `PERMISSION_DENIED` | Foreground location permission missing | `requestPermissions({permissions: ['location']})`; if still denied → `openLocationSettings()` |
| `BACKGROUND_PERMISSION_DENIED` | "Allow all the time" missing (non-fatal while tracking runs) | Explain, then `requestPermissions({permissions: ['backgroundLocation']})` |
| `LOCATION_SERVICES_DISABLED` | Device GPS toggle off | `openDeviceLocationSettings()` |
| `MISSING_PARAMETER` | Required option absent | Fix the call site |
| `SERVICE_START_FAILED` | Android refused the foreground service start | Retry from the foreground |
| `LOCATION_UNAVAILABLE` | No usable fix within timeout | Retry / move to open sky |
| `NOT_FOUND` | No stored data for the reference | Treat as empty |
| `CANCELLED` | Request superseded or cancelled | Usually ignorable |
| `INTERNAL_ERROR` | Unexpected native failure | Log and report |

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

### Session Persistence & Recovery
Tracking sessions are persisted natively and recover automatically:
- **Single Pipeline**: Each tracking mode is owned by exactly one foreground service — no duplicate writers
- **Survives Process Death**: Session parameters persist in SharedPreferences; the sticky service and a 30-minute watchdog alarm restore tracking (background permission required for background restarts)
- **GPS Toggle Aware**: If the user disables location services mid-session, the service waits and resumes automatically when they return
- **Accuracy Guarded**: Fixes worse than `maxAccuracy` are discarded, and travelled distance is computed over accepted fixes only

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
* [`requestPermissions(...)`](#requestpermissions)
* [`isLocationServiceEnabled()`](#islocationserviceenabled)
* [`openLocationSettings()`](#openlocationsettings)
* [`openDeviceLocationSettings()`](#opendevicelocationsettings)
* [`startTracking(...)`](#starttracking)
* [`stopTracking()`](#stoptracking)
* [`getTrackingStatus()`](#gettrackingstatus)
* [`getCurrentLocation(...)`](#getcurrentlocation)
* [`cancelCurrentLocationRequest()`](#cancelcurrentlocationrequest)
* [`getStoredLocations(...)`](#getstoredlocations)
* [`clearStoredLocations()`](#clearstoredlocations)
* [`getLastLocation(...)`](#getlastlocation)
* [`startLocationStatusTracking()`](#startlocationstatustracking)
* [`stopLocationStatusTracking()`](#stoplocationstatustracking)
* [`startWorkHourTracking(...)`](#startworkhourtracking)
* [`stopWorkHourTracking()`](#stopworkhourtracking)
* [`isWorkHourTrackingActive()`](#isworkhourtrackingactive)
* [`getQueuedWorkHourLocations()`](#getqueuedworkhourlocations)
* [`clearQueuedWorkHourLocations()`](#clearqueuedworkhourlocations)
* [`addListener('locationUpdate', ...)`](#addlistenerlocationupdate-)
* [`addListener('locationStatus', ...)`](#addlistenerlocationstatus-)
* [`addListener('currentLocation', ...)`](#addlistenercurrentlocation-)
* [`addListener('error', ...)`](#addlistenererror-)
* [`addListener('workHourLocationUpdate', ...)`](#addlistenerworkhourlocationupdate-)
* [`addListener('workHourLocationUploaded', ...)`](#addlistenerworkhourlocationuploaded-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

Background location tracking for Capacitor.

## Permission flow (Android)

```typescript
const status = await BackgroundLocation.checkPermissions();
if (status.location !== 'granted') {
  const after = await BackgroundLocation.requestPermissions({ permissions: ['location'] });
  if (after.location !== 'granted') {
    // Denied — explain, then send the user to settings:
    await BackgroundLocation.openLocationSettings();
    return;
  }
}
const start = await BackgroundLocation.startTracking({ reference: 'task_1' });
if (!start.backgroundLocationGranted) {
  // Tracking runs, but ask for "Allow all the time" so it survives backgrounding:
  await BackgroundLocation.requestPermissions({ permissions: ['backgroundLocation'] });
}
```

Every rejection carries an {@link ErrorCode} in `error.code`; asynchronous
failures arrive through the `error` event. Nothing in this plugin crashes the app
on a permission problem.

### checkPermissions()

```typescript
checkPermissions() => Promise<PermissionStatus>
```

Current state of all location permission tiers, including the accuracy tier
(Android 12+ users may grant approximate location only).

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### requestPermissions(...)

```typescript
requestPermissions(options?: RequestPermissionsOptions | undefined) => Promise<PermissionStatus>
```

Request location permissions. Foreground and background are requested in
sequence as Android requires; see {@link <a href="#requestpermissionsoptions">RequestPermissionsOptions</a>} for
requesting a single tier (recommended UX).

Once a tier reports `denied`, Android will not show the dialog again — use
`openLocationSettings()` and let the user grant it manually.

| Param         | Type                                                                            |
| ------------- | ------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#requestpermissionsoptions">RequestPermissionsOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### isLocationServiceEnabled()

```typescript
isLocationServiceEnabled() => Promise<{ enabled: boolean; }>
```

Whether device location services (the GPS toggle) are enabled.

**Returns:** <code>Promise&lt;{ enabled: boolean; }&gt;</code>

--------------------


### openLocationSettings()

```typescript
openLocationSettings() => Promise<void>
```

Open this app's system settings page — where the user grants a previously
denied permission or upgrades to "Allow all the time".

--------------------


### openDeviceLocationSettings()

```typescript
openDeviceLocationSettings() => Promise<void>
```

Open the device location-services settings — for the GPS-off case.

--------------------


### startTracking(...)

```typescript
startTracking(options: StartTrackingOptions) => Promise<StartTrackingResult>
```

Start recording a route under `reference`.

Requires foreground location permission and enabled location services
(rejects with `PERMISSION_DENIED` / `LOCATION_SERVICES_DISABLED` otherwise).
Missing background permission does NOT reject: tracking starts and the result's
`backgroundLocationGranted: false` (plus a non-fatal `error` event) tells you to
ask the user for "Allow all the time".

The session is persisted natively and survives app and device restarts until
`stopTracking()` is called.

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#starttrackingoptions">StartTrackingOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#starttrackingresult">StartTrackingResult</a>&gt;</code>

--------------------


### stopTracking()

```typescript
stopTracking() => Promise<void>
```

Stop the active tracking session. Idempotent — never rejects when inactive.

--------------------


### getTrackingStatus()

```typescript
getTrackingStatus() => Promise<TrackingStatus>
```

Current native tracking state — useful to resync UI after an app restart.

**Returns:** <code>Promise&lt;<a href="#trackingstatus">TrackingStatus</a>&gt;</code>

--------------------


### getCurrentLocation(...)

```typescript
getCurrentLocation(options?: CurrentLocationOptions | undefined) => Promise<CurrentLocation>
```

Get the device's current position.

With `targetAccuracy` set, fixes stream as `currentLocation` events until one
meets the target (see {@link <a href="#currentlocationoptions">CurrentLocationOptions</a>}) — use this to show a
live "improving accuracy" indicator while waiting for a precise fix.

| Param         | Type                                                                      |
| ------------- | ------------------------------------------------------------------------- |
| **`options`** | <code><a href="#currentlocationoptions">CurrentLocationOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#currentlocation">CurrentLocation</a>&gt;</code>

--------------------


### cancelCurrentLocationRequest()

```typescript
cancelCurrentLocationRequest() => Promise<void>
```

Cancel an in-flight progressive `getCurrentLocation()` request; its promise
rejects with `CANCELLED`.

--------------------


### getStoredLocations(...)

```typescript
getStoredLocations(options: { reference: string; }) => Promise<{ locations: LocationData[]; }>
```

All recorded fixes for a reference, oldest first.

| Param         | Type                                |
| ------------- | ----------------------------------- |
| **`options`** | <code>{ reference: string; }</code> |

**Returns:** <code>Promise&lt;{ locations: LocationData[]; }&gt;</code>

--------------------


### clearStoredLocations()

```typescript
clearStoredLocations() => Promise<void>
```

Delete all stored fixes.

--------------------


### getLastLocation(...)

```typescript
getLastLocation(options: { reference: string; }) => Promise<LocationData>
```

Latest stored fix for a reference. Also re-emits it as a `locationUpdate`
event. Rejects with `NOT_FOUND` when nothing is stored yet.

| Param         | Type                                |
| ------------- | ----------------------------------- |
| **`options`** | <code>{ reference: string; }</code> |

**Returns:** <code>Promise&lt;<a href="#locationdata">LocationData</a>&gt;</code>

--------------------


### startLocationStatusTracking()

```typescript
startLocationStatusTracking() => Promise<void>
```

Start emitting `locationStatus` events when the user toggles device location
services. Needs no permission — safe to call on app start.

--------------------


### stopLocationStatusTracking()

```typescript
stopLocationStatusTracking() => Promise<void>
```

Stop emitting `locationStatus` events.

--------------------


### startWorkHourTracking(...)

```typescript
startWorkHourTracking(options: WorkHourTrackingOptions) => Promise<StartTrackingResult>
```

Start periodic location uploads to `serverUrl` (persists across app kills as a
foreground service). Same permission model as `startTracking()`.

| Param         | Type                                                                        |
| ------------- | --------------------------------------------------------------------------- |
| **`options`** | <code><a href="#workhourtrackingoptions">WorkHourTrackingOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#starttrackingresult">StartTrackingResult</a>&gt;</code>

--------------------


### stopWorkHourTracking()

```typescript
stopWorkHourTracking() => Promise<void>
```

Stop work-hour tracking. Idempotent.

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

Fixes queued for upload, as visible to this app process.

**Returns:** <code>Promise&lt;{ locations: WorkHourLocationData[]; }&gt;</code>

--------------------


### clearQueuedWorkHourLocations()

```typescript
clearQueuedWorkHourLocations() => Promise<void>
```

--------------------


### addListener('locationUpdate', ...)

```typescript
addListener(eventName: 'locationUpdate', listenerFunc: (data: LocationData) => void) => Promise<PluginListenerHandle>
```

A fix was recorded for the active tracking session.

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

Device location services were toggled on/off.

| Param              | Type                                                    |
| ------------------ | ------------------------------------------------------- |
| **`eventName`**    | <code>'locationStatus'</code>                           |
| **`listenerFunc`** | <code>(status: { enabled: boolean; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('currentLocation', ...)

```typescript
addListener(eventName: 'currentLocation', listenerFunc: (data: CurrentLocation) => void) => Promise<PluginListenerHandle>
```

Live fix stream from a progressive `getCurrentLocation()` request.

| Param              | Type                                                                           |
| ------------------ | ------------------------------------------------------------------------------ |
| **`eventName`**    | <code>'currentLocation'</code>                                                 |
| **`listenerFunc`** | <code>(data: <a href="#currentlocation">CurrentLocation</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('error', ...)

```typescript
addListener(eventName: 'error', listenerFunc: (error: PluginError) => void) => Promise<PluginListenerHandle>
```

Asynchronous failure or warning — see {@link <a href="#pluginerror">PluginError</a>}. Listen for
`BACKGROUND_PERMISSION_DENIED` here to know when to ask the user for
"Allow all the time".

| Param              | Type                                                                    |
| ------------------ | ----------------------------------------------------------------------- |
| **`eventName`**    | <code>'error'</code>                                                    |
| **`listenerFunc`** | <code>(error: <a href="#pluginerror">PluginError</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('workHourLocationUpdate', ...)

```typescript
addListener(eventName: 'workHourLocationUpdate', listenerFunc: (data: WorkHourLocationData) => void) => Promise<PluginListenerHandle>
```

A fix entered the work-hour upload queue.

| Param              | Type                                                                                     |
| ------------------ | ---------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'workHourLocationUpdate'</code>                                                    |
| **`listenerFunc`** | <code>(data: <a href="#workhourlocationdata">WorkHourLocationData</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('workHourLocationUploaded', ...)

```typescript
addListener(eventName: 'workHourLocationUploaded', listenerFunc: (data: WorkHourUploadResult) => void) => Promise<PluginListenerHandle>
```

A work-hour upload batch succeeded or failed.

| Param              | Type                                                                                     |
| ------------------ | ---------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'workHourLocationUploaded'</code>                                                  |
| **`listenerFunc`** | <code>(data: <a href="#workhouruploadresult">WorkHourUploadResult</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Remove all listeners registered by this plugin.

--------------------


### Interfaces


#### PermissionStatus

| Prop                     | Type                                                                    | Description                                                                                                                                                                                             |
| ------------------------ | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`location`**           | <code><a href="#permissionstate">PermissionState</a></code>             | Foreground ("while in use") location permission. Granted when fine OR coarse is granted.                                                                                                                |
| **`backgroundLocation`** | <code><a href="#permissionstate">PermissionState</a></code>             | Background ("allow all the time") location permission.                                                                                                                                                  |
| **`accuracy`**           | <code><a href="#locationaccuracylevel">LocationAccuracyLevel</a></code> | Accuracy tier the user granted. `coarse` means approximate location only.                                                                                                                               |
| **`foregroundService`**  | <code>'granted' \| 'denied'</code>                                      | Install-time FOREGROUND_SERVICE_LOCATION permission (Android 14+). `denied` means the host app's AndroidManifest.xml is missing the declaration — an integration error, not something the user can fix. |


#### RequestPermissionsOptions

| Prop              | Type                                                | Description                                                                                                                                                                                                                                                                                                                                                                  |
| ----------------- | --------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`permissions`** | <code>('location' \| 'backgroundLocation')[]</code> | Which permission tiers to request. Defaults to both, requested in the required order: foreground first (system dialog), then background (Android opens the app's location settings where the user picks "Allow all the time"). Best practice: request `['location']` when tracking starts, and request `['backgroundLocation']` separately after explaining why you need it. |


#### StartTrackingResult

| Prop                            | Type                                                                    | Description                                                                                                                                                                                                                     |
| ------------------------------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`backgroundLocationGranted`** | <code>boolean</code>                                                    | false when tracking started with foreground permission only. Tracking works while the service lives, but cannot recover from a background restart — ask the user for "Allow all the time" (see `BACKGROUND_PERMISSION_DENIED`). |
| **`accuracy`**                  | <code><a href="#locationaccuracylevel">LocationAccuracyLevel</a></code> | Accuracy tier tracking runs at. Warn the user when it is `coarse`.                                                                                                                                                              |


#### StartTrackingOptions

| Prop                    | Type                 | Description                                                                                                                            |
| ----------------------- | -------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| **`reference`**         | <code>string</code>  | Identifier the recorded route is stored under (e.g. a task id). Required.                                                              |
| **`interval`**          | <code>number</code>  | Requested update interval in milliseconds. Default 3000.                                                                               |
| **`minDistance`**       | <code>number</code>  | Minimum movement in meters between recorded fixes. Default 10.                                                                         |
| **`highAccuracy`**      | <code>boolean</code> | true (default) = GPS-grade accuracy; false = balanced power.                                                                           |
| **`maxAccuracy`**       | <code>number</code>  | Worst acceptable horizontal accuracy in meters; less accurate fixes are discarded instead of polluting the recorded route. Default 30. |
| **`notificationTitle`** | <code>string</code>  | Title of the persistent tracking notification. Defaults to an English string.                                                          |
| **`notificationText`**  | <code>string</code>  | Body text of the persistent tracking notification.                                                                                     |


#### TrackingStatus

| Prop                     | Type                        | Description                                                          |
| ------------------------ | --------------------------- | -------------------------------------------------------------------- |
| **`isTracking`**         | <code>boolean</code>        | true when a task tracking session is active (survives app restarts). |
| **`isWorkHourTracking`** | <code>boolean</code>        | true when work-hour tracking is active.                              |
| **`reference`**          | <code>string \| null</code> | Reference of the active task session, or null.                       |


#### CurrentLocation

| Prop                 | Type                 | Description                                                               |
| -------------------- | -------------------- | ------------------------------------------------------------------------- |
| **`latitude`**       | <code>number</code>  |                                                                           |
| **`longitude`**      | <code>number</code>  |                                                                           |
| **`accuracy`**       | <code>number</code>  | Horizontal accuracy of this fix in meters.                                |
| **`altitude`**       | <code>number</code>  |                                                                           |
| **`speed`**          | <code>number</code>  |                                                                           |
| **`heading`**        | <code>number</code>  |                                                                           |
| **`timestamp`**      | <code>number</code>  |                                                                           |
| **`isFinal`**        | <code>boolean</code> | true when this fix ended the request (target met or timeout).             |
| **`timedOut`**       | <code>boolean</code> | true when the request timed out before reaching the target accuracy.      |
| **`targetAccuracy`** | <code>number</code>  | Echo of the requested target accuracy (only on `currentLocation` events). |


#### CurrentLocationOptions

| Prop                 | Type                | Description                                                                                                                                                                                                                                                                                                           |
| -------------------- | ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`targetAccuracy`** | <code>number</code> | Desired horizontal accuracy in meters. When set, the plugin keeps requesting fixes and emits each one as a `currentLocation` event (live, so the UI can show the position refining) until a fix meets this accuracy — that fix resolves the promise with `isFinal: true`. When omitted, the first fresh fix resolves. |
| **`timeout`**        | <code>number</code> | Give-up time in milliseconds (default 30000). On timeout the most accurate fix seen so far resolves with `timedOut: true`; if nothing usable arrived the call rejects with `LOCATION_UNAVAILABLE`.                                                                                                                    |


#### LocationData

| Prop                   | Type                | Description                                              |
| ---------------------- | ------------------- | -------------------------------------------------------- |
| **`reference`**        | <code>string</code> |                                                          |
| **`index`**            | <code>number</code> |                                                          |
| **`latitude`**         | <code>number</code> |                                                          |
| **`longitude`**        | <code>number</code> |                                                          |
| **`altitude`**         | <code>number</code> |                                                          |
| **`speed`**            | <code>number</code> |                                                          |
| **`heading`**          | <code>number</code> |                                                          |
| **`accuracy`**         | <code>number</code> |                                                          |
| **`altitudeAccuracy`** | <code>number</code> |                                                          |
| **`totalDistance`**    | <code>number</code> | Total distance travelled in this session, in kilometers. |
| **`timestamp`**        | <code>number</code> |                                                          |


#### WorkHourTrackingOptions

| Prop                     | Type                 | Description                                                                         |
| ------------------------ | -------------------- | ----------------------------------------------------------------------------------- |
| **`engineerId`**         | <code>string</code>  | Identifier sent with every upload. Required.                                        |
| **`uploadInterval`**     | <code>number</code>  | Upload (and sampling) interval in milliseconds. Default 300000 (5 minutes).         |
| **`serverUrl`**          | <code>string</code>  | Endpoint that receives `{engineerId, timestamp, locations: [...]}` POSTs. Required. |
| **`authToken`**          | <code>string</code>  | Sent as a Bearer token in the Authorization header.                                 |
| **`enableOfflineQueue`** | <code>boolean</code> | Keep fixes queued across failed uploads (default true).                             |


#### WorkHourLocationData

| Prop             | Type                |
| ---------------- | ------------------- |
| **`latitude`**   | <code>number</code> |
| **`longitude`**  | <code>number</code> |
| **`accuracy`**   | <code>number</code> |
| **`timestamp`**  | <code>number</code> |
| **`engineerId`** | <code>string</code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### PluginError

Payload of the `error` event.

Asynchronous failures — the tracking service losing its permission, the user
switching GPS off mid-session, a failed service restart — cannot reject a promise,
so they surface here. Fatal errors mean tracking stopped; non-fatal ones are
warnings (e.g. background permission missing while tracking continues in the
foreground).

| Prop          | Type                                                | Description                                       |
| ------------- | --------------------------------------------------- | ------------------------------------------------- |
| **`code`**    | <code><a href="#errorcode">ErrorCode</a></code>     |                                                   |
| **`message`** | <code>string</code>                                 |                                                   |
| **`source`**  | <code><a href="#errorsource">ErrorSource</a></code> |                                                   |
| **`fatal`**   | <code>boolean</code>                                | true when tracking stopped because of this error. |


#### WorkHourUploadResult

| Prop          | Type                 | Description                                         |
| ------------- | -------------------- | --------------------------------------------------- |
| **`success`** | <code>boolean</code> |                                                     |
| **`count`**   | <code>number</code>  | Number of fixes in the uploaded (or dropped) batch. |
| **`error`**   | <code>string</code>  |                                                     |


### Type Aliases


#### PermissionState

<code>'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'</code>


#### LocationAccuracyLevel

Location accuracy tier the user granted (Android 12+ lets users pick "approximate").

<code>'fine' | 'coarse' | 'none'</code>


#### ErrorCode

Machine-readable error codes.

Every rejected call carries one of these in the `code` property of the error, and
every `error` event carries one in its `code` field — branch on the code, never on
the human-readable message.

| Code | Meaning | Recommended app reaction |
|------|---------|--------------------------|
| `PERMISSION_DENIED` | Foreground location permission missing | Call `requestPermissions()`; if state is `denied`, call `openLocationSettings()` |
| `BACKGROUND_PERMISSION_DENIED` | "Allow all the time" missing | Explain why, then `requestPermissions({permissions: ['backgroundLocation']})` or `openLocationSettings()` |
| `LOCATION_SERVICES_DISABLED` | Device GPS toggle is off | Prompt user; `openDeviceLocationSettings()` |
| `MISSING_PARAMETER` | A required option was not provided | Fix the call site |
| `SERVICE_START_FAILED` | Android refused to start the foreground service | Retry from the foreground; check battery restrictions |
| `LOCATION_UNAVAILABLE` | No usable fix within the timeout | Retry, or ask the user to move to open sky |
| `NOT_FOUND` | No stored data for the given reference | Treat as empty |
| `CANCELLED` | Superseded/cancelled request | Usually ignorable |
| `INTERNAL_ERROR` | Unexpected native failure | Log and report |

<code>'PERMISSION_DENIED' | 'BACKGROUND_PERMISSION_DENIED' | 'LOCATION_SERVICES_DISABLED' | 'MISSING_PARAMETER' | 'SERVICE_START_FAILED' | 'LOCATION_UNAVAILABLE' | 'NOT_FOUND' | 'CANCELLED' | 'INTERNAL_ERROR'</code>


#### ErrorSource

Which subsystem raised an asynchronous error event.

<code>'taskTracking' | 'workHourTracking' | 'currentLocation' | 'permissions'</code>

</docgen-api>
