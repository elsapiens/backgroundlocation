// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ElsapiensBackgroundLocation",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "ElsapiensBackgroundLocation",
            targets: ["BackgroudLocationPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "BackgroudLocationPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/BackgroudLocationPlugin"),
        .testTarget(
            name: "BackgroudLocationPluginTests",
            dependencies: ["BackgroudLocationPlugin"],
            path: "ios/Tests/BackgroudLocationPluginTests")
    ]
)