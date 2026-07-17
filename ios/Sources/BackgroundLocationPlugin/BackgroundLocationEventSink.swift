import Foundation

/// Receives events raised by the tracking classes, for the plugin to forward to
/// JavaScript. Plays the same role the Android plugin's `LocationBroadcastReceiver`
/// plays there — a single seam between "native tracking logic" and "Capacitor bridge"
/// — but as a plain Swift delegate protocol rather than an OS broadcast, since iOS has
/// no cross-process broadcast primitive and does not need one here (everything runs in
/// one process).
protocol BackgroundLocationEventSink: AnyObject {
    /// A fix was recorded for the active task tracking session.
    func didRecordLocation(_ item: LocationItem)

    /// Device-wide Location Services toggle changed.
    func didChangeLocationServicesEnabled(_ enabled: Bool)

    /// A fix entered the work-hour upload queue.
    func didQueueWorkHourLocation(_ data: WorkHourLocationData)

    /// A work-hour upload batch finished (successfully or not).
    func didFinishWorkHourUpload(count: Int, success: Bool, error: String?)

    /// An asynchronous failure or warning — see `ErrorCode`/`ErrorSource`.
    func didEmitError(code: ErrorCode, message: String, source: ErrorSource, fatal: Bool)
}
