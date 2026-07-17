import XCTest
@testable import BackgroundLocationPlugin

final class CurrentLocationWatcherTests: XCTestCase {

    private func fix(_ lat: Double, _ lon: Double, _ accuracy: Double) -> CurrentLocationWatcher.Fix {
        CurrentLocationWatcher.Fix(latitude: lat, longitude: lon, accuracy: accuracy, altitude: 0, speed: 0, heading: 0, timestamp: Date())
    }

    func testLiveUpdatesUntilTargetMet() {
        let watcher = CurrentLocationWatcher(targetAccuracyMeters: 10, timeout: 30, now: Date(timeIntervalSince1970: 0))
        XCTAssertEqual(watcher.onFix(fix(51.5, -0.12, 50)), .liveUpdate)
        XCTAssertEqual(watcher.onFix(fix(51.5, -0.12, 25)), .liveUpdate)
        XCTAssertEqual(watcher.onFix(fix(51.5, -0.12, 8)), .complete)
    }

    func testExactTargetAccuracyCompletes() {
        let watcher = CurrentLocationWatcher(targetAccuracyMeters: 10, timeout: 30, now: Date(timeIntervalSince1970: 0))
        XCTAssertEqual(watcher.onFix(fix(51.5, -0.12, 10)), .complete)
    }

    func testKeepsBestFixAcrossUpdates() throws {
        let watcher = CurrentLocationWatcher(targetAccuracyMeters: 5, timeout: 30, now: Date(timeIntervalSince1970: 0))
        _ = watcher.onFix(fix(51.5, -0.12, 50))
        _ = watcher.onFix(fix(51.6, -0.13, 20))
        _ = watcher.onFix(fix(51.7, -0.14, 35)) // worse — must not replace best

        let best = try XCTUnwrap(watcher.best)
        XCTAssertEqual(best.accuracy, 20, accuracy: 1e-6)
        XCTAssertEqual(best.latitude, 51.6, accuracy: 1e-9)
    }

    func testIgnoresInvalidFixes() {
        let watcher = CurrentLocationWatcher(targetAccuracyMeters: 10, timeout: 30, now: Date(timeIntervalSince1970: 0))
        XCTAssertEqual(watcher.onFix(fix(0, 0, 5)), .ignore)
        XCTAssertEqual(watcher.onFix(fix(51.5, -0.12, 0)), .ignore)
        XCTAssertNil(watcher.best)
    }

    func testTimesOutAfterConfiguredDuration() {
        let start = Date(timeIntervalSince1970: 1_000)
        let watcher = CurrentLocationWatcher(targetAccuracyMeters: 10, timeout: 5, now: start)
        XCTAssertFalse(watcher.isTimedOut(now: start.addingTimeInterval(4.999)))
        XCTAssertTrue(watcher.isTimedOut(now: start.addingTimeInterval(5)))
    }

    func testNonPositiveTimeoutFallsBackToDefault() {
        let watcher = CurrentLocationWatcher(targetAccuracyMeters: 10, timeout: 0, now: Date(timeIntervalSince1970: 0))
        XCTAssertEqual(watcher.timeout, CurrentLocationWatcher.defaultTimeout, accuracy: 1e-9)
    }

    func testBestFixAvailableAfterTimeoutEvenWithoutTargetMet() throws {
        let start = Date(timeIntervalSince1970: 0)
        let watcher = CurrentLocationWatcher(targetAccuracyMeters: 5, timeout: 1, now: start)
        _ = watcher.onFix(fix(51.5, -0.12, 40))
        XCTAssertTrue(watcher.isTimedOut(now: start.addingTimeInterval(2)))
        let best = try XCTUnwrap(watcher.best)
        XCTAssertEqual(best.accuracy, 40, accuracy: 1e-6)
    }
}
