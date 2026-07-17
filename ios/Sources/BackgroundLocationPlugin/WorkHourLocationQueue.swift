import Foundation

/// A fix awaiting upload as part of work-hour tracking.
struct WorkHourLocationData {
    let latitude: Double
    let longitude: Double
    let accuracy: Double
    let timestamp: Date
    let engineerId: String
}

/// Bounded, thread-safe queue of work-hour fixes awaiting upload.
///
/// Owned by `WorkHourLocationUploader` (not the plugin instance) so it keeps working
/// when the WebView/plugin bridge is torn down — e.g. after the app was suspended and
/// later relaunched in the background by iOS. Bounded so a long offline stretch cannot
/// grow memory without limit: when full, the oldest entries are evicted first (the
/// newest data is the most valuable for "where is the engineer now" dashboards).
/// Mirrors the Android plugin's `WorkHourLocationQueue`.
final class WorkHourLocationQueue {

    static let defaultCapacity = 500

    private let capacity: Int
    private var items: [WorkHourLocationData] = []
    private let lock = NSLock()

    init(capacity: Int = WorkHourLocationQueue.defaultCapacity) {
        self.capacity = max(1, capacity)
    }

    /// Add a fix, evicting the oldest entries when the queue is full.
    func add(_ data: WorkHourLocationData) {
        lock.lock()
        defer { lock.unlock() }
        while items.count >= capacity {
            items.removeFirst()
        }
        items.append(data)
    }

    /// Copy of the current contents, oldest first.
    func snapshot() -> [WorkHourLocationData] {
        lock.lock()
        defer { lock.unlock() }
        return items
    }

    /// Remove entries that were uploaded successfully, matched by identity (timestamp
    /// + coordinates), since `WorkHourLocationData` is a value type without an id.
    func removeAll(matching uploaded: [WorkHourLocationData]) {
        lock.lock()
        defer { lock.unlock() }
        let uploadedKeys = Set(uploaded.map(Self.key))
        items.removeAll { uploadedKeys.contains(Self.key($0)) }
    }

    var count: Int {
        lock.lock()
        defer { lock.unlock() }
        return items.count
    }

    var isEmpty: Bool {
        count == 0
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        items.removeAll()
    }

    private static func key(_ item: WorkHourLocationData) -> String {
        "\(item.timestamp.timeIntervalSince1970)_\(item.latitude)_\(item.longitude)"
    }
}
