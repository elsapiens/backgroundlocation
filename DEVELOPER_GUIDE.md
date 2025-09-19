# Background Location Plugin - Developer Guide

## Architecture Overview

The Background Location Plugin is designed with a modular architecture that separates concerns and provides maintainable, testable code. The plugin has been refactored from a single monolithic class into specialized components.

### Core Architecture Principles

1. **Separation of Concerns**: Each component has a single, well-defined responsibility
2. **Dependency Injection**: Components are loosely coupled and can be easily tested
3. **Event-Driven Communication**: Components communicate through events and callbacks
4. **Resource Management**: Automatic cleanup and efficient resource usage
5. **Error Resilience**: Comprehensive error handling and graceful degradation

## Component Structure

```
BackgroundLocationPlugin (Main Entry Point)
├── LocationPermissionManager (Permission Handling)
├── LocationDataManager (Data Processing & Storage)
├── LocationTrackingManager (Tracking Coordination)
└── LocationCoordinator (Service Coordination)
```

### BackgroundLocationPlugin (Main Entry Point)

The main plugin class that implements the Capacitor plugin interface. It acts as a facade that delegates functionality to specialized manager classes.

**Responsibilities:**
- Implement Capacitor plugin methods
- Route calls to appropriate manager classes
- Handle plugin lifecycle events
- Manage component initialization

**Key Features:**
- Clean API surface with organized method groups
- Consistent error handling across all endpoints
- Thread-safe operations
- Resource cleanup on plugin destruction

### LocationPermissionManager

Handles all location permission-related functionality in a centralized, reusable way.

**Responsibilities:**
- Check current permission states
- Request permissions from users
- Validate permissions for specific operations
- Handle permission-related errors

**Key Features:**
- Comprehensive permission checking for all Android location permissions
- Smart permission request flow (foreground → background)
- Operation-specific permission validation
- Clear permission state reporting

### LocationDataManager

Manages all location data processing, validation, and persistence.

**Responsibilities:**
- Process raw location updates
- Validate location data quality
- Calculate distances and movement
- Store/retrieve locations from SQLite database
- Convert between data formats

**Key Features:**
- Intelligent location filtering (accuracy, distance, time)
- Efficient database operations with batch processing
- Distance calculation with running totals
- Data validation and error handling
- Memory-efficient location processing

### LocationTrackingManager

Coordinates different types of location tracking and manages their lifecycle.

**Responsibilities:**
- Manage task-based tracking sessions
- Coordinate work hour tracking
- Interface with LocationCoordinator for service management
- Handle tracking configuration and state

**Key Features:**
- Simultaneous tracking mode support
- Configuration management
- State tracking and validation
- Integration with service coordination
- Event emission for location updates

### LocationCoordinator

Singleton service that coordinates multiple location requests to optimize battery usage and prevent conflicts.

**Responsibilities:**
- Manage FusedLocationProviderClient instances
- Coordinate location request parameters
- Distribute location updates to multiple consumers
- Optimize battery usage through intelligent batching

**Key Features:**
- Single location source for multiple consumers
- Intelligent parameter merging (highest accuracy, shortest interval)
- Automatic service lifecycle management
- Thread-safe multi-consumer support

## Data Flow

### Task Tracking Flow

```
1. User calls startTracking()
   ↓
2. BackgroundLocationPlugin validates parameters
   ↓
3. LocationPermissionManager checks permissions
   ↓
4. LocationTrackingManager configures tracking
   ↓
5. LocationCoordinator starts location services
   ↓
6. Location updates flow through:
   LocationCoordinator → LocationTrackingManager → LocationDataManager
   ↓
7. LocationDataManager processes and stores locations
   ↓
8. Events emitted to JavaScript layer
```

### Work Hour Tracking Flow

```
1. User calls startWorkHourTracking()
   ↓
2. BackgroundLocationPlugin validates parameters
   ↓
3. LocationPermissionManager checks permissions
   ↓
4. LocationTrackingManager configures work hour tracking
   ↓
5. LocationCoordinator starts location services
   ↓
6. Location updates processed and queued
   ↓
7. WorkHourLocationUploader uploads to server periodically
   ↓
8. Offline queue management for failed uploads
```

## Database Schema

The plugin uses SQLite for local data storage with the following schema:

### locations table
```sql
CREATE TABLE locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    reference TEXT NOT NULL,
    location_index INTEGER NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    altitude REAL,
    accuracy REAL NOT NULL,
    speed REAL,
    heading REAL,
    altitude_accuracy REAL,
    total_distance REAL,
    timestamp INTEGER NOT NULL
);
```

### work_hour_locations table
```sql
CREATE TABLE work_hour_locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    engineer_id TEXT NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    accuracy REAL NOT NULL,
    altitude REAL,
    speed REAL,
    heading REAL,
    timestamp INTEGER NOT NULL,
    uploaded INTEGER DEFAULT 0
);
```

