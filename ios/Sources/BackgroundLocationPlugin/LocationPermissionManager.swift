import CoreLocation
import Foundation

/// Single source of truth for location permission checks and requests.
///
/// Two independent tiers exist on iOS, exactly as on Android, and this class never
/// conflates them:
///  - **Foreground** (`.authorizedWhenInUse` or `.authorizedAlways`) — enough to
///    start tracking. A tracking session begun while the app is active continues to
///    receive updates after backgrounding as long as the process stays alive.
///  - **Background** (`.authorizedAlways`) — needed only so tracking can *resume*
///    after iOS suspends and later relaunches the app in the background (via
///    significant-location-change wake or a background App Refresh), and to enable
///    `allowsBackgroundLocationUpdates` on the tracking managers at all.
///
/// `requestWhenInUseAuthorization()`/`requestAlwaysAuthorization()` complete
/// asynchronously via the CLLocationManager delegate, so every request here takes a
/// completion closure rather than returning synchronously — callers (the plugin)
/// bridge that to a Capacitor `PluginCall` themselves.
final class LocationPermissionManager: NSObject, CLLocationManagerDelegate {

    enum Accuracy: String {
        case fine
        case coarse
        case none
    }

    private let manager = CLLocationManager()
    private var pendingCompletion: ((CLAuthorizationStatus) -> Void)?
    private var timeoutWorkItem: DispatchWorkItem?

    override init() {
        super.init()
        manager.delegate = self
    }

    var authorizationStatus: CLAuthorizationStatus {
        manager.authorizationStatus
    }

    var hasForegroundLocationPermission: Bool {
        authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways
    }

    var hasBackgroundLocationPermission: Bool {
        authorizationStatus == .authorizedAlways
    }

    /// Accuracy tier the user granted (iOS 14+ lets the user downgrade to
    /// "Approximate Location" independent of the authorization tier above).
    var grantedAccuracy: Accuracy {
        guard hasForegroundLocationPermission else { return .none }
        return manager.accuracyAuthorization == .fullAccuracy ? .fine : .coarse
    }

    /// Whether the host app declared the `location` UIBackgroundModes capability.
    /// Missing this is one of two iOS equivalents of the Android foreground-service
    /// crash: setting `allowsBackgroundLocationUpdates = true` without it throws an
    /// uncaught `NSInvalidArgumentException` and kills the app outright. Every call
    /// site in this plugin MUST check this before touching that property.
    var hostAppDeclaresBackgroundLocationMode: Bool {
        guard let modes = Bundle.main.infoDictionary?["UIBackgroundModes"] as? [String] else {
            return false
        }
        return modes.contains("location")
    }

    /// Non-nil when the host app's Info.plist is missing the usage-description key
    /// required to request the given tier. This is the OTHER iOS crash-parity case:
    /// calling `requestWhenInUseAuthorization()`/`requestAlwaysAuthorization()`
    /// without the matching key crashes immediately with "This app has crashed
    /// because it attempted to access privacy-sensitive data without a usage
    /// description." Callers MUST check this and reject with a clear configuration
    /// error instead of ever calling the request method — this is a build-time app
    /// misconfiguration, not something the end user can be prompted about.
    func missingUsageDescriptionKey(forBackground: Bool) -> String? {
        let key = forBackground ? "NSLocationAlwaysAndWhenInUseUsageDescription" : "NSLocationWhenInUseUsageDescription"
        return Bundle.main.object(forInfoDictionaryKey: key) == nil ? key : nil
    }

    /// Request "When In Use" (foreground) authorization. No-ops with the current
    /// status if already determined — iOS never re-shows a dialog once answered.
    func requestForegroundPermission(completion: @escaping (CLAuthorizationStatus) -> Void) {
        guard authorizationStatus == .notDetermined else {
            completion(authorizationStatus)
            return
        }
        awaitAuthorizationChange(completion: completion)
        manager.requestWhenInUseAuthorization()
    }

    /// Request "Always" (background) authorization. Apple only shows the upgrade
    /// prompt when foreground permission is already granted; calling this beforehand
    /// is a no-op that resolves immediately with the current (denied) status.
    func requestBackgroundPermission(completion: @escaping (CLAuthorizationStatus) -> Void) {
        guard hasForegroundLocationPermission, authorizationStatus != .authorizedAlways else {
            completion(authorizationStatus)
            return
        }
        awaitAuthorizationChange(completion: completion)
        manager.requestAlwaysAuthorization()
    }

    /// Waits for `locationManagerDidChangeAuthorization` to fire, with a safety-net
    /// timeout: if the user has permanently denied location for this app in the past
    /// (or Settings-level restrictions apply), iOS may not show any UI at all, and
    /// the delegate callback backing this request would never fire — the timeout
    /// guarantees `completion` still resolves with whatever the status actually is,
    /// instead of leaving the JS promise hanging forever.
    private func awaitAuthorizationChange(completion: @escaping (CLAuthorizationStatus) -> Void) {
        timeoutWorkItem?.cancel()
        pendingCompletion = completion

        let workItem = DispatchWorkItem { [weak self] in
            guard let self, let pending = self.pendingCompletion else { return }
            self.pendingCompletion = nil
            pending(self.authorizationStatus)
        }
        timeoutWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0, execute: workItem)
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        timeoutWorkItem?.cancel()
        let completion = pendingCompletion
        pendingCompletion = nil
        completion?(manager.authorizationStatus)
    }

    /// Device-wide location services toggle. Apple recommends not calling the
    /// underlying check from the main thread since it can touch disk; this always
    /// hops to a background queue before calling back.
    func isLocationServicesEnabled(completion: @escaping (Bool) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async {
            let enabled = CLLocationManager.locationServicesEnabled()
            DispatchQueue.main.async { completion(enabled) }
        }
    }
}
