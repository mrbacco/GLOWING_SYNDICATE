/*
 * File:     android/app/src/test/java/com/mrbacco/syndicate/domain/DomainTest.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainTest {

    private val rules = Lotto.LOTTO_6_45
    private val draw = listOf(3, 12, 24, 27, 38, 41)
    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun countsMatchesAndBonus() {
        val result = Lotto.checkLine(listOf(3, 12, 24, 1, 2, 9), draw, 9, rules)
        assertEquals(setOf(3, 12, 24), result.matched)
        assertTrue(result.bonusMatched)
        assertEquals("MATCH_3_BONUS", result.tier?.code)
        assertEquals("3 numbers + bonus", result.label)
    }

    @Test
    fun labels() {
        assertEquals("no numbers", Lotto.checkLine(listOf(1, 2, 4, 5, 6, 7), draw, 9, rules).label)
        assertEquals("1 number", Lotto.checkLine(listOf(3, 2, 4, 5, 6, 7), draw, 9, rules).label)
        assertEquals("the bonus ball only", Lotto.checkLine(listOf(9, 2, 4, 5, 6, 7), draw, 9, rules).label)
    }

    @Test
    fun picksThePrizeTier() {
        assertEquals("JACKPOT", Lotto.checkLine(draw, draw, 9, rules).tier?.code)
        assertEquals("MATCH_5_BONUS", Lotto.checkLine(listOf(3, 12, 24, 27, 38, 9), draw, 9, rules).tier?.code)
        assertEquals("MATCH_4", Lotto.checkLine(listOf(3, 12, 24, 27, 1, 2), draw, 9, rules).tier?.code)
        // Two numbers without the bonus, or one number with it, wins nothing.
        assertNull(Lotto.checkLine(listOf(3, 12, 1, 2, 4, 5), draw, 9, rules).tier)
        assertNull(Lotto.checkLine(listOf(3, 9, 1, 2, 4, 5), draw, 9, rules).tier)
    }

    @Test
    fun bonusTierRanksAboveSameCountWithoutBonus() {
        val withBonus = Lotto.checkLine(listOf(3, 12, 24, 1, 2, 9), draw, 9, rules)
        val without = Lotto.checkLine(listOf(3, 12, 24, 1, 2, 4), draw, 9, rules)
        assertTrue(Lotto.tierRank(withBonus, rules) < Lotto.tierRank(without, rules))
        assertEquals(rules.tiers.size, Lotto.tierRank(Lotto.checkLine(listOf(1, 2, 4, 5, 6, 7), draw, 9, rules), rules))
    }

    @Test
    fun rulesChangeOn5September2026() {
        assertEquals("Lotto 6/47", Lotto.rulesFor(date("2026-09-02")).name)
        assertEquals("Lotto 6/45", Lotto.rulesFor(date("2026-09-03")).name) // in between: the newer game
        assertEquals("Lotto 6/45", Lotto.rulesFor(date("2026-09-05")).name)
        assertFalse(Lotto.LOTTO_6_47.isDrawDay(date("2026-08-31"))) // a Monday
        assertTrue(Lotto.LOTTO_6_45.isDrawDay(date("2026-10-19"))) // a Monday
    }

    @Test
    fun validatesLines() {
        assertNull(Lotto.validateNumbers(listOf(1, 2, 3, 4, 5, 45), rules))
        assertEquals("Enter 6 different numbers.", Lotto.validateNumbers(listOf(1, 2, 3, 4, 5), rules))
        assertEquals("Enter 6 different numbers.", Lotto.validateNumbers(listOf(1, 2, 3, 4, 5, 5), rules))
        assertEquals("Numbers must be from 1 to 45.", Lotto.validateNumbers(listOf(1, 2, 3, 4, 5, 46), rules))
        assertNull(Lotto.validateNumbers(listOf(1, 2, 3, 4, 5, 47), Lotto.LOTTO_6_47))
    }

    @Test
    fun weeksStartOnMonday() {
        assertEquals(date("2026-11-09"), Weeks.weekStart(date("2026-11-09")))
        assertEquals(date("2026-11-09"), Weeks.weekStart(date("2026-11-15")))
        assertEquals(
            listOf(date("2026-10-26"), date("2026-11-02"), date("2026-11-09")),
            Weeks.between(date("2026-10-28"), date("2026-11-10")),
        )
        assertEquals(emptyList<LocalDate>(), Weeks.between(date("2026-11-10"), date("2026-10-28")))
    }

    @Test
    fun balance() {
        val paid = setOf(date("2026-10-26"))
        val b = Ledger.balance(date("2026-10-28"), paid, 500, date("2026-11-10"))
        assertFalse(b.paidThisWeek)
        assertEquals(2, b.weeksOwed)
        assertEquals(1000L, b.amountOwedCents)
    }

    @Test
    fun texts() {
        assertEquals("€5.00", Texts.euro(500))
        assertEquals("€0.05", Texts.euro(5))
        assertEquals("€2,904,338", Texts.amount(290433800))
        assertEquals("€1,234.50", Texts.amount(123450))
        assertEquals("Mon 19 Oct 2026", Texts.drawDate(date("2026-10-19")))
        assertEquals("19 Oct", Texts.shortDate(date("2026-10-19")))
        assertEquals("Aoife, Ciarán and Niamh", Texts.names(listOf("Aoife", "Ciarán", "Niamh")))
        assertEquals("Aoife", Texts.names(listOf("Aoife")))
    }

    @Test
    fun memberMessages() {
        assertEquals("Hi Aoife, you're all paid up for Office. Thanks!", Texts.memberMessage("Aoife", "Office", 0, 0))
        assertEquals(
            "Hi Aoife, a quick reminder for Office: you owe €10.00 (2 weeks). Thanks!",
            Texts.memberMessage("Aoife", "Office", 1000, 2),
        )
        assertTrue(Texts.memberMessage("Aoife", "Office", 500, 1, "Good news!").contains("Good news! A quick reminder too"))
    }

    // ------------------------------------------------------------ winnings

    private val week = date("2026-10-19")
    private fun member(id: String, joined: String = "2026-10-01", paid: Boolean = true) =
        Winnings.MemberIn(id, id, date(joined), if (paid) setOf(week) else emptySet())

    @Test
    fun splitWithLeftOverCents() {
        val split = Winnings.split(date("2026-10-21"), listOf(Winnings.LinePrize(1000), Winnings.LinePrize(400)), listOf(member("a"), member("b"), member("c")))
        assertEquals(1400L, split.totalCents)
        assertEquals(466L, split.eachCents)
        assertEquals(2L, split.leftOverCents)
        assertEquals(466L, split.shareOf("a"))
    }

    @Test
    fun unpaidAndLateJoinersAreLeftOut() {
        val split = Winnings.split(
            date("2026-10-21"),
            listOf(Winnings.LinePrize(1000)),
            listOf(member("a"), member("unpaid", paid = false), member("late", joined = "2026-10-22")),
        )
        assertEquals(listOf("a"), split.sharers.map { it.id })
        assertEquals(listOf(Winnings.NOT_PAID, Winnings.JOINED_AFTER), split.leftOut.map { it.reason })
        assertNull(split.shareOf("unpaid"))
        assertEquals(1000L, split.eachCents)
    }

    @Test
    fun unpublishedAmountsAreNotSplit() {
        val split = Winnings.split(date("2026-10-21"), listOf(Winnings.LinePrize(400), Winnings.LinePrize()), listOf(member("a")))
        assertFalse(split.allPublished)
        assertEquals(0L, split.eachCents)
    }

    @Test
    fun nonCashPrizesAreListedNotDivided() {
        val split = Winnings.split(date("2026-10-21"), listOf(Winnings.LinePrize(text = "Free ticket")), listOf(member("a")))
        assertEquals(listOf("Free ticket"), split.nonCash)
        assertEquals(0L, split.totalCents)
    }
}
