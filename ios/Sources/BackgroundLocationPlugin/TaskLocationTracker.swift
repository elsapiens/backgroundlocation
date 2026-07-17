import CoreLocation
import Foundation

/// Records the route of the active task using CoreLocation.
///
/// This is the iOS counterpart to the Android plugin's `BackgroundLocationService`,
/// adapted to iOS idioms: there is no separate service process to crash, but there is
/// a direct analog to the crash this plugin's Android side was originally rewritten
/// to fix. Setting `allowsBackgroundLocationUpdates = true` without the host app
/// declaring the `location` UIBackgroundModes capability throws an uncaught
/// `NSInvalidArgumentException` and terminates the app immediately — the iOS sibling
/// of `ForegroundServiceDidNotStartInTimeException`. `beginTracking(...)` guards this
/// exhaustively: that property is only ever touched after confirming both background
/// permission AND the Info.plist capability are present.
///
/// Session parameters persist via `TrackingStateStore` so `resumeIfNeeded()` can
/// restore the exact same session after iOS suspends and later relaunches the app
/// (memory pressure, or a significant-location-change wake) — the iOS equivalent of
/// the Android watchdog alarm restart.
final class TaskLocationTracker: NSObject, CLLocationManagerDelegate {

    struct StartResult {
        let success: Bool
        let code: ErrorCode?
        let message: String
        let backgroundLocationGranted: Bool

        static func success(backgroundLocationGranted: Bool) -> StartResult {
            StartResult(success: true, code: nil, message: "Tracking started", backgroundLocationGranted: backgroundLocationGranted)
        }
        static func failure(_ code: ErrorCode, _ message: String) -> StartResult {
            StartResult(success: false, code: code, message: message, backgroundLocationGranted: false)
        }
    }

    weak var eventSink: BackgroundLocationEventSink?

    private let manager = CLLocationManager()
    private let permissions: LocationPermissionManager
    private let stateStore: TrackingStateStore
    private let database: LocationDatabase
    private let filter = LocationFilter()
    private let distanceTracker = DistanceTracker()

    private var reference: String?
    private var maxAccuracy: Double = TrackingStateStore.defaultMaxAccuracyMeters
    /// Minimum time between recorded fixes, in seconds. CoreLocation (unlike
    /// Android's FusedLocationProvider) has no time-based update-rate request — only
    /// `distanceFilter` — so `interval` from the JS API is honored here as an
    /// explicit gate rather than silently dropped, keeping recorded cadence
    /// comparable across platforms for the same options.
    private var minIntervalSeconds: Double = TrackingStateStore.defaultIntervalSeconds
    private var lastRecordedAt: Date?
    private(set) var isActive = false

    init(permissions: LocationPermissionManager, stateStore: TrackingStateStore, database: LocationDatabase) {
        self.permissions = permissions
        self.stateStore = stateStore
        self.database = database
        super.init()
        manager.delegate = self
    }

    /// Start (or restart, for a new reference) recording a route.
    func start(reference: String, interval: Double, minDistance: Double, highAccuracy: Bool,
               maxAccuracy: Double, completion: @escaping (StartResult) -> Void) {
        let trimmed = reference.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else {
            completion(.failure(.missingParameter, "reference must not be empty"))
            return
        }
        guard permissions.hasForegroundLocationPermission else {
            completion(.failure(.permissionDenied, "Location permission not granted. Call requestPermissions() first."))
            return
        }
        permissions.isLocationServicesEnabled { [weak self] enabled in
            guard let self else { return }
            guard enabled else {
                completion(.failure(.locationServicesDisabled,
                    "Device location services are disabled. Ask the user to enable them "
                        + "(openDeviceLocationSettings() opens the settings screen)."))
                return
            }
            let state = TrackingStateStore.TaskTrackingState(
                reference: trimmed, interval: interval, minDistance: minDistance,
                highAccuracy: highAccuracy, maxAccuracy: maxAccuracy)
            self.stateStore.saveTaskTracking(state)
            self.beginTracking(reference: trimmed, interval: interval, minDistance: minDistance,
                                highAccuracy: highAccuracy, maxAccuracy: maxAccuracy)
            completion(.success(backgroundLocationGranted: self.permissions.hasBackgroundLocationPermission))
        }
    }

    /// Stop the active session. Idempotent.
    func stop() {
        manager.stopUpdatingLocation()
        manager.stopMonitoringSignificantLocationChanges()
        if permissions.hostAppDeclaresBackgroundLocationMode {
            manager.allowsBackgroundLocationUpdates = false
        }
        isActive = false
        reference = nil
        lastRecordedAt = nil
        distanceTracker.reset()
        stateStore.clearTaskTracking()
    }

    var currentReference: String? { reference }

    /// Restore a session that was active when the app was last suspended/terminated.
    /// Safe to call unconditionally (e.g. on app launch and on foreground re-entry) —
    /// it is a no-op when nothing needs resuming.
    func resumeIfNeeded() {
        guard !isActive, let saved = stateStore.getTaskTracking() else { return }
        guard permissions.hasForegroundLocationPermission else { return }
        beginTracking(reference: saved.reference, interval: saved.interval, minDistance: saved.minDistance,
                       highAccuracy: saved.highAccuracy, maxAccuracy: saved.maxAccuracy)
    }

