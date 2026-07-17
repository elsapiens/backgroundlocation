import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Periodically uploads queued work-hour fixes to the configured server.
///
/// Mirrors the Android plugin's `WorkHourLocationUploader`: owns its own
/// `WorkHourLocationQueue` so retries survive independent of the plugin/bridge
/// lifecycle, and wraps each upload attempt in a `UIApplication` background task
/// assertion so a request that begins just before the app suspends is given a grace
/// window (Apple guarantees up to ~30s) to finish instead of being cut off mid-flight.
final class WorkHourLocationUploader {
    private let engineerId: String
    private let uploadInterval: TimeInterval
    private let serverUrl: URL
    private let authToken: String?
    private let enableOfflineQueue: Bool
    private let queue: WorkHourLocationQueue
    private weak var eventSink: BackgroundLocationEventSink?

    private var timer: DispatchSourceTimer?
    private let workQueue = DispatchQueue(label: "com.elsapiens.backgroundlocation.uploader")
    private var isActive = false

    init?(engineerId: String, uploadInterval: TimeInterval, serverUrl: String, authToken: String?,
          enableOfflineQueue: Bool, queue: WorkHourLocationQueue, eventSink: BackgroundLocationEventSink?) {
        guard let url = URL(string: serverUrl) else { return nil }
        self.engineerId = engineerId
        self.uploadInterval = uploadInterval > 0 ? uploadInterval : TrackingStateStore.defaultUploadIntervalSeconds
        self.serverUrl = url
        self.authToken = authToken
        self.enableOfflineQueue = enableOfflineQueue
        self.queue = queue
        self.eventSink = eventSink
    }

    func start() {
        guard !isActive else { return }
        isActive = true
        let timer = DispatchSource.makeTimerSource(queue: workQueue)
        timer.schedule(deadline: .now() + uploadInterval, repeating: uploadInterval)
        timer.setEventHandler { [weak self] in self?.uploadQueuedLocations() }
        timer.resume()
        self.timer = timer
    }

    func stop() {
        isActive = false
        timer?.cancel()
        timer = nil
        // Best-effort final flush so a normal stop does not strand queued fixes.
        uploadQueuedLocations()
    }

    /// Queue a fix. Called from the tracker's CLLocationManagerDelegate callback.
    func addLocationToQueue(latitude: Double, longitude: Double, accuracy: Double, timestamp: Date) {
        let data = WorkHourLocationData(latitude: latitude, longitude: longitude, accuracy: accuracy,
                                         timestamp: timestamp, engineerId: engineerId)
        queue.add(data)
        eventSink?.didQueueWorkHourLocation(data)
    }

    func queueSnapshot() -> [WorkHourLocationData] {
        queue.snapshot()
    }

    func clearQueue() {
        queue.clear()
    }

    private func uploadQueuedLocations() {
        let batch = queue.snapshot()
        guard !batch.isEmpty else { return }

        let taskId = Self.beginBackgroundTask()
        uploadBatch(batch) { [weak self] success in
            // Ending the assertion must not depend on `self` surviving the request —
            // an uploader torn down mid-flight (stop() called, tracker deallocated)
            // must not leak the assertion until the OS forcibly reclaims it.
            defer { Self.endBackgroundTask(taskId) }
            guard let self else { return }
            if success {
                self.queue.removeAll(matching: batch)
                self.eventSink?.didFinishWorkHourUpload(count: batch.count, success: true, error: nil)
            } else if !self.enableOfflineQueue {
                self.queue.removeAll(matching: batch)
                self.eventSink?.didFinishWorkHourUpload(count: batch.count, success: false,
                    error: "Upload failed and offline queue is disabled")
            } else {
                // Left in the queue for retry on the next interval.
                self.eventSink?.didFinishWorkHourUpload(count: batch.count, success: false, error: "Upload failed; retrying")
            }
        }
    }

    private func uploadBatch(_ batch: [WorkHourLocationData], completion: @escaping (Bool) -> Void) {
        var request = URLRequest(url: serverUrl)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("ElsapiensBackgroundLocation/1.0", forHTTPHeaderField: "User-Agent")
        if let authToken, !authToken.isEmpty {
            request.setValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        }
        request.timeoutInterval = 10

        let payload: [String: Any] = [
            "engineerId": engineerId,
            "timestamp": Date().timeIntervalSince1970 * 1000,
            "locations": batch.map { item in
                [
                    "latitude": item.latitude,
                    "longitude": item.longitude,
                    "accuracy": item.accuracy,
                    "timestamp": item.timestamp.timeIntervalSince1970 * 1000,
                ]
            },
        ]
        guard let body = try? JSONSerialization.data(withJSONObject: payload) else {
            completion(false)
            return
        }
        request.httpBody = body

        URLSession.shared.dataTask(with: request) { _, response, error in
            if let error {
                NSLog("[BackgroundLocation] Work hour upload network error: \(error.localizedDescription)")
                completion(false)
                return
            }
            guard let http = response as? HTTPURLResponse else {
                completion(false)
                return
            }
            completion((200..<300).contains(http.statusCode))
        }.resume()
    }

    // MARK: Background task assertion

    #if canImport(UIKit)
    private static func beginBackgroundTask() -> UIBackgroundTaskIdentifier {
        var identifier: UIBackgroundTaskIdentifier = .invalid
        identifier = UIApplication.shared.beginBackgroundTask(withName: "WorkHourLocationUpload") {
            UIApplication.shared.endBackgroundTask(identifier)
        }
        return identifier
    }

    private static func endBackgroundTask(_ identifier: UIBackgroundTaskIdentifier) {
        guard identifier != .invalid else { return }
        UIApplication.shared.endBackgroundTask(identifier)
    }
    #else
    private static func beginBackgroundTask() -> Int { 0 }
    private static func endBackgroundTask(_ identifier: Int) {}
    #endif
}
