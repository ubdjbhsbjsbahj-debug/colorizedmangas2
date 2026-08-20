package eu.kanade.tachiyomi.extension.en.colorizedmangas

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ColorizedMangas : ParsedHttpSource() {

    override val name = "Colorized Mangas"
    override val baseUrl = "https://colorizedmangas.com"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

    // ============== POPULAR ==============
    override fun popularMangaRequest(page: Int) = GET("$baseUrl", headers)

    override fun popularMangaSelector() = "a[href*='/'][data-testid*='series-link'], a[href*='/one-piece'], a[href*='/naruto'], a[href*='/bleach']"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val href = element.attr("href")
        val title = element.text().substringBefore("\n").trim()
        
        manga.url = href
        manga.title = title.ifEmpty { 
            href.replace(baseUrl, "").trim('/').replace("-", " ").replaceFirstChar { it.uppercase() }
        }
        
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    // ============== LATEST ==============
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // ============== SEARCH ==============
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty()) {
            GET("$baseUrl", headers)
        } else {
            GET("$baseUrl", headers)
        }
    }

    override fun searchMangaSelector() = "a[href*='/'][href!='/']:not([href^='#']):not([href*='contact']):not([href*='dmca']):not([href*='terms']):not([href*='privacy'])"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val href = element.attr("href")
        val title = element.select("img").attr("alt")
            .takeIf { it.isNotEmpty() } 
            ?: element.text().substringBefore("\n").trim()
        
        manga.url = href
        manga.title = title.ifEmpty {
            href.replace(baseUrl, "").trim('/').replace("-", " ")
                .replaceFirstChar { it.uppercase() }
        }
        
        return manga
    }

    override fun searchMangaNextPageSelector(): String? = null

    // ============== MANGA DETAILS ==============
    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        
        manga.title = document.selectFirst("h1")?.text() ?: "Unknown"
        
        // Try to find author/artist info
        document.selectFirst("p:contains(Author), p:contains(By)")?.let {
            manga.author = it.text().replace("Author", "").replace("By", "").trim()
        }
        
        manga.description = document.select("p").mapNotNull { 
            it.text().takeIf { text -> text.length > 50 && !text.contains("colorized", ignoreCase = true) }
        }.firstOrNull() ?: "Colorized manga from colorizedmangas.com"
        
        manga.status = SManga.ONGOING
        manga.genre = "Manga,Colorized"
        manga.thumbnail_url = document.selectFirst("img[alt*='cover'], img[src*='cover'], img[alt*='manga']")?.attr("src")
        
        return manga
    }

    // ============== CHAPTERS ==============
    override fun chapterListSelector() = "a[href*='/chapter/']:not([href*='#'])"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val href = element.attr("href")
        
        // Extract chapter number from URL or text
        val chapterText = element.text().trim()
        chapter.name = when {
            chapterText.contains("Chapter") -> chapterText
            chapterText.isNotEmpty() -> "Chapter: $chapterText"
            else -> {
                val match = Regex("""chapter/(\d+)""").find(href)
                "Chapter ${match?.groupValues?.get(1) ?: "?"}"
            }
        }
        
        chapter.url = href
        chapter.date_upload = System.currentTimeMillis()
        
        return chapter
    }

    override fun chapterListParse(response: okhttp3.Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val document = response.asJsoup()
        
        document.select(chapterListSelector()).forEach { element ->
            try {
                chapters.add(chapterFromElement(element))
            } catch (e: Exception) {
                // Skip problematic entries
            }
        }
        
        return chapters.distinctBy { it.url }.reversed()
    }

    // ============== PAGES ==============
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        
        // Try to find all images in the reader
        val images = document.select("img[src*='image'], img[src*='chapter'], img[src*='manga'], img[src*='page']")
        
        // Fallback: Look for images with common patterns
        if (images.isEmpty()) {
            document.select("img").forEach { img ->
                val src = img.attr("src")
                if (src.contains(Regex("""(image|chapter|page|manga)""", RegexOption.IGNORE_CASE))) {
                    val fullUrl = if (src.startsWith("http")) src else baseUrl + src
                    pages.add(Page(pages.size, "", fullUrl))
                }
            }
        } else {
            images.forEachIndexed { index, img ->
                val src = img.attr("src")
                val fullUrl = if (src.startsWith("http")) src else baseUrl + src
                pages.add(Page(index, "", fullUrl))
            }
        }
        
        return pages
    }

    override fun imageUrlParse(document: Document): String {
        return document.selectFirst("img")?.attr("src") ?: ""
    }

    override fun getFilterList() = FilterList()
}
