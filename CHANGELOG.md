# Changelog

All notable changes to the Elsapiens Background Location Plugin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1] - 2026-07-17

### Changed
- Documentation only — no code changes. Android compatibility is presented
  capability-first (tracking works on every supported version; per-version
  differences are one-time grant steps the plugin guides the user through),
  minSdk corrected to 31, architecture diagram updated to the current
  components.

## [0.1.0] - 2026-07-17

### Fixed (crashes)
- **App no longer crashes when the user grants only "While using the app"**: both
  foreground services promote themselves with `startForeground()` FIRST and stop
  gracefully on permission problems. Previously the service stopped itself without
  ever calling `startForeground()`, and Android killed the whole app with
  `ForegroundServiceDidNotStartInTimeException` — repeatedly, via the sticky
  restart and the 30-minute watchdog alarm.
- Restart receivers (`ServiceRestartReceiver`, boot/task-removed paths) now check
  the persisted session state and permissions before attempting a restart, ending
  the background crash loop.
- Work-hour uploader no longer NPE-crashes when the plugin instance is gone
  (app process killed, service restarted standalone).
- `SecurityException` from revoked permissions during tracking is caught and
  surfaced as an `error` event instead of crashing.

### Fixed (correctness)
- **Tracking now works with foreground-only permission**: previously the service
  demanded `ACCESS_BACKGROUND_LOCATION` and silently recorded nothing (and then
  crashed). Background permission is only needed for background restarts and its
  absence is reported, not fatal.
- **Approximate location supported**: permission checks were FINE AND COARSE, so
  the Android 12+ "Approximate" choice disabled tracking entirely; now fine OR
  coarse suffices and the granted accuracy tier is reported.
- **Work-hour tracking actually works in the background**: `startWorkHourTracking`
  never started its foreground service (sampling died with the app process), and
  the uploader always uploaded an empty list — no fix ever reached the server.
- **Duplicate location events fixed**: the service registered a second
  `LocationBroadcastReceiver` in the same process, delivering every fix twice.
- **Duplicate route points fixed**: task tracking ran a second, in-process pipeline
  writing the same fixes to the same SQLite table as the service.
- **Distance no longer inflated**: travelled distance was accumulated before the
  accuracy filter, double-counting stretches around rejected fixes. It is now
  computed over accepted fixes only and resumes correctly after service restarts.
- System restarts no longer corrupt sessions with `default_reference` /
  `auto_restart` references — parameters persist in `TrackingStateStore` and are
  restored on every restart path.
- If the user toggles device location services off mid-session, the service now
  waits and resumes automatically when they are re-enabled.

### Added
- **Typed error contract**: every rejection carries a machine-readable `code`
  (`PERMISSION_DENIED`, `BACKGROUND_PERMISSION_DENIED`,
  `LOCATION_SERVICES_DISABLED`, `SERVICE_START_FAILED`, `LOCATION_UNAVAILABLE`,
  `MISSING_PARAMETER`, `NOT_FOUND`, `CANCELLED`, `INTERNAL_ERROR`), and a new
  `error` event reports asynchronous failures with `source` and `fatal` flags.
- **Progressive-accuracy `getCurrentLocation({ targetAccuracy, timeout })`**:
  streams live fixes as `currentLocation` events until the requested accuracy is
  met; resolves the best fix on timeout. `cancelCurrentLocationRequest()` cancels.
- `requestPermissions({ permissions })` can request the foreground and background
  tiers separately (recommended incremental UX on Android 11+).
- `checkPermissions()` now reports the granted accuracy tier (`fine`/`coarse`/
  `none`) and real `prompt`/`denied` states.
- `getTrackingStatus()` to resync UI with the native session after app restarts.
- `openDeviceLocationSettings()` for the GPS-off case.
- `startTracking` options: `maxAccuracy` (fix filter), `notificationTitle`,
  `notificationText`; resolves `{ backgroundLocationGranted, accuracy }`.
- Bounded, service-owned work-hour upload queue with real batch uploads, retry
  and offline handling; `workHourLocationUploaded` now reports `{ success, count,
  error? }`.
- Unit test suite for the extracted pure logic (filtering, distance, progressive
  accuracy watcher, queue, state store) — 33 tests.
- Web implementation backed by the real W3C Geolocation API, matching the native
  error contract.

### Changed
- Internals reorganised along SOLID lines: `LocationFilter`, `DistanceTracker`,
  `CurrentLocationWatcher`, `WorkHourLocationQueue`, `TrackingStateStore` (behind
  `KeyValueStore`), `NotificationFactory`, `ErrorCodes` — services and plugin
  delegate instead of duplicating logic. `LocationCoordinator` (dead second
  pipeline) removed.
- Plugin manifest trimmed to least privilege: removed `CAMERA`,
  `READ_PHONE_STATE`, WiFi-state and exact-alarm permissions that leaked into
  consuming apps.
- `startLocationStatusTracking()` no longer demands location permission (reading
  the GPS toggle requires none), so an "enable location" banner can be shown
  before any permission prompt.
- Tracking sessions survive the WebView/plugin being destroyed: the plugin no
  longer stops the services in `handleOnDestroy`.

## [Unreleased]

### Added
- Comprehensive documentation suite
- Setup guide with step-by-step integration instructions
- Developer guide with architecture overview
- API documentation with detailed examples
- Work hour tracking with server integration
- Offline queue support for work hour locations
- Intelligent location service coordination
- Modular architecture with separated concerns

