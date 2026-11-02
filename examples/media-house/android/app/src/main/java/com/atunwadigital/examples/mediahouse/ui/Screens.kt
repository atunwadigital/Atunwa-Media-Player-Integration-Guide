package com.atunwadigital.examples.mediahouse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import com.atunwadigital.examples.mediahouse.data.MediaHouseViewModel
import com.atunwadigital.examples.mediahouse.data.PodcastShow
import com.atunwadigital.examples.mediahouse.data.formatDuration
import com.atunwadigital.examples.mediahouse.playback.InFeedBanner
import com.atunwadigital.examples.mediahouse.playback.LiveCard
import com.atunwadigital.examples.mediahouse.playback.Playback
import com.atunwadigital.mediasdk.MediaSDK
import java.util.Calendar

enum class Tab(val label: String) { Home("Home"), Radio("Radio"), Tv("TV"), News("News"), Podcasts("Podcasts") }

// Home: one screen that samples every section, the way large broadcasters open their apps.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MediaHouseViewModel, playback: Playback, onOpenTab: (Tab) -> Unit, onOpenShow: (PodcastShow) -> Unit) {
    val context = LocalContext.current
    val publisher = vm.publisher ?: return
    PullToRefreshBox(isRefreshing = vm.isRefreshing, onRefresh = { vm.refresh() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    StationLogo(publisher.publisher.logo, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(greeting(), color = Muted, fontSize = 13.sp)
                        Text(publisher.publisher.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // The SDK's live card: shows whatever is playing, or the featured channel until then.
            // Its play button plays in place; a tap on the card opens the full-screen player.
            item {
                val featured = vm.featuredStation
                LiveCard(
                    previewPlayer = featured,
                    onClick = {
                        if (playback.hasStream) MediaSDK.launchPlayer(context)
                        else featured?.let { vm.watchStation(context, it) }
                    },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                )
            }
            if (vm.radioStations.isNotEmpty()) {
                item { SectionHeader("On air now", "All radio") { onOpenTab(Tab.Radio) } }
                rail {
                    items(vm.radioStations, key = { it.assetKey }) { station ->
                        val playing = playback.isPlayingStation(station.assetKey)
                        StationCircle(station, playing) { if (playing) vm.pause() else vm.playStationInPlace(station) }
                    }
                }
            }
            if (vm.tvStations.isNotEmpty()) {
                item { SectionHeader("Live TV", "All TV") { onOpenTab(Tab.Tv) } }
                rail {
                    items(vm.tvStations, key = { it.assetKey }) { station ->
                        TvTile(station, playback.isPlayingStation(station.assetKey), 260.dp) { vm.watchStation(context, station) }
                    }
                }
            }
            if (vm.news.isNotEmpty()) {
                item { SectionHeader("Top stories", "More news") { onOpenTab(Tab.News) } }
                items(vm.news.take(3), key = { it.link }) { article -> NewsRow(article) { openArticle(context, article) } }
            }
            item { SectionHeader("Catch up", "All shows") { onOpenTab(Tab.Tv) } }
            rail {
                items(vm.catalogue.videos, key = { it.id }) { video ->
                    VideoTile(video, playback.isPlayingItem(video.id), 240.dp) { vm.watchVideo(context, video) }
                }
            }
            item { SectionHeader("Podcasts", "All podcasts") { onOpenTab(Tab.Podcasts) } }
            rail {
                items(vm.catalogue.podcasts, key = { it.id }) { show -> PodcastTile(show, 140.dp) { onOpenShow(show) } }
            }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

// Radio: every audio station, played in place. The mini player is the listener's remote control.

@Composable
fun RadioScreen(vm: MediaHouseViewModel, playback: Playback) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle("Radio", "${vm.radioStations.size} stations, live")
        }
        items(vm.radioStations, key = { it.assetKey }) { station ->
            val isActive = playback.liveStationKey == station.assetKey
            val playing = playback.isPlayingStation(station.assetKey)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isActive) Brand.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { if (playing) vm.pause() else vm.playStationInPlace(station) }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    StationLogo(station.logo, 56.dp, name = station.playerName)
                    Spacer(Modifier.weight(1f))
                    PlayPauseButton(playing, { if (playing) vm.pause() else vm.playStationInPlace(station) }, size = 34.dp)
                }
                Spacer(Modifier.height(12.dp))
                Text(station.playerName.trim(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playing) {
                        Equalizer()
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (isActive) "On air" else "Radio", fontSize = 12.sp, color = if (isActive) Brand else Muted)
                }
            }
        }
    }
}