## Extension Guidelines

### Adding New Tracking Modes

To add a new tracking mode to the plugin:

1. **Define the tracking mode** in LocationTrackingManager:
```java
public class LocationTrackingManager {
    public void startCustomTracking(CustomTrackingConfig config) {
        // Validate configuration
        // Register with LocationCoordinator
        // Set up event handling
    }
}
```

2. **Add configuration handling** in the main plugin:
```java
@PluginMethod
public void startCustomTracking(PluginCall call) {
    // Extract parameters
    // Validate input
    // Delegate to LocationTrackingManager
    // Handle errors and responses
}
```

3. **Update LocationCoordinator** if new location parameters are needed:
```java
public void registerCustomConsumer(String consumerId, CustomLocationRequest request) {
    // Add new parameter types to optimal calculation
    // Update location request merging logic
}
```

### Adding New Data Processing

To add new location data processing logic:

1. **Extend LocationDataManager**:
```java
public void processCustomLocationData(Location location, CustomConfig config) {
    // Add custom validation logic
    // Implement custom processing
    // Store results appropriately
}
```

2. **Update database schema** if needed:
```sql
-- Add new columns to existing tables or create new tables
ALTER TABLE locations ADD COLUMN custom_field REAL;
```

3. **Add corresponding API methods** in the main plugin.

### Adding New Permissions

To add new permission types:

1. **Update LocationPermissionManager**:
```java
public boolean hasCustomPermission() {
    // Check custom permission
}

public void requestCustomPermission(Activity activity, int requestCode) {
    // Request custom permission
}
```

2. **Update permission constants** and add to overall permission checks.

## Development Best Practices

### Code Organization

1. **Single Responsibility**: Each class should have one clear purpose
2. **Dependency Injection**: Pass dependencies through constructors
3. **Interface Segregation**: Use interfaces for testability
4. **Error Handling**: Always handle errors gracefully with meaningful messages

### Testing Guidelines

1. **Unit Testing**: Test each component in isolation
2. **Integration Testing**: Test component interactions
3. **Mock Dependencies**: Use mock objects for external dependencies
4. **Error Scenarios**: Test error conditions and edge cases

Example test structure:
```java
@Test
public void testLocationDataManager_validLocation_shouldStoreLocation() {
    // Arrange
    LocationDataManager manager = new LocationDataManager(mockDatabase);
    Location testLocation = createValidTestLocation();
    
    // Act
    boolean result = manager.processLocationUpdate(testLocation, "test_ref");
    
    // Assert
    assertTrue(result);
    verify(mockDatabase).insertLocation(any());
}
```

### Performance Considerations

1. **Background Thread Usage**: Perform heavy operations off the main thread
2. **Database Optimization**: Use batch operations and indexed queries
3. **Memory Management**: Clean up resources and avoid memory leaks
4. **Battery Optimization**: Minimize location request frequency when possible

### Debugging

1. **Comprehensive Logging**: Use consistent logging throughout the codebase
2. **Error Context**: Include relevant context in error messages
3. **State Validation**: Validate component state at critical points
4. **Debugging Hooks**: Provide methods to inspect internal state

Example logging:
```java
Log.d(TAG, "Starting location tracking - Reference: " + reference + 
           ", Interval: " + interval + "ms, Accuracy: " + (highAccuracy ? "HIGH" : "BALANCED"));
```

## Configuration Management

### Plugin Configuration

The plugin supports various configuration options that can be set at different levels:

1. **Default Configuration**: Built-in sensible defaults
2. **Global Configuration**: Plugin-wide settings
3. **Operation Configuration**: Per-operation customization

Example configuration structure:
```java
public class LocationConfig {
    // Default values
    public static final int DEFAULT_INTERVAL = 3000;
    public static final int DEFAULT_MIN_DISTANCE = 10;
    public static final boolean DEFAULT_HIGH_ACCURACY = true;
    
    // Instance configuration
    private int interval = DEFAULT_INTERVAL;
    private int minDistance = DEFAULT_MIN_DISTANCE;
    private boolean highAccuracy = DEFAULT_HIGH_ACCURACY;
}
```

### Environment-Specific Settings

For different environments (development, staging, production), consider:

1. **Build-time Configuration**: Use Android build variants
2. **Runtime Configuration**: Read from plugin initialization
3. **Debug Features**: Enable additional logging and validation in debug builds

## Security Considerations

### Data Protection

1. **Local Storage**: Location data is stored in app-private SQLite database
2. **Network Transmission**: Use HTTPS for all server communications
3. **Authentication**: Support token-based authentication for server uploads
4. **Data Retention**: Implement data cleanup policies

### Permission Handling

