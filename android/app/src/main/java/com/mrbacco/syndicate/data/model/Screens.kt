/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/data/model/Screens.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-10-20
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.data.model

// What each screen shows, worked out and worded by ScreenBuilder, so the screens only display it.

data class Syndicate(
    val id: String,
    val name: String,
    val weeklyCostCents: Int,
    /** "MON", "WED", "SAT". */
    val drawDays: List<String>,
)

data class SummaryCard(val title: String, val lines: List<String>)

// ------------------------------------------------------------ My syndicates

data class SyndicateRow(val id: String, val name: String, val subtitle: String)

data class SyndicateListScreen(val syndicates: List<SyndicateRow>)

// ------------------------------------------------------------------ Members

data class MemberRow(
    val id: String,
    val name: String,
    val phone: String? = null,
    /** International digits for WhatsApp links, e.g. "353871234567". */
    val whatsappNumber: String? = null,
    val paidThisWeek: Boolean,
    val weeksOwed: Int,
    val amountOwedCents: Long,
    /** "All paid up" or "Owes €5.00 (1 week)". */
    val status: String,
    /** Ready to send by SMS or WhatsApp. */
    val message: String,
)

data class MembersScreen(
    val syndicate: Syndicate,
    /** The Monday of this week, e.g. "2026-10-19"; payments are recorded against it. */
    val weekStart: String,
    val summary: SummaryCard,
    val members: List<MemberRow>,
    val emptyText: String? = null,
)

// -------------------------------------------------------------------- Lines

data class LineRow(val id: String, val numbers: List<Int>)

data class LinesScreen(
    val syndicate: Syndicate,
    val summary: SummaryCard,
    val lines: List<LineRow>,
    val emptyText: String? = null,
)

// ------------------------------------------------------------------ Results

data class DrawCard(
    val drawDate: String,
    /** "Mon 19 Oct 2026". */
    val title: String,
    val numbers: List<Int>,
    val bonus: Int,
)

data class LineResultRow(
    val lineId: String,
    val numbers: List<Int>,
    val matched: List<Int>,
    val bonusMatched: Boolean,
    /** "Matched 3 numbers + bonus". */
    val text: String,
    /** "Match 3 + Bonus", or null if the line won nothing. */
    val prizeTier: String? = null,
    /** What that tier paid per line, e.g. "€25", once lottery.ie has published it. */
    val prizeAmount: String? = null,
    val won: Boolean,
)

data class ResultsScreen(
    val syndicate: Syndicate,
    val latestDraw: DrawCard? = null,
    val verdict: String? = null,
    val results: List<LineResultRow>,
    val previousDraws: List<DrawCard>,
    /** Who shares the latest draw's winnings and how much each gets; null if nothing was won. */
    val winnings: SummaryCard? = null,
    /** The latest result for the syndicate's group chat; it names nobody. */
    val groupMessage: String? = null,
    val emptyText: String? = null,
)

// -------------------------------------------------------------------- Rules

/** The Lotto rules today; typed lines are checked against them. */
data class GameRules(
    val name: String,
    val numbersPerLine: Int,
    val maxNumber: Int,
    val drawDayLabels: List<String>,
)
