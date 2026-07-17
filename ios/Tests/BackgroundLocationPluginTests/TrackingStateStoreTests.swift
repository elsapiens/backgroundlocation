import XCTest
@testable import BackgroundLocationPlugin

/// In-memory `KeyValueStore` so persistence logic is testable without `UserDefaults`.
private final class InMemoryStore: KeyValueStore {
    private var strings: [String: String] = [:]
    private var bools: [String: Bool] = [:]
    private var doubles: [String: Double] = [:]

    func string(forKey key: String) -> String? { strings[key] }
    func set(_ value: String?, forKey key: String) { strings[key] = value }

    func bool(forKey key: String) -> Bool { bools[key] ?? false }
    func set(_ value: Bool, forKey key: String) { bools[key] = value }

    func double(forKey key: String) -> Double { doubles[key] ?? 0 }
    func set(_ value: Double, forKey key: String) { doubles[key] = value }

    func removeObject(forKey key: String) {
        strings.removeValue(forKey: key)
        bools.removeValue(forKey: key)
        doubles.removeValue(forKey: key)
    }
}

final class TrackingStateStoreTests: XCTestCase {

    private func newStore() -> TrackingStateStore {
        TrackingStateStore(store: InMemoryStore())
    }

    func testInactiveByDefault() {
        let store = newStore()
        XCTAssertFalse(store.isTaskTrackingActive)
        XCTAssertFalse(store.isWorkHourTrackingActive)
        XCTAssertNil(store.getTaskTracking())
        XCTAssertNil(store.getWorkHourTracking())
    }

    func testSavedTaskSessionRoundTrips() {
        let store = newStore()
        store.saveTaskTracking(.init(reference: "task_42", interval: 5, minDistance: 15, highAccuracy: false, maxAccuracy: 25))

        XCTAssertTrue(store.isTaskTrackingActive)
        let state = store.getTaskTracking()
        XCTAssertEqual(state?.reference, "task_42")
        XCTAssertEqual(state?.interval, 5)
        XCTAssertEqual(state?.minDistance, 15)
        XCTAssertEqual(state?.highAccuracy, false)
        XCTAssertEqual(state?.maxAccuracy, 25)
    }

    func testClearedTaskSessionYieldsNil() {
        let store = newStore()
        store.saveTaskTracking(.init(reference: "task_42", interval: 5, minDistance: 15, highAccuracy: true, maxAccuracy: 25))
        store.clearTaskTracking()

        XCTAssertFalse(store.isTaskTrackingActive)
        XCTAssertNil(store.getTaskTracking())
    }

    func testActiveFlagWithoutReferenceYieldsNil() {
        // Guards the restart path: an active flag with no reference must not resume
        // tracking into a bogus session.
        let raw = InMemoryStore()
        raw.set(true, forKey: "task_active")
        let store = TrackingStateStore(store: raw)
        XCTAssertNil(store.getTaskTracking())
    }

    func testWorkHourSessionRoundTrips() {
        let store = newStore()
        store.saveWorkHourTracking(.init(engineerId: "eng-7", uploadInterval: 60, serverUrl: "https://api.example.com/loc", authToken: "token123", enableOfflineQueue: false))

        XCTAssertTrue(store.isWorkHourTrackingActive)
        let state = store.getWorkHourTracking()
        XCTAssertEqual(state?.engineerId, "eng-7")
        XCTAssertEqual(state?.uploadInterval, 60)
        XCTAssertEqual(state?.serverUrl, "https://api.example.com/loc")
        XCTAssertEqual(state?.authToken, "token123")
        XCTAssertEqual(state?.enableOfflineQueue, false)
    }

    func testClearedWorkHourSessionYieldsNil() {
        let store = newStore()
        store.saveWorkHourTracking(.init(engineerId: "eng-7", uploadInterval: 60, serverUrl: "https://api.example.com/loc", authToken: nil, enableOfflineQueue: true))
        store.clearWorkHourTracking()

        XCTAssertFalse(store.isWorkHourTrackingActive)
        XCTAssertNil(store.getWorkHourTracking())
    }
}
