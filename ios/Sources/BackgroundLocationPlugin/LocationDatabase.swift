import Foundation
#if canImport(SQLite3)
import SQLite3
#endif

/// A single recorded fix, mirroring the Android plugin's `LocationItem`.
struct LocationItem {
    let reference: String
    let index: Int
    let latitude: Double
    let longitude: Double
    let altitude: Double
    let accuracy: Double
    let speed: Double
    let heading: Double
    let altitudeAccuracy: Double
    let totalDistance: Double
    let timestamp: Date
}

/// SQLite-backed storage for recorded route fixes.
///
/// Uses the system `libsqlite3` C API directly (bundled with iOS, zero extra
/// dependencies) rather than a wrapper library, keeping this plugin's only
/// dependency as Capacitor itself — mirroring the Android plugin's raw
/// `SQLiteOpenHelper` usage. Not thread-safe by itself: callers serialize access
/// through a single dedicated queue (see `TaskLocationTracker`), matching how the
/// Android service only ever touches its `SQLiteDatabaseHelper` from one thread.
final class LocationDatabase {
    private var db: OpaquePointer?
    private let path: String

    init(filename: String = "elsapiens_background_location.sqlite") {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        self.path = directory.appendingPathComponent(filename).path
        open()
    }

    private func open() {
        guard sqlite3_open(path, &db) == SQLITE_OK else {
            NSLog("[BackgroundLocation] Failed to open location database at \(path)")
            db = nil
            return
        }
        let createTable = """
            CREATE TABLE IF NOT EXISTS locations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                reference TEXT NOT NULL,
                idx INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitude REAL NOT NULL,
                accuracy REAL NOT NULL,
                speed REAL NOT NULL,
                heading REAL NOT NULL,
                altitude_accuracy REAL NOT NULL,
                timestamp REAL NOT NULL
            );
            """
        execute(createTable)
        execute("CREATE INDEX IF NOT EXISTS idx_locations_reference ON locations(reference);")
    }

    @discardableResult
    private func execute(_ sql: String) -> Bool {
        guard let db else { return false }
        if sqlite3_exec(db, sql, nil, nil, nil) != SQLITE_OK {
            let message = String(cString: sqlite3_errmsg(db))
            NSLog("[BackgroundLocation] SQL error: \(message) — statement: \(sql)")
            return false
        }
        return true
    }

    func insertLocation(reference: String, index: Int, latitude: Double, longitude: Double, altitude: Double,
                         accuracy: Double, speed: Double, heading: Double, altitudeAccuracy: Double,
                         timestamp: Date) {
        guard let db else { return }
        let sql = """
            INSERT INTO locations
                (reference, idx, latitude, longitude, altitude, accuracy, speed, heading, altitude_accuracy, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """
        var statement: OpaquePointer?
        defer { sqlite3_finalize(statement) }
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else { return }

        sqlite3_bind_text(statement, 1, reference, -1, SQLITE_TRANSIENT)
        sqlite3_bind_int(statement, 2, Int32(index))
        sqlite3_bind_double(statement, 3, latitude)
        sqlite3_bind_double(statement, 4, longitude)
        sqlite3_bind_double(statement, 5, altitude)
        sqlite3_bind_double(statement, 6, accuracy)
        sqlite3_bind_double(statement, 7, speed)
        sqlite3_bind_double(statement, 8, heading)
        sqlite3_bind_double(statement, 9, altitudeAccuracy)
        sqlite3_bind_double(statement, 10, timestamp.timeIntervalSince1970)

        if sqlite3_step(statement) != SQLITE_DONE {
            NSLog("[BackgroundLocation] Failed to insert location: \(String(cString: sqlite3_errmsg(db)))")
        }
    }

    func getNextIndex(forReference reference: String) -> Int {
        guard let db else { return 0 }
        let sql = "SELECT COALESCE(MAX(idx), -1) + 1 FROM locations WHERE reference = ?;"
        var statement: OpaquePointer?
        defer { sqlite3_finalize(statement) }
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else { return 0 }
        sqlite3_bind_text(statement, 1, reference, -1, SQLITE_TRANSIENT)
        guard sqlite3_step(statement) == SQLITE_ROW else { return 0 }
        return Int(sqlite3_column_int(statement, 0))
    }

    func getTotalDistanceKm(forReference reference: String) -> Double {
        let rows = queryLocations(forReference: reference)
        guard rows.count > 1 else { return 0 }
        var total = 0.0
        for i in 1..<rows.count {
            total += DistanceTracker.haversineKm(
                lat1: rows[i - 1].latitude, lon1: rows[i - 1].longitude,
                lat2: rows[i].latitude, lon2: rows[i].longitude)
        }
        return total
    }

    func getLastLocation(forReference reference: String) -> LocationItem? {
        queryLocations(forReference: reference, limit: 1, descending: true).first
    }

    func getLocations(forReference reference: String) -> [LocationItem] {
        queryLocations(forReference: reference)
    }

    private func queryLocations(forReference reference: String, limit: Int? = nil, descending: Bool = false) -> [LocationItem] {
        guard let db else { return [] }
        var sql = "SELECT reference, idx, latitude, longitude, altitude, accuracy, speed, heading, altitude_accuracy, timestamp "
            + "FROM locations WHERE reference = ? ORDER BY idx \(descending ? "DESC" : "ASC")"
        if let limit {
            sql += " LIMIT \(limit)"
        }
        sql += ";"

        var statement: OpaquePointer?
        defer { sqlite3_finalize(statement) }
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else { return [] }
        sqlite3_bind_text(statement, 1, reference, -1, SQLITE_TRANSIENT)

        var results: [LocationItem] = []
        var runningDistance = 0.0
        var previous: (Double, Double)?
        while sqlite3_step(statement) == SQLITE_ROW {
            let lat = sqlite3_column_double(statement, 2)
            let lon = sqlite3_column_double(statement, 3)
            if let previous {
                runningDistance += DistanceTracker.haversineKm(lat1: previous.0, lon1: previous.1, lat2: lat, lon2: lon)
            }
            previous = (lat, lon)
            results.append(LocationItem(
                reference: String(cString: sqlite3_column_text(statement, 0)),
                index: Int(sqlite3_column_int(statement, 1)),
                latitude: lat,
                longitude: lon,
                altitude: sqlite3_column_double(statement, 4),
                accuracy: sqlite3_column_double(statement, 5),
                speed: sqlite3_column_double(statement, 6),
                heading: sqlite3_column_double(statement, 7),
                altitudeAccuracy: sqlite3_column_double(statement, 8),
                totalDistance: runningDistance,
                timestamp: Date(timeIntervalSince1970: sqlite3_column_double(statement, 9))
            ))
        }
        return results
    }

    func clearAll() {
        execute("DELETE FROM locations;")
    }

    deinit {
        sqlite3_close(db)
    }
}

// sqlite3_bind_text needs an explicit "how to free this string" transient marker.
private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