// TV: live channels open the SDK's full-screen player; catch-up videos are the publisher's own
// streams, handed to the same player as MediaItems.

@Composable
fun TvScreen(vm: MediaHouseViewModel, playback: Playback) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle("TV", "Live channels and catch-up", Modifier.padding(20.dp)) }
        items(vm.tvStations, key = { it.assetKey }) { station ->
            Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                TvTile(station, playback.isPlayingStation(station.assetKey), null) { vm.watchStation(context, station) }
            }
        }
        item {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Catch up", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                DemoLabel()
            }
        }
        items(vm.catalogue.videos, key = { it.id }) { video ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { vm.watchVideo(context, video) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(150.dp)) { VideoTile(video, playback.isPlayingItem(video.id), 150.dp, compact = true) { vm.watchVideo(context, video) } }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(video.show.uppercase(), color = Brand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(video.title, fontWeight = FontWeight.Bold)
                    Text(video.summary, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// News: the publisher's feeds from the dashboard, fetched by the SDK, with an Ad Manager banner in
// the feed. Stories open in a Custom Tab, so whatever is playing keeps playing.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(vm: MediaHouseViewModel) {
    val context = LocalContext.current
    PullToRefreshBox(isRefreshing = vm.isRefreshing, onRefresh = { vm.refresh() }) {
        when {
            vm.news.isEmpty() && vm.isLoadingNews -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            vm.news.isEmpty() -> CenteredMessage("No news yet", "Add news feeds to the publisher on the Atunwa dashboard.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { ScreenTitle("News", vm.publisher?.publisher?.newsSources?.joinToString { it.name }.orEmpty(), Modifier.padding(20.dp)) }
                item { NewsHero(vm.news.first()) { openArticle(context, vm.news.first()) } }
                itemsIndexed(vm.news.drop(1), key = { _, it -> it.link }) { index, article ->
                    NewsRow(article) { openArticle(context, article) }
                    if (index == 2) vm.inFeedAdUnit?.let { InFeedBanner(it) }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

// Podcasts: on-demand audio from the publisher's CMS (a bundled demo catalogue here), played in
// place through MediaSDK.load(item).

@Composable
fun PodcastsScreen(vm: MediaHouseViewModel, onOpenShow: (PodcastShow) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Podcasts", "Shows from the newsroom", Modifier.weight(1f))
                DemoLabel()
            }
        }
        items(vm.catalogue.podcasts, key = { it.id }) { show ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenShow(show) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GradientArtwork(show.color, "", Modifier.size(88.dp), icon = Icons.Filled.Mic)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(show.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(show.host, color = Brand, fontSize = 13.sp)
                    Text(show.summary, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun PodcastDetailScreen(vm: MediaHouseViewModel, show: PodcastShow, playback: Playback, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                GradientArtwork(show.color, show.title, Modifier.size(180.dp), icon = Icons.Filled.Mic)
                Spacer(Modifier.height(16.dp))
                Text(show.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(show.host, color = Brand)
                Spacer(Modifier.height(8.dp))
                Text(show.summary, color = Muted)
                Spacer(Modifier.height(8.dp))
                DemoLabel()
                Spacer(Modifier.height(16.dp))
            }
        }
        items(show.episodes, key = { it.id }) { episode ->
            val isActive = playback.itemId == episode.id
            val playing = playback.isPlayingItem(episode.id)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { if (playing) vm.pause() else vm.playEpisode(show, episode) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(episode.published, color = Muted, fontSize = 12.sp)
                    Text(episode.title, fontWeight = FontWeight.SemiBold, color = if (isActive) Brand else MaterialTheme.colorScheme.onSurface)
                    Text(formatDuration(episode.durationSeconds), color = Muted, fontSize = 12.sp)
                }
                PlayPauseButton(playing, { if (playing) vm.pause() else vm.playEpisode(show, episode) })
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = Muted)
    }
}
