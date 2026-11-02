import Flutter
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)

    // The Atunwa Media SDK bridge and its native views.
    let registrar = engineBridge.pluginRegistry.registrar(forPlugin: "AtunwaMediaBridge")!
    AtunwaMediaBridge.register(with: registrar)
    registrar.register(MiniPlayerFactory(), withId: "atunwa/mini_player")
    registrar.register(LiveCardFactory(), withId: "atunwa/live_card")
  }
}
