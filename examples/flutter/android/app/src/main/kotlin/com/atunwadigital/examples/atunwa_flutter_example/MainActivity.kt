package com.atunwadigital.examples.atunwa_flutter_example

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        AtunwaMediaBridge(this, flutterEngine.dartExecutor.binaryMessenger)

        val registry = flutterEngine.platformViewsController.registry
        registry.registerViewFactory("atunwa/mini_player", MiniPlayerFactory(this))
        registry.registerViewFactory("atunwa/live_card", LiveCardFactory(this))
    }
}
