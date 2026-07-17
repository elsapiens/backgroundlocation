import CoreLocation
import Foundation

/// Drives a single-shot or progressive-accuracy "current location" request.
///
/// Owns its own `CLLocationManager`, independent of the tracking managers, so asking
/// for a quick precise fix never disturbs an in-progress task/work-hour session.
/// Single-shot and progressive-accuracy requests share one code path: a single-shot
/// request is simply a progressive request whose target accuracy no fix could ever
/// fail to meet, so the very first valid fix always completes it. Mirrors the
/// Android plugin's `getCurrentLocation` handling built on `CurrentLocationWatcher`.
final class CurrentLocationRequester: NSObject, CLLocationManagerDelegate {

    typealias FixResult = Result<(fix: CurrentLocationWatcher.Fix, timedOut: Bool), ErrorCode>

    private let manager = CLLocationManager()
    private let permissions: LocationPermissionManager

    private var watcher: CurrentLocationWatcher?
    private var completion: ((FixResult) -> Void)?
    private var onLiveUpdate: ((CurrentLocationWatcher.Fix) -> Void)?
    private var timeoutWorkItem: DispatchWorkItem?

    init(permissions: LocationPermissionManager) {
        self.permissions = permissions
        super.init()
        manager.delegate = self
    }

    func requestSingleShot(timeout: TimeInterval, completion: @escaping (FixResult) -> Void) {
        beginWatch(targetAccuracy: .greatestFiniteMagnitude, timeout: timeout, onLiveUpdate: nil, completion: completion)
    }

    func requestProgressive(targetAccuracy: Double, timeout: TimeInterval,
                             onLiveUpdate: @escaping (CurrentLocationWatcher.Fix) -> Void,
                             completion: @escaping (FixResult) -> Void) {
        beginWatch(targetAccuracy: targetAccuracy, timeout: timeout, onLiveUpdate: onLiveUpdate, completion: completion)
    }

    /// Cancel an in-flight request; its completion resolves with `.cancelled`.
    func cancel() {
        guard watcher != nil else { return }
        let pending = completion
        finish()
        pending?(.failure(.cancelled))
    }

    private func beginWatch(targetAccuracy: Double, timeout: TimeInterval,
                             onLiveUpdate: ((CurrentLocationWatcher.Fix) -> Void)?,
                             completion: @escaping (FixResult) -> Void) {
        guard permissions.hasForegroundLocationPermission else {
            completion(.failure(.permissionDenied))
            return
        }
        // A newer request supersedes whatever is in flight — matches the Android plugin.
        cancel()

        let watcher = CurrentLocationWatcher(targetAccuracyMeters: targetAccuracy, timeout: timeout, now: Date())
        self.watcher = watcher
        self.completion = completion
        self.onLiveUpdate = onLiveUpdate

        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = kCLDistanceFilterNone
        manager.startUpdatingLocation()

        let workItem = DispatchWorkItem { [weak self] in self?.handleTimeout() }
        timeoutWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + watcher.timeout, execute: workItem)
    }

    private func handleTimeout() {
        guard let watcher, watcher.isTimedOut(now: Date()) else { return }
        let pending = completion
        let best = watcher.best
        finish()
        if let best {
            pending?(.success((fix: best, timedOut: true)))
        } else {
            pending?(.failure(.locationUnavailable))
        }
    }

    private func finish() {
        manager.stopUpdatingLocation()
        timeoutWorkItem?.cancel()
        timeoutWorkItem = nil
        watcher = nil
        completion = nil
        onLiveUpdate = nil
    }

    // MARK: CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let watcher else { return }
        for location in locations {
            let fix = CurrentLocationWatcher.Fix(
                latitude: location.coordinate.latitude, longitude: location.coordinate.longitude,
                accuracy: location.horizontalAccuracy, altitude: location.altitude,
                speed: max(0, location.speed), heading: max(0, location.course), timestamp: location.timestamp)

            switch watcher.onFix(fix) {
            case .ignore:
                continue
            case .liveUpdate:
                onLiveUpdate?(fix)
            case .complete:
                let pending = completion
                finish()
                pending?(.success((fix: fix, timedOut: false)))
                return
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard watcher != nil, let clError = error as? CLError, clError.code == .denied else {
            return // Transient — CoreLocation keeps retrying until the timeout fires.
        }
        let pending = completion
        finish()
        pending?(.failure(.permissionDenied))
    }
}