### Changed
- Refactored monolithic plugin into modular components
- Improved error handling and validation
- Enhanced battery optimization through intelligent coordination
- Better permission management with granular controls

### Fixed
- Compilation errors with missing imports
- Class naming conflicts during refactoring
- Permission state access modifier issues
- Service lifecycle management issues

## [0.0.17] - 2025-09-18

### Added
- LocationPermissionManager for centralized permission handling
- LocationDataManager for location data processing and validation
- LocationTrackingManager for tracking coordination
- LocationCoordinator for parallel service management
- WorkHourLocationUploader for server communication
- Background location service improvements
- Enhanced location filtering and validation

### Changed
- Separated concerns from monolithic BackgroundLocationPlugin
- Improved code organization and maintainability
- Enhanced location accuracy validation
- Better resource management and cleanup

### Fixed
- Background service permission issues
- Location update coordination problems
- Memory leaks in service management
- Database operation efficiency

## [0.0.16] - Previous Release

### Added
- Basic work hour tracking functionality
- Server upload capabilities
- Offline location queueing

### Changed
- Improved location service stability
- Enhanced permission request flow

### Fixed
- Background service lifecycle issues
- Location permission edge cases

## [0.0.15] - Previous Release

### Added
- Parallel service coordination system
- LocationCoordinator singleton for service management
- Work hour tracking with configurable intervals

### Changed
- Optimized battery usage through service coordination
- Improved location request parameter management

### Fixed
- Multiple service conflicts
- Battery drain from duplicate location requests

## [0.0.14] - Previous Release

### Added
- Enhanced location validation
- Distance calculation improvements
- Better error handling for edge cases

### Changed
- Improved location accuracy filtering
- Enhanced database performance

### Fixed
- Location data inconsistencies
- Database connection management

## [0.0.13] - Previous Release

### Added
- Work hour tracking foundation
- Server communication framework
- Authentication token support

### Changed
- Enhanced plugin architecture
- Improved service management

### Fixed
- Service startup reliability
- Permission handling edge cases

## [0.0.12] - Previous Release

### Added
- Background location service improvements
- Enhanced notification management
- Better foreground service handling

### Changed
- Improved service lifecycle management
- Enhanced error reporting

### Fixed
- Service notification issues
- Background service stability

## [0.0.11] - Previous Release

### Added
- Enhanced permission management
- Better location service status monitoring
- Improved error handling

### Changed
- Updated permission request flow
- Enhanced service coordination

### Fixed
- Permission state synchronization
- Location service detection

## [0.0.10] - Previous Release

### Added
- Basic background location tracking
- SQLite database storage
- Location service monitoring

### Changed
- Improved location accuracy
- Enhanced data persistence

### Fixed
- Location update reliability
- Database initialization

## [0.0.9] - Previous Release

### Added
- Foreground service implementation
- Location permission handling
- Basic location tracking functionality

### Changed
- Enhanced service architecture
- Improved location provider setup

### Fixed
- Service startup issues
- Location provider initialization

## [0.0.8] - Previous Release

### Added
- Initial location tracking implementation
- Basic permission management
- SQLite database integration

### Changed
- Core plugin structure
- Location service framework

### Fixed
- Initial setup and configuration issues

## [0.0.7] - Previous Release

### Added
- Core Capacitor plugin structure
- Basic Android implementation
- Location service foundation

## Earlier Versions

- Initial development and proof of concept
- Basic location tracking experiments
- Plugin architecture design

---

## Migration Guide

### From 0.0.16 to 0.0.17

The plugin has been significantly refactored for better maintainability and performance. While the public API remains largely compatible, there are some internal changes:

#### Breaking Changes
- None for public API users
- Internal class structure has changed (affects custom extensions)

#### New Features
- Modular architecture with separated components
- Enhanced error handling and validation
- Improved battery optimization
- Better documentation and examples

#### Migration Steps
1. Update to version 0.0.17: `npm install elsapiens-background-location@0.0.17`
2. Run `npx cap sync` to update native dependencies
3. Test your existing implementation (API compatibility maintained)
4. Review new documentation for enhanced features
5. Consider adopting new best practices from updated examples

### From Earlier Versions

For migrations from versions prior to 0.0.16, please refer to the specific version notes above and consider reviewing the complete [Setup Guide](SETUP_GUIDE.md) for the most current implementation patterns.

## Development Notes

### Version 0.0.17 Development Focus
- **Architecture**: Complete refactoring from monolithic to modular design
- **Documentation**: Comprehensive documentation suite creation
- **Testing**: Enhanced error handling and edge case coverage
- **Performance**: Battery optimization through intelligent service coordination
- **Maintainability**: Separated concerns for easier development and testing

### Future Roadmap
- **iOS Support**: Native iOS implementation
- **Web Support**: Full web platform support for development
- **Enhanced Analytics**: Built-in location analytics and reporting
- **Geofencing**: Geofence monitoring capabilities
- **Advanced Filtering**: More sophisticated location filtering algorithms

## Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details on:

- How to report bugs
- How to suggest enhancements
- Development setup
- Code style guidelines
- Pull request process

## Support

- **Documentation**: Complete guides available in the repository
- **Issues**: [GitHub Issues](https://github.com/your-org/elsapiens-background-location/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-org/elsapiens-background-location/discussions)
- **Email**: support@elsapiens.com