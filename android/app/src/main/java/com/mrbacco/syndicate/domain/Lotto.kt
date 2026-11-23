/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/domain/Lotto.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Irish Lotto game rules and line checking.
 *
 * The rules change over time (47 balls on Wed/Sat until 2 September 2026, then 45 balls on
 * Mon/Wed/Sat), so every draw is checked against the rules that applied on its date.
 */
enum class DrawDay(val code: String, val label: String, val dayOfWeek: DayOfWeek) {
    MONDAY("MON", "Mon", DayOfWeek.MONDAY),
    WEDNESDAY("WED", "Wed", DayOfWeek.WEDNESDAY),
    SATURDAY("SAT", "Sat", DayOfWeek.SATURDAY);

    companion object {
        fun of(day: LocalDate): DrawDay? = entries.firstOrNull { it.dayOfWeek == day.dayOfWeek }
        fun fromCode(code: String): DrawDay? = entries.firstOrNull { it.code == code }
    }
}

data class PrizeTier(
    val code: String,
    val label: String,
    val matches: Int,
    val bonus: Boolean,
    /** Fixed prizes are the same for every winner; the others share a prize pool. */
    val fixedPrizeCents: Long? = null,
)

data class LottoGame(
    val name: String,
    val firstDraw: LocalDate?,
    val lastDraw: LocalDate?,
    val maxNumber: Int,
    val drawDays: List<DrawDay>,
    /** Best first. */
    val tiers: List<PrizeTier>,
    val numbersPerLine: Int = 6,
) {
    fun covers(day: LocalDate): Boolean =
        (firstDraw == null || day >= firstDraw) && (lastDraw == null || day <= lastDraw)

    fun isDrawDay(day: LocalDate): Boolean = DrawDay.of(day) in drawDays
}

data class LineResult(val matched: Set<Int>, val bonusMatched: Boolean, val tier: PrizeTier?) {
    val matchCount: Int get() = matched.size

    /** Short description such as "3 numbers + bonus". */
    val label: String
        get() {
            if (matchCount == 0 && bonusMatched) return "the bonus ball only"
            val numbers = when (matchCount) {
                0 -> "no numbers"
                1 -> "1 number"
                else -> "$matchCount numbers"
            }
            return if (bonusMatched) "$numbers + bonus" else numbers
        }
}

object Lotto {

    private fun tiers(match3Cents: Long?, match2BonusCents: Long) = listOf(
        PrizeTier("JACKPOT", "Jackpot", 6, false),
        PrizeTier("MATCH_5_BONUS", "Match 5 + Bonus", 5, true),
        PrizeTier("MATCH_5", "Match 5", 5, false),
        PrizeTier("MATCH_4_BONUS", "Match 4 + Bonus", 4, true),
        PrizeTier("MATCH_4", "Match 4", 4, false),
        PrizeTier("MATCH_3_BONUS", "Match 3 + Bonus", 3, true),
        PrizeTier("MATCH_3", "Match 3", 3, false, match3Cents),
        PrizeTier("MATCH_2_BONUS", "Match 2 + Bonus", 2, true, match2BonusCents),
    )

    /** Lotto 6/47 Rules, Issue 9 (29 October 2024). Match 2 + Bonus paid a €3 Quick Pick, or €3 cash in shops. */
    val LOTTO_6_47 = LottoGame(
        name = "Lotto 6/47",
        firstDraw = null,
        lastDraw = LocalDate.of(2026, 9, 2),
        maxNumber = 47,
        drawDays = listOf(DrawDay.WEDNESDAY, DrawDay.SATURDAY),
        tiers = tiers(match3Cents = null, match2BonusCents = 300),
    )

    /** Lotto 6/45 Rules, Issue 10 (3 September 2026). */
    val LOTTO_6_45 = LottoGame(
        name = "Lotto 6/45",
        firstDraw = LocalDate.of(2026, 9, 5),
        lastDraw = null,
        maxNumber = 45,
        drawDays = listOf(DrawDay.MONDAY, DrawDay.WEDNESDAY, DrawDay.SATURDAY),
        tiers = tiers(match3Cents = 400, match2BonusCents = 400),
    )

    private val ALL = listOf(LOTTO_6_47, LOTTO_6_45)

    /** The rules for a draw on [day]. Days between two games use the newer game. */
    fun rulesFor(day: LocalDate): LottoGame = ALL.firstOrNull { it.covers(day) } ?: ALL.last()

    /** An error message, or null if [numbers] is a valid line under [rules]. */
    fun validateNumbers(numbers: List<Int>, rules: LottoGame): String? {
        val n = rules.numbersPerLine
        if (numbers.size != n || numbers.toSet().size != n) return "Enter $n different numbers."
        if (numbers.any { it !in 1..rules.maxNumber }) return "Numbers must be from 1 to ${rules.maxNumber}."
        return null
    }

    fun checkLine(numbers: List<Int>, drawNumbers: List<Int>, bonus: Int, rules: LottoGame): LineResult {
        val matched = numbers.toSet() intersect drawNumbers.toSet()
        val bonusMatched = bonus in numbers
        // A bonus match only counts where a tier has one.
        val tier = rules.tiers.firstOrNull { it.matches == matched.size && (bonusMatched || !it.bonus) }
        return LineResult(matched, bonusMatched, tier)
    }

    /** Sort key: best prize first, lines with no prize last. */
    fun tierRank(result: LineResult, rules: LottoGame): Int =
        result.tier?.let { rules.tiers.indexOf(it) } ?: rules.tiers.size
}
