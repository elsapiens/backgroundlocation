import Foundation

/// Canonical error codes surfaced to the JavaScript layer.
///
/// Every rejected plugin call and every "error" event carries one of these codes so the
/// consuming app can branch on them (e.g. show a permission prompt for
/// `.backgroundPermissionDenied`) instead of parsing free-form messages. The raw string
/// values are part of the public plugin API and match the Android plugin exactly — never
/// change them, only add. Kept as an enum (rather than a struct of constants, as on
/// Android) because Swift enums give exhaustive `switch` checking at call sites for free.
enum ErrorCode: String, Error {
    /// Foreground (while-in-use) location permission has not been granted.
    case permissionDenied = "PERMISSION_DENIED"

    /// Background ("Always") location permission has not been granted. Tracking
    /// started in the foreground still works; it just cannot resume automatically
    /// after the app is suspended and later relaunched by the system.
    case backgroundPermissionDenied = "BACKGROUND_PERMISSION_DENIED"

    /// Device location services are switched off system-wide.
    case locationServicesDisabled = "LOCATION_SERVICES_DISABLED"

    /// A required call parameter is missing or empty.
    case missingParameter = "MISSING_PARAMETER"

    /// Background location updates could not be started — see the message for detail
    /// (most commonly a missing `UIBackgroundModes` "location" entry in the host
    /// app's Info.plist).
    case serviceStartFailed = "SERVICE_START_FAILED"

    /// No location fix could be obtained (timeout or provider failure).
    case locationUnavailable = "LOCATION_UNAVAILABLE"

    /// No stored data exists for the requested reference.
    case notFound = "NOT_FOUND"

    /// The request was superseded or cancelled by a newer request.
    case cancelled = "CANCELLED"

    /// An unexpected internal error occurred; message carries detail.
    case internalError = "INTERNAL_ERROR"
}

/// Which subsystem raised an "error" event, mirrored from the Android plugin.
enum ErrorSource: String {
    case taskTracking = "taskTracking"
    case workHourTracking = "workHourTracking"
    case currentLocation = "currentLocation"
    case permissions = "permissions"
}