1. **Minimal Permissions**: Request only necessary permissions
2. **Permission Education**: Provide clear explanations for permission requests
3. **Graceful Degradation**: Handle permission denial gracefully
4. **Permission Re-validation**: Check permissions before sensitive operations

### Privacy Best Practices

1. **User Consent**: Always obtain user consent before tracking
2. **Data Minimization**: Collect only necessary location data
3. **Anonymization**: Consider anonymizing data when possible
4. **User Control**: Provide clear controls to start/stop tracking

## Troubleshooting

### Common Development Issues

1. **Permission Errors**
   - Verify all required permissions are declared in AndroidManifest.xml
   - Check permission request flow is implemented correctly
   - Test permission handling on different Android versions

2. **Location Updates Not Received**
   - Verify LocationCoordinator is properly initialized
   - Check FusedLocationProviderClient configuration
   - Ensure device location services are enabled
   - Test with device outdoors for GPS signal

3. **Database Errors**
   - Check SQLite database initialization
   - Verify table schemas are correct
   - Handle database version migrations properly
   - Test database operations on different Android versions

4. **Service Lifecycle Issues**
   - Ensure proper service registration/unregistration
   - Handle activity lifecycle events correctly
   - Test service behavior during app backgrounding
   - Verify notification permissions for foreground services

### Performance Issues

1. **High Battery Usage**
   - Review location request intervals and accuracy settings
   - Check for proper service cleanup when tracking stops
   - Verify LocationCoordinator is optimizing requests correctly
   - Consider reducing location accuracy for long-term tracking

2. **Memory Leaks**
   - Ensure all listeners are properly removed
   - Check for static references to activity contexts
   - Verify database connections are closed
   - Test with memory profiling tools

3. **Slow Database Operations**
   - Use database indices on frequently queried columns
   - Implement batch operations for multiple inserts
   - Consider database cleanup for old data
   - Profile database query performance

## Migration Guide

### From Monolithic to Modular Architecture

If you have customizations in the original BackgroundLocationPlugin.java, here's how to migrate:

1. **Identify Customizations**: Review what changes were made to the original plugin
2. **Map to Components**: Determine which new component should contain each customization
3. **Extract Logic**: Move custom logic to appropriate manager classes
4. **Update API**: Ensure custom functionality is exposed through plugin API
5. **Test Integration**: Verify all customizations still work correctly

### Example Migration

Original monolithic code:
```java
// Old way - everything in one class
public class BackgroundLocationPlugin extends Plugin {
    private void someCustomLocationProcessing(Location location) {
        // Custom processing logic mixed with plugin logic
    }
}
```

New modular approach:
```java
// New way - separated concerns
public class CustomLocationDataManager extends LocationDataManager {
    @Override
    public void processLocationUpdate(Location location, String reference) {
        // Custom processing logic in dedicated component
        super.processLocationUpdate(location, reference);
        someCustomLocationProcessing(location);
    }
    
    private void someCustomLocationProcessing(Location location) {
        // Custom logic isolated in appropriate component
    }
}
```

## Contributing

### Development Setup

1. **Clone Repository**: Get the latest plugin source code
2. **Android Studio**: Open the plugin in Android Studio
3. **Dependencies**: Ensure all dependencies are properly resolved
4. **Build**: Verify the plugin builds without errors
5. **Testing**: Run existing tests to ensure everything works

### Code Style

1. **Java Conventions**: Follow standard Java coding conventions
2. **Naming**: Use descriptive names for classes, methods, and variables
3. **Documentation**: Document all public methods and complex logic
4. **Formatting**: Use consistent code formatting throughout

### Pull Request Guidelines

1. **Single Purpose**: Each PR should address a single feature or bug
2. **Testing**: Include tests for new functionality
3. **Documentation**: Update documentation for API changes
4. **Backwards Compatibility**: Maintain backwards compatibility when possible

## Future Enhancements

### Planned Features

1. **iOS Support**: Extend plugin to support iOS platform
2. **Web Implementation**: Add web platform support for development/testing
3. **Enhanced Filtering**: More sophisticated location filtering algorithms
4. **Geofencing**: Add geofence monitoring capabilities
5. **Analytics**: Built-in location analytics and reporting

### Extension Points

The modular architecture provides several extension points for future enhancements:

1. **LocationDataManager**: Add new data processing algorithms
2. **LocationTrackingManager**: Support additional tracking modes
3. **LocationCoordinator**: Optimize for specific use cases
4. **Database Layer**: Support different storage backends

### API Evolution

As the plugin evolves, we aim to:

1. **Maintain Compatibility**: Preserve existing API contracts
2. **Deprecation Strategy**: Provide clear migration paths for deprecated features
3. **Version Management**: Use semantic versioning for releases
4. **Feature Flags**: Allow gradual rollout of new features

This developer guide should help you understand the plugin architecture and contribute effectively to its development. For specific implementation details, refer to the source code and API documentation.