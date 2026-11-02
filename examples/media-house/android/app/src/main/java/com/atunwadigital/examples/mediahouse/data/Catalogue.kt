package com.atunwadigital.examples.mediahouse.data

import android.content.Context
import com.atunwadigital.mediasdk.models.MediaItem
import com.atunwadigital.mediasdk.models.MediaMetadata
import com.atunwadigital.mediasdk.models.StreamChannels
import com.atunwadigital.mediasdk.models.StreamType
import org.json.JSONObject

// On-demand content. The SDK serves live stations and news from the Atunwa dashboard; videos and
// podcasts come from the publisher's own CMS. This example reads a bundled demo catalogue
// (assets/catalogue.json) instead, and hands each item to the SDK as a MediaItem.

data class Video(
    val id: String,
    val title: String,
    val show: String,
    val summary: String,
    val durationSeconds: Int,
    val color: String,
    val url: String,
)

data class Episode(
    val id: String,
    val title: String,
    val published: String,
    val durationSeconds: Int,
    val url: String,
)

data class PodcastShow(
    val id: String,
    val title: String,
    val host: String,
    val summary: String,
    val color: String,
    val episodes: List<Episode>,
)

data class Catalogue(val videos: List<Video>, val podcasts: List<PodcastShow>) {

    companion object {
        fun load(context: Context): Catalogue {
            val json = JSONObject(context.assets.open("catalogue.json").bufferedReader().use { it.readText() })
            val videos = json.getJSONArray("videos").objects().map {
                Video(
                    id = it.getString("id"),
                    title = it.getString("title"),
                    show = it.getString("show"),
                    summary = it.getString("summary"),
                    durationSeconds = it.getInt("durationSeconds"),
                    color = it.getString("color"),
                    url = it.getString("url"),
                )
            }
            val podcasts = json.getJSONArray("podcasts").objects().map { show ->
                PodcastShow(
                    id = show.getString("id"),
                    title = show.getString("title"),
                    host = show.getString("host"),
                    summary = show.getString("summary"),
                    color = show.getString("color"),
                    episodes = show.getJSONArray("episodes").objects().map {
                        Episode(
                            id = it.getString("id"),
                            title = it.getString("title"),
                            published = it.getString("published"),
                            durationSeconds = it.getInt("durationSeconds"),
                            url = it.getString("url"),
                        )
                    },
                )
            }
            return Catalogue(videos, podcasts)
        }

        private fun org.json.JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    }
}

/** An on-demand video for the SDK. The full-screen player shows it in its TV layout. */
fun Video.toMediaItem() = MediaItem(
    id = id,
    streamUrl = url,
    streamChannels = StreamChannels(isAudio = true, isVideo = true),
    streamType = StreamType.VOD,
    metadata = MediaMetadata(title = title, artistOrShow = show),
)

/** A podcast episode for the SDK: on-demand audio, with the show as the subtitle. */
fun Episode.toMediaItem(show: PodcastShow) = MediaItem(
    id = id,
    streamUrl = url,
    streamChannels = StreamChannels(isAudio = true, isVideo = false),
    streamType = StreamType.VOD,
    metadata = MediaMetadata(title = title, artistOrShow = show.title),
)

/** "9:56" or "1:02:03". */
fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