    private func beginTracking(reference: String, interval: Double, minDistance: Double, highAccuracy: Bool, maxAccuracy: Double) {
        if self.reference != reference {
            // New session under a different reference — distance must not carry over.
            distanceTracker.reset()
            lastRecordedAt = nil
        }
        self.reference = reference
        self.maxAccuracy = maxAccuracy
        self.minIntervalSeconds = interval > 0 ? interval : TrackingStateStore.defaultIntervalSeconds

        manager.desiredAccuracy = highAccuracy ? kCLLocationAccuracyBest : kCLLocationAccuracyHundredMeters
        manager.distanceFilter = minDistance
        manager.pausesLocationUpdatesAutomatically = false
        manager.activityType = .other

        if permissions.hasBackgroundLocationPermission && permissions.hostAppDeclaresBackgroundLocationMode {
            manager.allowsBackgroundLocationUpdates = true
            manager.showsBackgroundLocationIndicator = true
            // Safety net: if the OS terminates this process under memory pressure, a
            // subsequent 500m+ move relaunches the app in the background so
            // `resumeIfNeeded()` (called from app launch) can restore full tracking.
            manager.startMonitoringSignificantLocationChanges()
        } else {
            // Do NOT set allowsBackgroundLocationUpdates here — see class doc. Tracking
            // still proceeds in the foreground; only the background-recovery path is
            // unavailable, exactly mirroring the Android non-fatal warning.
            if !permissions.hasBackgroundLocationPermission {
                eventSink?.didEmitError(code: .backgroundPermissionDenied,
                    message: "Background location permission (\"Always\") is not granted. Tracking continues "
                        + "while the app is active, but cannot resume automatically if iOS suspends it.",
                    source: .taskTracking, fatal: false)
            } else {
                eventSink?.didEmitError(code: .serviceStartFailed,
                    message: "The host app's Info.plist is missing the \"location\" UIBackgroundModes capability. "
                        + "Background location permission is granted, but tracking cannot continue once the app "
                        + "is suspended until this is added to the app's Signing & Capabilities.",
                    source: .taskTracking, fatal: false)
            }
        }

        manager.startUpdatingLocation()
        isActive = true
        resumeDistanceFromDatabase()
    }

    private func resumeDistanceFromDatabase() {
        guard !distanceTracker.hasLastPoint, let reference else { return }
        if let last = database.getLastLocation(forReference: reference) {
            distanceTracker.resume(
                totalKmSoFar: database.getTotalDistanceKm(forReference: reference),
                latitude: last.latitude, longitude: last.longitude)
        }
    }

    // MARK: CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let reference else { return }
        let now = Date()
        for location in locations {
            handle(location, reference: reference, now: now)
        }
    }

    private func handle(_ location: CLLocation, reference: String, now: Date) {
        guard filter.shouldRecord(
            latitude: location.coordinate.latitude, longitude: location.coordinate.longitude,
            accuracyMeters: location.horizontalAccuracy, maxAccuracyMeters: maxAccuracy,
            fixTime: location.timestamp, now: now) else {
            return
        }
        if let lastRecordedAt, now.timeIntervalSince(lastRecordedAt) < minIntervalSeconds {
            return
        }
        lastRecordedAt = now

        let totalKm = distanceTracker.addPoint(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude)
        let index = database.getNextIndex(forReference: reference)
        let speed = max(0, location.speed)
        let heading = max(0, location.course)

        database.insertLocation(
            reference: reference, index: index,
            latitude: location.coordinate.latitude, longitude: location.coordinate.longitude,
            altitude: location.altitude, accuracy: location.horizontalAccuracy,
            speed: speed, heading: heading, altitudeAccuracy: location.verticalAccuracy,
            timestamp: location.timestamp)

        eventSink?.didRecordLocation(LocationItem(
            reference: reference, index: index,
            latitude: location.coordinate.latitude, longitude: location.coordinate.longitude,
            altitude: location.altitude, accuracy: location.horizontalAccuracy,
            speed: speed, heading: heading, altitudeAccuracy: location.verticalAccuracy,
            totalDistance: totalKm, timestamp: location.timestamp))
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard let clError = error as? CLError, clError.code == .denied else {
            return // Transient (.locationUnknown, .network, ...) — CoreLocation keeps retrying.
        }
        permissions.isLocationServicesEnabled { [weak self] enabled in
            guard let self else { return }
            if !enabled {
                self.eventSink?.didChangeLocationServicesEnabled(false)
                self.eventSink?.didEmitError(code: .locationServicesDisabled,
                    message: "Device location services were disabled during tracking. Waiting for them to be re-enabled.",
                    source: .taskTracking, fatal: false)
                // Deliberately not calling stop(): CoreLocation resumes delivering
                // updates on this same request once services are re-enabled.
            } else {
                self.eventSink?.didEmitError(code: .permissionDenied,
                    message: "Location permission was revoked while tracking.", source: .taskTracking, fatal: true)
                self.stop()
            }
        }
    }
}
