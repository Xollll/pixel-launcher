package com.example.pixellauncher

import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

data class NewsHeadline(val title: String, val url: String, val source: String)
data class NewsFeedState(val headlines: List<NewsHeadline> = emptyList(), val failed: Boolean = false)

fun fetchGoogleNews(): NewsFeedState = runCatching {
    val connection = (URL("https://news.google.com/rss?hl=en-MY&gl=MY&ceid=MY:en").openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "PixelLauncher/1.0")
    }
    connection.useConnection { input ->
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, "UTF-8")
        }
        val headlines = mutableListOf<NewsHeadline>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && headlines.size < 12) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                var title = ""
                var link = ""
                var source = ""
                var itemEvent = parser.next()
                while (!(itemEvent == XmlPullParser.END_TAG && parser.name == "item") && itemEvent != XmlPullParser.END_DOCUMENT) {
                    if (itemEvent == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "title" -> title = parser.nextText().trim()
                            "link" -> link = parser.nextText().trim()
                            "source" -> source = parser.nextText().trim()
                        }
                    }
                    itemEvent = parser.next()
                }
                val uri = Uri.parse(link)
                if (title.isNotBlank() && uri.scheme == "https" && uri.host == "news.google.com") {
                    if (source.isBlank()) {
                        val separator = title.lastIndexOf(" - ")
                        if (separator > 0) {
                            source = title.substring(separator + 3).trim()
                            title = title.substring(0, separator).trim()
                        }
                    }
                    headlines += NewsHeadline(title, link, source.ifBlank { "GOOGLE NEWS" })
                }
                event = parser.next()
            } else {
                event = parser.next()
            }
        }
        NewsFeedState(headlines = headlines)
    }
}.getOrElse { NewsFeedState(failed = true) }

private inline fun <T> HttpURLConnection.useConnection(block: (java.io.InputStream) -> T): T = try {
    inputStream.use(block)
} finally {
    disconnect()
}
