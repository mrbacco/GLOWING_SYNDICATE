/*
 * File:     android/app/src/test/java/com/mrbacco/syndicate/data/results/ResultsTest.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.data.results

import com.mrbacco.syndicate.data.local.AppData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** Pages shaped like lottery.ie's results history page (no network in tests). */
object SamplePages {

    const val PRIZES_17_OCT = """[
        {"match": "6", "prizeType": "Cash", "numberOfWinners": 0, "prize": "€2,765,876"},
        {"match": "5+1", "prizeType": "Cash", "numberOfWinners": 0, "prize": "€69,370"},
        {"match": "3", "prizeType": "Cash", "numberOfWinners": 4100, "prize": "€4"},
        {"match": "2+1", "prizeType": "Cash", "numberOfWinners": 3200, "prize": "€4"},
        {"match": "7", "prizeType": "Cash", "numberOfWinners": 1, "prize": "€1"}
    ]"""

    fun entry(whenUtc: String, numbers: List<Int>, bonus: Int, prizes: String = "[]", title: String = "Lotto") = """
        {"standard": {"gameTitle": "$title", "drawDates": ["$whenUtc"],
          "grids": [{"standard": [$numbers], "additional": [[$bonus]]}], "prizes": $prizes},
         "addonGames": [], "raffle": null}"""

    fun page(vararg entries: String) =
        """<html><body><script id="__NEXT_DATA__" type="application/json">{"props": {"pageProps": {"list": [${entries.joinToString(",")}], "pageSize": 5}}}</script></body></html>"""

    val SATURDAY_17 = entry("2026-10-17T18:45:00.000Z", listOf(43, 13, 21, 27, 38, 41), 42, PRIZES_17_OCT)
    val MONDAY_19 = entry("2026-10-19T18:45:00.000Z", listOf(19, 26, 30, 33, 34, 40), 14)
    val LATEST = page(MONDAY_19, SATURDAY_17)
}

class ResultsTest {

    private fun irish(text: String): ZonedDateTime = LocalDateTime.parse(text).atZone(LotteryIe.IRELAND)

    @Test
    fun parsesThePage() {
        val result = LotteryIe.parse(SamplePages.LATEST)
        assertEquals(emptyList<String>(), result.skipped)
        val saturday = result.draws.first { it.drawDate == LocalDate.of(2026, 10, 17) }
        assertEquals(listOf(13, 21, 27, 38, 41, 43), saturday.numbers) // sorted
        assertEquals(42, saturday.bonus)
        assertEquals(276587600L, saturday.prizeFor("JACKPOT")?.prizeCents)
        assertEquals(400L, saturday.prizeFor("MATCH_3")?.prizeCents)
        assertEquals(3200, saturday.prizeFor("MATCH_2_BONUS")?.winners)
        assertEquals(4, saturday.prizes.size) // the unknown "7" tier is ignored
    }

    @Test
    fun drawTimeInUtcGivesTheIrishDate() {
        // 23:30 UTC on Saturday 31 October is still Saturday in Ireland (winter time, UTC+0)...
        val winter = LotteryIe.parse(SamplePages.page(SamplePages.entry("2026-10-31T23:30:00.000Z", listOf(1, 2, 3, 4, 5, 6), 7)))
        assertEquals(LocalDate.of(2026, 10, 31), winter.draws.single().drawDate)
        // ...and 23:30 UTC on Friday 2 October is already Saturday 3 October in Irish summer time.
        val summer = LotteryIe.parse(SamplePages.page(SamplePages.entry("2026-10-02T23:30:00.000Z", listOf(1, 2, 3, 4, 5, 6), 7)))
        assertEquals(LocalDate.of(2026, 10, 3), summer.draws.single().drawDate)
    }

    @Test
    fun skipsEntriesThatBreakTheRules() {
        val result = LotteryIe.parse(
            SamplePages.page(
                SamplePages.entry("2026-10-19T18:45:00.000Z", listOf(1, 2, 3, 4, 5, 46), 7), // 46 after the change to 45 balls
                SamplePages.entry("2026-10-20T18:45:00.000Z", listOf(1, 2, 3, 4, 5, 6), 7), // a Tuesday
                SamplePages.entry("2026-10-17T18:45:00.000Z", listOf(1, 2, 3, 4, 5, 6), 6), // bonus is a main number
                SamplePages.entry("2026-10-17T18:45:00.000Z", listOf(1, 2, 3, 4, 5, 6), 7, title = "Lotto Plus 1"),
                SamplePages.SATURDAY_17,
            ),
        )
        assertEquals(1, result.draws.size)
        assertEquals(4, result.skipped.size)
    }

    @Test
    fun old47BallDrawsAreStillValid() {
        val result = LotteryIe.parse(SamplePages.page(SamplePages.entry("2026-08-29T18:45:00.000Z", listOf(1, 2, 3, 4, 5, 47), 46)))
        assertEquals(1, result.draws.size)
    }

    @Test(expected = ScrapeException::class)
    fun pageWithoutDataIsAnError() {
        LotteryIe.parse("<html>Maintenance</html>")
    }

    @Test(expected = ScrapeException::class)
    fun dataInTheWrongShapeIsAnError() {
        LotteryIe.parse("""<script id="__NEXT_DATA__" type="application/json">{"props": {}}</script>""")
    }

    @Test
    fun parseEuro() {
        assertEquals(290433800L, LotteryIe.parseEuro("€2,904,338"))
        assertEquals(400L, LotteryIe.parseEuro("€4"))
        assertEquals(350L, LotteryIe.parseEuro("€3.50"))
        assertNull(LotteryIe.parseEuro("Free ticket"))
    }

    @Test
    fun mergeAddsCorrectsAndSkipsUnchanged() {
        val first = LotteryIe.parse(SamplePages.LATEST).draws
        val (data, added) = Results.merge(AppData(), first)
        assertEquals(2, added)
        assertEquals(LocalDate.of(2026, 10, 19), data.draws.first().drawDate) // newest first

        val (same, none) = Results.merge(data, first)
        assertEquals(0, none)
        assertSame(data, same)

        // Prize amounts published later count as a change.
        val monday = first.first { it.drawDate == LocalDate.of(2026, 10, 19) }
        val withPrizes = monday.copy(prizes = first.first { it.drawDate == LocalDate.of(2026, 10, 17) }.prizes)
        val (updated, changed) = Results.merge(data, listOf(withPrizes))
        assertEquals(1, changed)
        assertEquals(2, updated.draws.size)
        assertEquals(4, updated.draws.first().prizes.size)
    }

    @Test
    fun whichDrawIsDue() {
        // Monday 19 Oct before 20:30: the latest due draw is Saturday's.
        assertEquals(LocalDate.of(2026, 10, 17), Results.latestDueDraw(irish("2026-10-19T20:00")))
        assertEquals(LocalDate.of(2026, 10, 19), Results.latestDueDraw(irish("2026-10-19T20:30")))
        assertEquals(LocalDate.of(2026, 10, 19), Results.latestDueDraw(irish("2026-10-20T09:00")))

        assertTrue(Results.isDue(irish("2026-10-19T21:00"), LocalDate.of(2026, 10, 17)))
        assertFalse(Results.isDue(irish("2026-10-19T21:00"), LocalDate.of(2026, 10, 19)))
        assertFalse(Results.isDue(irish("2026-10-19T20:00"), LocalDate.of(2026, 10, 17)))
        assertTrue(Results.isDue(irish("2026-10-19T20:00"), null))
    }
}
