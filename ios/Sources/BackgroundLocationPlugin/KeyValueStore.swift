import Foundation

/// Minimal key-value persistence abstraction.
///
/// `TrackingStateStore` depends on this protocol instead of `UserDefaults` directly so
/// its logic can be unit-tested with an in-memory implementation (Dependency
/// Inversion), matching the Android plugin's `KeyValueStore` interface.
protocol KeyValueStore {
    func string(forKey key: String) -> String?
    func set(_ value: String?, forKey key: String)

    func bool(forKey key: String) -> Bool
    func set(_ value: Bool, forKey key: String)

    func double(forKey key: String) -> Double
    func set(_ value: Double, forKey key: String)

    func removeObject(forKey key: String)
}

/// `KeyValueStore` backed by `UserDefaults` — the production implementation.
final class UserDefaultsKeyValueStore: KeyValueStore {
    private let defaults: UserDefaults

    init(suiteName: String = "com.elsapiens.backgroundlocation.state") {
        self.defaults = UserDefaults(suiteName: suiteName) ?? .standard
    }

    func string(forKey key: String) -> String? {
        defaults.string(forKey: key)
    }

    func set(_ value: String?, forKey key: String) {
        defaults.set(value, forKey: key)
    }

    func bool(forKey key: String) -> Bool {
        defaults.bool(forKey: key)
    }

    func set(_ value: Bool, forKey key: String) {
        defaults.set(value, forKey: key)
    }

    func double(forKey key: String) -> Double {
        defaults.double(forKey: key)
    }

    func set(_ value: Double, forKey key: String) {
        defaults.set(value, forKey: key)
    }

    func removeObject(forKey key: String) {
        defaults.removeObject(forKey: key)
    }
}
