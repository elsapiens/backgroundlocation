// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ElsapiensBackgroundLocation",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "ElsapiensBackgroundLocation",
            targets: ["BackgroundLocationPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "BackgroundLocationPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/BackgroundLocationPlugin"),
        .testTarget(
            name: "BackgroundLocationPluginTests",
            dependencies: ["BackgroundLocationPlugin"],
            path: "ios/Tests/BackgroundLocationPluginTests")
    ]
)