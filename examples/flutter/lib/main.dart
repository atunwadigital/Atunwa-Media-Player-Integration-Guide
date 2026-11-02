import 'package:flutter/material.dart';

import 'atunwa_media.dart';
import 'config.dart';
import 'publisher_screen.dart';
import 'single_player_screen.dart';
import 'widgets.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Starts the SDK's player and ad managers before any of its views are shown.
  await AtunwaMedia.setup();
  runApp(ExampleApp(config: AtunwaConfig.fromEnvironment()));
}

class ExampleApp extends StatelessWidget {
  const ExampleApp({super.key, required this.config});

  final AtunwaConfig config;

  @override
  Widget build(BuildContext context) {
    const brand = Color(0xFF0057FF); // the SDK's default primary color
    return MaterialApp(
      title: 'Atunwa Example',
      theme: ThemeData(colorSchemeSeed: brand),
      darkTheme: ThemeData(colorSchemeSeed: brand, brightness: Brightness.dark),
      home: switch (config) {
        SinglePlayer(:final playerApiKey) => SinglePlayerScreen(playerApiKey: playerApiKey),
        Publisher(:final publisherSdkKey) => PublisherScreen(publisherSdkKey: publisherSdkKey),
        MissingKey(:final problem) => MissingKeyScreen(problem: problem),
      },
    );
  }
}
