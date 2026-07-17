import Foundation

/// State machine for the progressive-accuracy "current location" request.
///
/// The app asks for the current position with a target accuracy; every incoming fix
/// is streamed live to JavaScript until a fix meets the target (or the timeout
/// expires), at which point the best fix wins. Framework-free (no CoreLocation types)
/// so the accept/finish/timeout behaviour is fully unit-testable. Mirrors the Android
/// plugin's `CurrentLocationWatcher` so both platforms make identical accept/finish
/// decisions given the same fix stream.
final class CurrentLocationWatcher {

    /// What the caller should do with a fix that was just fed in.
    enum Decision {
        /// Stream the fix as a live (non-final) update; keep watching.
        case liveUpdate
        /// Target accuracy met — deliver this fix as final and stop watching.
        case complete
        /// The fix is unusable (invalid coordinates); ignore it.
        case ignore
    }

    static let defaultTimeout: TimeInterval = 30.0

    struct Fix {
        let latitude: Double
        let longitude: Double
        let accuracy: Double
        let altitude: Double
        let speed: Double
        let heading: Double
        let timestamp: Date
    }

    let targetAccuracyMeters: Double
    let timeout: TimeInterval
    private let startedAt: Date
    private let filter: LocationFilter

    private(set) var best: Fix?

    init(targetAccuracyMeters: Double, timeout: TimeInterval, now: Date, filter: LocationFilter = LocationFilter()) {
        self.targetAccuracyMeters = targetAccuracyMeters
        self.timeout = timeout > 0 ? timeout : Self.defaultTimeout
        self.startedAt = now
        self.filter = filter
    }

    /// Feed a fix into the watcher. Tracks the most accurate fix seen so far and
    /// decides whether the watch is done.
    func onFix(_ fix: Fix) -> Decision {
        guard filter.hasValidCoordinates(latitude: fix.latitude, longitude: fix.longitude), fix.accuracy > 0 else {
            return .ignore
        }
        if best == nil || fix.accuracy < best!.accuracy {
            best = fix
        }
        return fix.accuracy <= targetAccuracyMeters ? .complete : .liveUpdate
    }

    func isTimedOut(now: Date) -> Bool {
        now.timeIntervalSince(startedAt) >= timeout
    }
}
