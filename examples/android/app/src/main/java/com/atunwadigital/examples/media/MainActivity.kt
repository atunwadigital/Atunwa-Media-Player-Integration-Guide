package com.atunwadigital.examples.media

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.atunwadigital.examples.media.publisher.PublisherScreen
import com.atunwadigital.examples.media.single.SinglePlayerScreen
import com.atunwadigital.examples.media.ui.ExampleTheme
import com.atunwadigital.examples.media.ui.LoadingState
import com.atunwadigital.examples.media.ui.MissingKeyScreen
import com.atunwadigital.mediasdk.MediaSDK

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val config = AtunwaConfig.fromBuildConfig()

        setContent {
            ExampleTheme {
                // setup() binds the SDK's media controller and starts its ad managers. It must
                // finish before any SDK view is shown. It's idempotent, so running it again after
                // a configuration change returns straight away.
                var isSetUp by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    MediaSDK.setup(applicationContext).onFailure { Log.e(TAG, "MediaSDK setup failed", it) }
                    isSetUp = true
                }

                when {
                    !isSetUp -> LoadingState()
                    config is AtunwaConfig.SinglePlayer -> SinglePlayerScreen(config.playerApiKey)
                    config is AtunwaConfig.Publisher -> PublisherScreen(config.publisherSdkKey)
                    config is AtunwaConfig.Missing -> MissingKeyScreen(config.problem)
                }
            }
        }
    }

    private companion object {
        const val TAG = "AtunwaExample"
    }
}
