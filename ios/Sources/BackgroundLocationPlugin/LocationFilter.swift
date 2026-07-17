import Foundation

/// Pure validation rules deciding whether a fix is trustworthy enough to record.
///
/// Framework-free (no CoreLocation types) so the rules are unit-testable and shared by
/// every consumer (task tracking, work-hour tracking, current-location watch). Mirrors
/// the Android plugin's `LocationFilter` exactly, including the 30-second staleness
/// window, so a route recorded on iOS and Android is held to the same standard.
struct LocationFilter {

    /// Fixes older than this are considered stale and rejected.
    static let maxFixAgeSeconds: TimeInterval = 30.0

    /// Basic sanity check on coordinates — rejects "Null Island" (0,0, CoreLocation's
    /// classic placeholder for "no fix yet") and anything outside valid ranges.
    func hasValidCoordinates(latitude: Double, longitude: Double) -> Bool {
        if latitude == 0.0 && longitude == 0.0 {
            return false
        }
        return abs(latitude) <= 90.0 && abs(longitude) <= 180.0
    }

    /// - Parameters:
    ///   - accuracyMeters: reported horizontal accuracy of the fix. CoreLocation
    ///     reports a *negative* value when accuracy is invalid — that must fail here.
    ///   - maxAccuracyMeters: worst acceptable accuracy; fixes above it are dropped.
    func meetsAccuracy(_ accuracyMeters: Double, maxAccuracyMeters: Double) -> Bool {
        accuracyMeters > 0 && accuracyMeters <= maxAccuracyMeters
    }

    func isFresh(fixTime: Date, now: Date) -> Bool {
        now.timeIntervalSince(fixTime) <= Self.maxFixAgeSeconds
    }

    /// Combined check used by the tracking services.
    func shouldRecord(latitude: Double, longitude: Double, accuracyMeters: Double,
                       maxAccuracyMeters: Double, fixTime: Date, now: Date) -> Bool {
        hasValidCoordinates(latitude: latitude, longitude: longitude)
            && meetsAccuracy(accuracyMeters, maxAccuracyMeters: maxAccuracyMeters)
            && isFresh(fixTime: fixTime, now: now)
    }
}
