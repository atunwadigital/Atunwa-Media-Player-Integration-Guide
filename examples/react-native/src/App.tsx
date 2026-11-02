import { useEffect, useState } from 'react';
import { StatusBar, useColorScheme } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AtunwaMedia } from './atunwaMedia';
import { Loading, MissingKeyScreen } from './components';
import { readConfig } from './config';
import { PublisherScreen } from './PublisherScreen';
import { SinglePlayerScreen } from './SinglePlayerScreen';

const config = readConfig();

export default function App() {
  const isDarkMode = useColorScheme() === 'dark';
  const [ready, setReady] = useState(false);

  useEffect(() => {
    // Starts the SDK's player and ad managers before any of its views are shown.
    AtunwaMedia.setup().finally(() => setReady(true));
  }, []);

  let screen;
  if (!ready) {
    screen = <Loading />;
  } else if (config.mode === 'single') {
    screen = <SinglePlayerScreen playerApiKey={config.playerApiKey} />;
  } else if (config.mode === 'publisher') {
    screen = <PublisherScreen publisherSdkKey={config.publisherSdkKey} />;
  } else {
    screen = <MissingKeyScreen problem={config.problem} />;
  }

  return (
    <SafeAreaProvider>
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      {screen}
    </SafeAreaProvider>
  );
}
