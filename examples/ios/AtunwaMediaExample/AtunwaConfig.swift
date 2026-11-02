import Foundation

/// Which Atunwa key this build uses. Atunwa issues both kinds of key, and both are safe to ship in
/// an app. They're set in `Config/Atunwa.local.xcconfig` and reach the app through `Info.plist`.
enum AtunwaConfig {
    /// One station, identified by its player API key.
    case singlePlayer(playerApiKey: String)
    /// Every station a publisher has, identified by the publisher's SDK key.
    case publisher(publisherSdkKey: String)
    /// The build has no usable key. `problem` says what to fix.
    case missing(problem: String)

    static func fromInfoPlist(_ bundle: Bundle = .main) -> AtunwaConfig {
        func value(_ key: String) -> String {
            (bundle.object(forInfoDictionaryKey: key) as? String ?? "").trimmingCharacters(in: .whitespaces)
        }

        let mode = value("ATUNWA_APP_MODE")
        switch mode.lowercased() {
        case "single":
            let key = value("ATUNWA_PLAYER_KEY")
            return key.isEmpty ? .missing(problem: "ATUNWA_APP_MODE is single, but ATUNWA_PLAYER_KEY is empty.")
                               : .singlePlayer(playerApiKey: key)
        case "publisher":
            let key = value("ATUNWA_PUBLISHER_SDK_KEY")
            return key.isEmpty ? .missing(problem: "ATUNWA_APP_MODE is publisher, but ATUNWA_PUBLISHER_SDK_KEY is empty.")
                               : .publisher(publisherSdkKey: key)
        default:
            return .missing(problem: "ATUNWA_APP_MODE must be single or publisher, not \"\(mode)\".")
        }
    }
}
