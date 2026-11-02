package com.atunwadigital.examples.mediahouse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atunwadigital.examples.mediahouse.data.MediaHouseViewModel
import com.atunwadigital.examples.mediahouse.playback.MiniPlayer
import com.atunwadigital.examples.mediahouse.playback.rememberPlayback
import com.atunwadigital.examples.mediahouse.ui.Brand
import com.atunwadigital.examples.mediahouse.ui.CenteredMessage
import com.atunwadigital.examples.mediahouse.ui.HomeScreen
import com.atunwadigital.examples.mediahouse.ui.MediaHouseTheme
import com.atunwadigital.examples.mediahouse.ui.Muted
import com.atunwadigital.examples.mediahouse.ui.NewsScreen
import com.atunwadigital.examples.mediahouse.ui.PodcastDetailScreen
import com.atunwadigital.examples.mediahouse.ui.PodcastsScreen
import com.atunwadigital.examples.mediahouse.ui.RadioScreen
import com.atunwadigital.examples.mediahouse.ui.Tab
import com.atunwadigital.examples.mediahouse.ui.TvScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            MediaHouseTheme { MediaHouseApp() }
        }
    }
}

@Composable
private fun MediaHouseApp(vm: MediaHouseViewModel = viewModel()) {
    val playback = rememberPlayback()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var openShowId by rememberSaveable { mutableStateOf<String?>(null) }
    val openShow = vm.catalogue.podcasts.firstOrNull { it.id == openShowId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                // One mini player for the whole app, above the tabs: whatever is playing (a station,
                // a video or an episode) stays one tap away from every screen.
                MiniPlayer(visible = playback.hasStream && !playback.isPlayerOpen, onClose = vm::stop)
                NavigationBar(containerColor = Color(0xFF15161B)) {
                    Tab.entries.forEach {
                        NavigationBarItem(
                            selected = tab == it,
                            onClick = { tab = it; openShowId = null },
                            icon = { Icon(it.icon, contentDescription = null) },
                            label = { Text(it.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                indicatorColor = Brand,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !vm.hasKey -> CenteredMessage(
                    "Add your publisher SDK key",
                    "Copy local.properties.example to local.properties, set ATUNWA_PUBLISHER_SDK_KEY to the key Atunwa gave you, and rebuild.",
                )
                vm.publisher == null && vm.error != null -> CenteredMessage("Couldn't load the stations", vm.error.orEmpty(), "Try again") { vm.load() }
                vm.publisher == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                openShow != null -> PodcastDetailScreen(vm, openShow, playback) { openShowId = null }
                else -> when (tab) {
                    Tab.Home -> HomeScreen(vm, playback, onOpenTab = { tab = it }, onOpenShow = { tab = Tab.Podcasts; openShowId = it.id })
                    Tab.Radio -> RadioScreen(vm, playback)
                    Tab.Tv -> TvScreen(vm, playback)
                    Tab.News -> NewsScreen(vm)
                    Tab.Podcasts -> PodcastsScreen(vm) { openShowId = it.id }
                }
            }
        }
    }
}

private val Tab.icon
    get() = when (this) {
        Tab.Home -> Icons.Filled.Home
        Tab.Radio -> Icons.Filled.Headphones
        Tab.Tv -> Icons.Filled.LiveTv
        Tab.News -> Icons.Filled.Newspaper
        Tab.Podcasts -> Icons.Filled.Mic
    }
