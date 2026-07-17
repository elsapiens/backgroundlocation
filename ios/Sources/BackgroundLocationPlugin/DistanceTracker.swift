import Foundation

/// Accumulates travelled distance across accepted fixes only.
///
/// Fed exclusively by fixes that already passed `LocationFilter` — feeding it a
/// rejected (inaccurate) fix would inflate the total and double-count the next
/// accepted stretch. Pure math, no CoreLocation types, so it is unit-testable and
/// mirrors the Android plugin's `DistanceTracker` (including the haversine formula)
/// so a route's recorded distance does not depend on which platform recorded it.
final class DistanceTracker {

    private static let earthRadiusKm = 6371.0

    private var lastLatitude: Double?
    private var lastLongitude: Double?
    private(set) var totalKm: Double = 0.0

    /// Record an accepted fix and return the running total in kilometers.
    @discardableResult
    func addPoint(latitude: Double, longitude: Double) -> Double {
        if let lastLat = lastLatitude, let lastLon = lastLongitude {
            totalKm += Self.haversineKm(lat1: lastLat, lon1: lastLon, lat2: latitude, lon2: longitude)
        }
        lastLatitude = latitude
        lastLongitude = longitude
        return totalKm
    }

    var hasLastPoint: Bool {
        lastLatitude != nil
    }

    /// Forget everything — used when a new tracking session starts under a new reference.
    func reset() {
        lastLatitude = nil
        lastLongitude = nil
        totalKm = 0.0
    }

    /// Seed the running total (e.g. resuming a session from the database after the
    /// app was relaunched by the system) without adding distance for the seed point.
    func resume(totalKmSoFar: Double, latitude: Double, longitude: Double) {
        totalKm = totalKmSoFar
        lastLatitude = latitude
        lastLongitude = longitude
    }

    static func haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2)
            + cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * sin(dLon / 2) * sin(dLon / 2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }
}
