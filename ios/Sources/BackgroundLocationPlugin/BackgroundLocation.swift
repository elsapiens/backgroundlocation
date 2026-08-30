import Foundation

/// Owns every native collaborator and wires them together.
///
/// This is the iOS counterpart to the Android plugin's `LocationTrackingManager` +
/// `BackgroundLocationPlugin.load()` initialization combined — but since iOS has no
/// separate service process to coordinate, one orchestrator object is enough. The
/// Capacitor-facing `BackgroundLocationPlugin` class owns exactly one of these and
/// translates bridge calls into calls on it; this class has no Capacitor imports and
/// could be unit-tested or reused outside a Capacitor app entirely.
final class BackgroundLocation {
    let permissions = LocationPermissionManager()
    let database = LocationDatabase()
    let stateStore = TrackingStateStore(store: UserDefaultsKeyValueStore())

    let taskTracker: TaskLocationTracker
    let workHourTracker: WorkHourLocationTracker
    let currentLocationRequester: CurrentLocationRequester
    let geofences: GeofenceMonitor

    weak var eventSink: BackgroundLocationEventSink? {
        didSet {
            taskTracker.eventSink = eventSink
            workHourTracker.eventSink = eventSink
            geofences.eventSink = eventSink
        }
    }

    init() {
        taskTracker = TaskLocationTracker(permissions: permissions, stateStore: stateStore, database: database)
        workHourTracker = WorkHourLocationTracker(permissions: permissions, stateStore: stateStore)
        currentLocationRequester = CurrentLocationRequester(permissions: permissions)
        geofences = GeofenceMonitor(permissions: permissions, store: UserDefaultsKeyValueStore())
    }

    /// Restore any session that was active when the app was last suspended or
    /// terminated. Call once on plugin load (app launch) and again whenever the app
    /// re-enters the foreground, as a defensive resync — both calls are cheap no-ops
    /// when nothing needs resuming.
    func resumeSessionsIfNeeded() {
        taskTracker.resumeIfNeeded()
        workHourTracker.resumeIfNeeded()
    }
}
