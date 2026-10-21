# News feed

Publishers can register RSS/Atom feeds in the Atunwa CMS. The SDK fetches and parses them on the
device, so your app can show a news feed next to its stations.

## Where sources come from

The backend returns a publisher's validated feeds as `NewsSource`s:

| Mode | Where |
|---|---|
| Publisher mode | `MediaSDK.shared.publisherConfig?.publisher.newsSources` |
| Single-player mode | `MediaSDK.shared.playerConfig?.publisher?.newsSources` |

```swift
public struct NewsSource {
    public let id: String
    public let name: String
    public let logo: String      // "" when none
    public let feedUrl: String
}
```

Every source the backend returns has already passed server-side validation: the feed was reachable,
parsed as RSS/Atom, and its recent articles had a title, link, date and thumbnail.

## Fetching

```swift
let sources = MediaSDK.shared.publisherConfig?.publisher.newsSources ?? []
let articles = await MediaSDK.shared.fetchNews(sources: sources)
```

`fetchNews(sources:)`:

- Fetches every source in parallel (15 s timeout each).
- Parses RSS 2.0 and Atom.
- Merges the results into one list, newest first.
- Never throws. A source that fails to fetch or parse is left out of the result.

```swift
public struct NewsArticle: Identifiable {
    public var id: String { link }
    public let title: String
    public let link: String
    public let publishedAt: Date
    public let thumbnailUrl: String?
    public let sourceName: String
    public let sourceLogo: String?
}
```

> On Android `publishedAt` is epoch milliseconds; on iOS it's a `Date`.

The SDK doesn't cache articles. Fetch again on pull-to-refresh or when the screen appears.

## Paging

`fetchNews` returns everything the feeds currently hold (typically a few dozen articles per source).
Page through that list in your UI:

```swift
let pageSize = 20
var visible = Array(articles.prefix(pageSize))
func loadMore() { visible = Array(articles.prefix(visible.count + pageSize)) }
```

## Displaying an article

Open the article's `link` in Safari View Controller so the user stays in your app:

```swift
import SafariServices

func open(_ article: NewsArticle) {
    guard let url = URL(string: article.link) else { return }
    present(SFSafariViewController(url: url), animated: true)
}
```

In SwiftUI, `@Environment(\.openURL)` or `Link(destination:)` open it in the browser instead.

The sample app's `PublisherView` shows the full flow: it fetches with `fetchNews(sources:)` and lists
the articles with their thumbnails and source logos.
