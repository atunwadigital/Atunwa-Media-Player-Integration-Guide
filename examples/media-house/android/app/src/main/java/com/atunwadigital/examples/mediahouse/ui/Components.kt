package com.atunwadigital.examples.mediahouse.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.atunwadigital.examples.mediahouse.data.BRAND_COLOR_HEX

val Brand = Color(android.graphics.Color.parseColor(BRAND_COLOR_HEX))
private val Background = Color(0xFF0E0F13)
private val SurfaceColor = Color(0xFF1A1C22)
val Muted = Color(0xFF9AA0AC)

@Composable
fun MediaHouseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Brand,
            onPrimary = Color.White,
            background = Background,
            surface = Background,
            surfaceVariant = SurfaceColor,
            surfaceContainer = SurfaceColor,
            onSurfaceVariant = Muted,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background, content = content)
    }
}

fun hexColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(SurfaceColor)

/** A section title, with an optional "See all" action. */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Brand) }
    }
}

/** A horizontally scrolling rail of items, the shape of most publishers' home screens. */
fun LazyListScope.rail(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    item {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Artwork for content with no image of its own: a gradient in its color, with its title. */
@Composable
fun GradientArtwork(color: String, title: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val base = hexColor(color)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.55f), Color.Black))),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}

/**
 * A station logo on a light chip, since most logos are designed for white backgrounds. Stations
 * without a logo on the dashboard get their initials instead.
 */
@Composable
fun StationLogo(url: String?, size: Dp, modifier: Modifier = Modifier, circle: Boolean = false, name: String = "") {
    val shape = if (circle) CircleShape else RoundedCornerShape(size / 5)
    val chip = modifier.size(size).clip(shape).background(Color.White)
    if (url.isNullOrBlank()) {
        Box(chip, contentAlignment = Alignment.Center) {
            Text(initials(name), color = Background, fontWeight = FontWeight.Black, fontSize = (size.value / 3).sp)
        }
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = chip.padding(size / 10))
    }
}

private fun initials(name: String) =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

/** A pulsing "LIVE" pill. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(50)).background(Brand).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(4.dp))
        Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Three bouncing bars, shown on whatever is playing. */
@Composable
fun Equalizer(modifier: Modifier = Modifier, color: Color = Brand) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    Row(modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(380, 520, 300).forEach { duration ->
            val fraction by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse),
                label = "bar",
            )
            Box(Modifier.width(3.dp).fillMaxHeight(fraction).background(color))
        }
    }
}

/** A round play/pause button. */
@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Brand).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** A small "DEMO" label for the bundled on-demand content. */
@Composable
fun DemoLabel(modifier: Modifier = Modifier) {
    Text(
        "DEMO CONTENT",
        color = Muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.border(1.dp, Muted.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun CenteredMessage(title: String, message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Muted, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** A 16:9 tile with content laid over the bottom, for TV channels and videos. */
@Composable
fun WideTile(width: Dp?, onClick: () -> Unit, background: @Composable () -> Unit, overlay: @Composable RowScope.() -> Unit) {
    Box(
        modifier = (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        background()
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = overlay,
        )
    }
}
