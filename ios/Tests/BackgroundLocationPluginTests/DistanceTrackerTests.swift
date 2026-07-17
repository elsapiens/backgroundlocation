import XCTest
@testable import BackgroundLocationPlugin

final class DistanceTrackerTests: XCTestCase {

    func testFirstPointAddsNoDistance() {
        let tracker = DistanceTracker()
        XCTAssertEqual(tracker.addPoint(latitude: 51.5074, longitude: -0.1278), 0, accuracy: 1e-9)
        XCTAssertTrue(tracker.hasLastPoint)
    }

    func testAccumulatesKnownDistance() {
        let tracker = DistanceTracker()
        // London -> Paris is ~343.5 km great-circle.
        tracker.addPoint(latitude: 51.5074, longitude: -0.1278)
        let total = tracker.addPoint(latitude: 48.8566, longitude: 2.3522)
        XCTAssertEqual(total, 343.5, accuracy: 2.0)
    }

    func testSequentialPointsDoNotDoubleCount() {
        // The tracker only ever receives accepted points, so feeding A then B
        // directly must equal the distance A->B.
        let tracker = DistanceTracker()
        tracker.addPoint(latitude: 51.5074, longitude: -0.1278)
        let direct = tracker.addPoint(latitude: 51.5174, longitude: -0.1278) // ~1.11 km north
        XCTAssertEqual(direct, 1.11, accuracy: 0.02)
    }

    func testResetForgetsEverything() {
        let tracker = DistanceTracker()
        tracker.addPoint(latitude: 51.5, longitude: -0.12)
        tracker.addPoint(latitude: 51.6, longitude: -0.12)
        tracker.reset()
        XCTAssertFalse(tracker.hasLastPoint)
        XCTAssertEqual(tracker.totalKm, 0, accuracy: 1e-9)
    }

    func testResumeSeedsTotalWithoutAddingDistance() {
        let tracker = DistanceTracker()
        tracker.resume(totalKmSoFar: 12.5, latitude: 51.5074, longitude: -0.1278)
        XCTAssertEqual(tracker.totalKm, 12.5, accuracy: 1e-9)
        let total = tracker.addPoint(latitude: 51.5174, longitude: -0.1278)
        XCTAssertEqual(total, 12.5 + 1.11, accuracy: 0.02)
    }

    func testHaversineZeroForSamePoint() {
        XCTAssertEqual(DistanceTracker.haversineKm(lat1: 51.5, lon1: -0.12, lat2: 51.5, lon2: -0.12), 0, accuracy: 1e-9)
    }
}
