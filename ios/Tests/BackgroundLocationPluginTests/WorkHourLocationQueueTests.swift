import XCTest
@testable import BackgroundLocationPlugin

final class WorkHourLocationQueueTests: XCTestCase {

    private func data(_ timestamp: TimeInterval) -> WorkHourLocationData {
        WorkHourLocationData(latitude: 51.5, longitude: -0.12, accuracy: 10, timestamp: Date(timeIntervalSince1970: timestamp), engineerId: "eng-1")
    }

    func testAddAndSnapshotPreservesOrder() {
        let queue = WorkHourLocationQueue(capacity: 10)
        queue.add(data(1))
        queue.add(data(2))
        queue.add(data(3))

        let snapshot = queue.snapshot()
        XCTAssertEqual(snapshot.count, 3)
        XCTAssertEqual(snapshot[0].timestamp.timeIntervalSince1970, 1)
        XCTAssertEqual(snapshot[2].timestamp.timeIntervalSince1970, 3)
    }

    func testEvictsOldestWhenFull() {
        let queue = WorkHourLocationQueue(capacity: 3)
        for t in stride(from: 1, through: 5, by: 1) {
            queue.add(data(TimeInterval(t)))
        }
        let snapshot = queue.snapshot()
        XCTAssertEqual(snapshot.count, 3)
        XCTAssertEqual(snapshot[0].timestamp.timeIntervalSince1970, 3) // 1 and 2 evicted
        XCTAssertEqual(snapshot[2].timestamp.timeIntervalSince1970, 5)
    }

    func testRemoveAllRemovesOnlyUploadedBatch() {
        let queue = WorkHourLocationQueue(capacity: 10)
        queue.add(data(1))
        queue.add(data(2))

        let batch = queue.snapshot()
        queue.add(data(3)) // Arrives while the batch is "uploading"

        queue.removeAll(matching: batch)
        XCTAssertEqual(queue.count, 1)
        XCTAssertEqual(queue.snapshot()[0].timestamp.timeIntervalSince1970, 3)
    }

    func testClearEmptiesQueue() {
        let queue = WorkHourLocationQueue(capacity: 10)
        queue.add(data(1))
        queue.clear()
        XCTAssertTrue(queue.isEmpty)
    }
}
