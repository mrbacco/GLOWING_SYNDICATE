/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/data/results/LotteryIe.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.data.results

import com.mrbacco.syndicate.data.local.StoredDraw
import com.mrbacco.syndicate.data.local.StoredPrize
import com.mrbacco.syndicate.domain.Lotto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId

/** The results page could not be read: no internet, lottery.ie is down, or it changed its page. */
class ScrapeException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ScrapeResult(val draws: List<StoredDraw>, val skipped: List<String>)

/**
 * Reads Lotto results from the official results history page on lottery.ie.
 *
 * The page is a Next.js site that embeds its data as JSON in <script id="__NEXT_DATA__">: for each
 * of the last few weeks of draws, the draw time, the six numbers, the bonus and what each prize tier
 * paid. Reading that JSON is much sturdier than reading the visible text. Every draw is checked
 * against the game rules for its date before it is used; anything odd is skipped.
 */
object LotteryIe {

    const val HISTORY_URL = "https://www.lottery.ie/results/lotto/history"
    private const val USER_AGENT = "SYNDICATE/1.0 (Android lotto syndicate organiser; fetches after each draw)"
    val IRELAND: ZoneId = ZoneId.of("Europe/Dublin")

    /** How lottery.ie names the tiers -> our tier codes (domain/Lotto.kt). */
    private val MATCH_TO_TIER = mapOf(
        "6" to "JACKPOT",
        "5+1" to "MATCH_5_BONUS",
        "5" to "MATCH_5",
        "4+1" to "MATCH_4_BONUS",
        "4" to "MATCH_4",
        "3+1" to "MATCH_3_BONUS",
        "3" to "MATCH_3",
        "2+1" to "MATCH_2_BONUS",
    )

    private val NEXT_DATA = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    /** Downloads the page. Call it off the main thread. */
    fun download(url: String = HISTORY_URL): String {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw ScrapeException("Could not reach lottery.ie.", e)
        }
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "text/html")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw ScrapeException("lottery.ie answered with error $code.")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            throw ScrapeException("Could not reach lottery.ie.", e)
        } finally {
            connection.disconnect()
        }
    }

    /** "€2,904,338" -> 290433800 cents. Null if it isn't an amount. */
    fun parseEuro(text: String): Long? {
        val value = text.replace("€", "").replace(",", "").trim().toBigDecimalOrNull() ?: return null
        return if (value.signum() >= 0) value.multiply(BigDecimal(100)).toLong() else null
    }

    fun parse(html: String): ScrapeResult {
        val match = NEXT_DATA.find(html) ?: throw ScrapeException("No results data on the page: lottery.ie may have changed it.")
        val items = try {
            Json.parseToJsonElement(match.groupValues[1])
                .jsonObject["props"]!!.jsonObject["pageProps"]!!.jsonObject["list"]!!.jsonArray
        } catch (e: Exception) {
            throw ScrapeException("The results data is not in the expected shape.", e)
        }
        val draws = mutableListOf<StoredDraw>()
        val skipped = mutableListOf<String>()
        items.forEachIndexed { index, item ->
            try {
                draws += draw(item.jsonObject["standard"]!!.jsonObject)
            } catch (e: Exception) {
                skipped += "entry $index: ${e.message}"
            }
        }
        return ScrapeResult(draws, skipped)
    }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

    private fun draw(game: JsonObject): StoredDraw {
        val title = game["gameTitle"].text() ?: "Lotto"
        require(title == "Lotto") { "not the main Lotto game ($title)" }
        // "2026-10-19T18:45:00.000Z": the date in Ireland, where the draw is held.
        val drawDate = Instant.parse(game["drawDates"]!!.jsonArray[0].jsonPrimitive.content).atZone(IRELAND).toLocalDate()
        val grid = game["grids"]!!.jsonArray[0].jsonObject
        val numbers = grid["standard"]!!.jsonArray[0].jsonArray.map { it.jsonPrimitive.content.toInt() }
        val bonus = grid["additional"]!!.jsonArray[0].jsonArray[0].jsonPrimitive.content.toInt()

        val rules = Lotto.rulesFor(drawDate)
        require(rules.isDrawDay(drawDate)) { "$drawDate is not a ${rules.name} draw day" }
        Lotto.validateNumbers(numbers, rules)?.let { throw IllegalArgumentException("$drawDate: $it") }
        require(bonus !in numbers && bonus in 1..rules.maxNumber) { "$drawDate: bad bonus $bonus" }

        val prizes = (game["prizes"] as? JsonArray).orEmpty().mapNotNull { element ->
            val prize = element as? JsonObject ?: return@mapNotNull null
            // A tier we don't know about is ignored rather than guessed.
            val tier = MATCH_TO_TIER[prize["match"].text()] ?: return@mapNotNull null
            val text = prize["prize"].text().orEmpty().trim()
            val cents = if ((prize["prizeType"].text() ?: "Cash") == "Cash") parseEuro(text) else null
            StoredPrize(tier, prize["numberOfWinners"].text()?.toIntOrNull() ?: 0, cents, text)
        }
        return StoredDraw(drawDate, numbers.sorted(), bonus, prizes)
    }
}
