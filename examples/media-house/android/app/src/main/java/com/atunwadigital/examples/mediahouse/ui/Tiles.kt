package com.atunwadigital.examples.mediahouse.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.atunwadigital.examples.mediahouse.data.PodcastShow
import com.atunwadigital.examples.mediahouse.data.Video
import com.atunwadigital.examples.mediahouse.data.formatDuration
import com.atunwadigital.mediasdk.models.NewsArticle
import com.atunwadigital.mediasdk.models.PlayerConfig
import java.text.DateFormat
import java.util.Date

/** A radio station as a round logo, the way most radio apps present "on air now". */
@Composable
fun StationCircle(station: PlayerConfig, isPlaying: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(84.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            StationLogo(
                station.logo, 76.dp, circle = true, name = station.playerName,
                modifier = if (isPlaying) Modifier.border(3.dp, Brand, CircleShape) else Modifier,
            )
            if (isPlaying) {
                Box(Modifier.clip(CircleShape).background(Color.Black).padding(5.dp)) { Equalizer() }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            station.playerName.trim(),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** A live TV channel as a 16:9 tile. */
@Composable
fun TvTile(station: PlayerConfig, isPlaying: Boolean, width: Dp?, onClick: () -> Unit) {
    WideTile(
        width = width,
        onClick = onClick,
        background = {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A2D36), Color(0xFF111217)))), contentAlignment = Alignment.Center) {
                StationLogo(station.logo, 64.dp, name = station.playerName)
            }
        },
        overlay = {
            LiveBadge()
            Spacer(Modifier.width(8.dp))
            Text(station.playerName.trim(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (isPlaying) Equalizer(color = Color.White)
        },
    )
}

/**
 * A catch-up video as a 16:9 tile. [compact] shows only the duration, for rows that print the
 * title beside the tile.
 */
@Composable
fun VideoTile(video: Video, isPlaying: Boolean, width: Dp?, compact: Boolean = false, onClick: () -> Unit) {
    WideTile(
        width = width,
        onClick = onClick,
        background = { GradientArtwork(video.color, "", Modifier.fillMaxSize(), icon = Icons.Filled.PlayCircle) },
        overlay = {
            if (compact) {
                Text(formatDuration(video.durationSeconds), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f)) {
                    Text(video.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${video.show} · ${formatDuration(video.durationSeconds)}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
            if (isPlaying) Equalizer(color = Color.White)
        },
    )
}

/** A podcast as square artwork. */
@Composable
fun PodcastTile(show: PodcastShow, size: Dp, onClick: () -> Unit) {
    Column(Modifier.width(size).clickable(onClick = onClick)) {
        GradientArtwork(show.color, show.title, Modifier.size(size), icon = Icons.Filled.Mic)
        Spacer(Modifier.height(6.dp))
        Text(show.host, fontSize = 12.sp, color = Muted, maxLines = 1)
    }
}

/** The lead story: a large image with the headline over it. */
@Composable
fun NewsHero(article: NewsArticle, onClick: () -> Unit) {
    Box(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = article.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(article.sourceName.uppercase(), color = Brand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(article.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A story in a list: headline, source and time, with a thumbnail. */
@Composable
fun NewsRow(article: NewsArticle, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(article.title, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("${article.sourceName} · ${relativeTime(article.publishedAt)}", fontSize = 12.sp, color = Muted)
        }
        AsyncImage(
            model = article.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 96.dp, height = 72.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

/** Opens a story in a Custom Tab, so the mini player keeps playing underneath. */
fun openArticle(context: Context, article: NewsArticle) {
    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(article.link))
}

private fun relativeTime(epochMs: Long): String {
    val minutes = (System.currentTimeMillis() - epochMs) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
    }
}
