import XCTest
@testable import BackgroundLocationPlugin

final class LocationFilterTests: XCTestCase {
    let filter = LocationFilter()

    func testRejectsNullIsland() {
        XCTAssertFalse(filter.hasValidCoordinates(latitude: 0, longitude: 0))
    }

    func testRejectsOutOfRangeCoordinates() {
        XCTAssertFalse(filter.hasValidCoordinates(latitude: 91, longitude: 10))
        XCTAssertFalse(filter.hasValidCoordinates(latitude: -91, longitude: 10))
        XCTAssertFalse(filter.hasValidCoordinates(latitude: 45, longitude: 181))
        XCTAssertFalse(filter.hasValidCoordinates(latitude: 45, longitude: -181))
    }

    func testAcceptsRealCoordinates() {
        XCTAssertTrue(filter.hasValidCoordinates(latitude: 51.5074, longitude: -0.1278)) // London
        XCTAssertTrue(filter.hasValidCoordinates(latitude: -33.8688, longitude: 151.2093)) // Sydney
        XCTAssertTrue(filter.hasValidCoordinates(latitude: 90, longitude: 180)) // Poles/antimeridian valid
    }

    func testAccuracyWithinThresholdAccepted() {
        XCTAssertTrue(filter.meetsAccuracy(10, maxAccuracyMeters: 30))
        XCTAssertTrue(filter.meetsAccuracy(30, maxAccuracyMeters: 30)) // boundary inclusive
    }

    func testAccuracyBeyondThresholdRejected() {
        XCTAssertFalse(filter.meetsAccuracy(31, maxAccuracyMeters: 30))
        XCTAssertFalse(filter.meetsAccuracy(100, maxAccuracyMeters: 30))
    }

    func testNonPositiveAccuracyRejected() {
        // CoreLocation reports negative accuracy for invalid fixes.
        XCTAssertFalse(filter.meetsAccuracy(0, maxAccuracyMeters: 30))
        XCTAssertFalse(filter.meetsAccuracy(-5, maxAccuracyMeters: 30))
    }

    func testFreshFixAccepted() {
        let now = Date(timeIntervalSince1970: 1_000_000)
        XCTAssertTrue(filter.isFresh(fixTime: now.addingTimeInterval(-1), now: now))
        XCTAssertTrue(filter.isFresh(fixTime: now.addingTimeInterval(-LocationFilter.maxFixAgeSeconds), now: now)) // boundary
    }

    func testStaleFixRejected() {
        let now = Date(timeIntervalSince1970: 1_000_000)
        XCTAssertFalse(filter.isFresh(fixTime: now.addingTimeInterval(-LocationFilter.maxFixAgeSeconds - 1), now: now))
    }

    func testShouldRecordCombinesAllRules() {
        let now = Date(timeIntervalSince1970: 1_000_000)
        XCTAssertTrue(filter.shouldRecord(latitude: 51.5, longitude: -0.12, accuracyMeters: 10,
            maxAccuracyMeters: 30, fixTime: now.addingTimeInterval(-1), now: now))
        XCTAssertFalse(filter.shouldRecord(latitude: 0, longitude: 0, accuracyMeters: 10,
            maxAccuracyMeters: 30, fixTime: now.addingTimeInterval(-1), now: now)) // bad coords
        XCTAssertFalse(filter.shouldRecord(latitude: 51.5, longitude: -0.12, accuracyMeters: 50,
            maxAccuracyMeters: 30, fixTime: now.addingTimeInterval(-1), now: now)) // inaccurate
        XCTAssertFalse(filter.shouldRecord(latitude: 51.5, longitude: -0.12, accuracyMeters: 10,
            maxAccuracyMeters: 30, fixTime: now.addingTimeInterval(-60), now: now)) // stale
    }
}
