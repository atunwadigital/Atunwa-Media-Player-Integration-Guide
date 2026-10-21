# News feed

This feature is optional and runs entirely on the client. Publishers register RSS or Atom feeds
(`NewsSource`) in the Atunwa dashboard. The backend returns only each source's identity. The SDK
fetches and parses the feeds itself.

## Where sources come from

```kotlin
val sources: List<NewsSource> =
    (MediaSDK.publisherConfig?.publisher?.newsSources          // publisher mode
        ?: MediaSDK.playerConfig?.publisher?.newsSources)      // single-player mode
        .orEmpty()
```

```kotlin
data class NewsSource(val id: String, val name: String, val logo: String = "", val feedUrl: String = "")
```

The server validates every source when it's registered. Its recent articles are guaranteed to
have a title, link, publish time and thumbnail.

## Fetching

```kotlin
lifecycleScope.launch {
    val articles: List<NewsArticle> = MediaSDK.fetchNews(sources)
    adapter.submitList(articles)
}
```

- Sources are fetched **in parallel**, with 15s connect and read timeouts and gzip support.
- Results are **merged and sorted newest first** by `publishedAt`.
- If a source fails to fetch or parse, it's **dropped** from the result instead of failing the
  whole call. An empty list means no sources, or that every one of them failed.
- No caching. Every call goes to the network, so call it again for pull-to-refresh.

```kotlin
data class NewsArticle(
    val title: String,
    val link: String,
    val publishedAt: Long,        // epoch millis
    val thumbnailUrl: String?,
    val sourceName: String,
    val sourceLogo: String?,
)
```

## Paging

Feeds have no cursor, so the full dataset is whatever `fetchNews` returned. To "paginate", reveal
more of that list as the user scrolls:

```kotlin
private var all: List<NewsArticle> = emptyList()
private var shown = 10

suspend fun loadNews() {
    all = MediaSDK.fetchNews(sources)
    adapter.submitList(all.take(shown))
}

recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
    override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
        val lm = rv.layoutManager as LinearLayoutManager
        if (lm.findLastVisibleItemPosition() >= adapter.itemCount - 3 && shown < all.size) {
            shown += 10
            adapter.submitList(all.take(shown))
        }
    }
})
```

## Displaying an article

The SDK doesn't include a reader UI. `NewsArticle.link` is a plain URL. You can open it in a
Custom Tab, an in-app `WebView` (the sample app uses a bottom sheet with a `WebView`), or the
external browser:

```kotlin
CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(article.link))
```
