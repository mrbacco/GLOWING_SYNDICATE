/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/data/ScreenBuilder.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.data

import com.mrbacco.syndicate.data.local.AppData
import com.mrbacco.syndicate.data.local.StoredDraw
import com.mrbacco.syndicate.data.local.StoredLine
import com.mrbacco.syndicate.data.local.StoredMember
import com.mrbacco.syndicate.data.local.StoredSyndicate
import com.mrbacco.syndicate.data.model.DrawCard
import com.mrbacco.syndicate.data.model.GameRules
import com.mrbacco.syndicate.data.model.LineResultRow
import com.mrbacco.syndicate.data.model.LineRow
import com.mrbacco.syndicate.data.model.LinesScreen
import com.mrbacco.syndicate.data.model.MemberRow
import com.mrbacco.syndicate.data.model.MembersScreen
import com.mrbacco.syndicate.data.model.ResultsScreen
import com.mrbacco.syndicate.data.model.SummaryCard
import com.mrbacco.syndicate.data.model.Syndicate
import com.mrbacco.syndicate.data.model.SyndicateListScreen
import com.mrbacco.syndicate.data.model.SyndicateRow
import com.mrbacco.syndicate.domain.DrawDay
import com.mrbacco.syndicate.domain.Ledger
import com.mrbacco.syndicate.domain.Lotto
import com.mrbacco.syndicate.domain.PhoneNumbers
import com.mrbacco.syndicate.domain.Texts
import com.mrbacco.syndicate.domain.Weeks
import com.mrbacco.syndicate.domain.Winnings
import java.time.LocalDate

/** Builds what each screen shows from the saved data: everything worked out and worded here. */
object ScreenBuilder {

    private const val PREVIOUS_DRAWS_SHOWN = 4

    // ------------------------------------------------------------- helpers

    private fun StoredSyndicate.days(): List<DrawDay> = DrawDay.entries.filter { it.code in drawDays }

    private fun StoredSyndicate.daysText(): String = days().joinToString(", ") { it.label }

    private fun StoredSyndicate.toModel() = Syndicate(id, name, weeklyCostCents, days().map { it.code })

    private fun AppData.membersOf(s: StoredSyndicate): List<StoredMember> = members.filter { it.syndicateId == s.id }

    private fun AppData.linesOf(s: StoredSyndicate): List<StoredLine> = lines.filter { it.syndicateId == s.id }

    private fun AppData.paidWeeks(m: StoredMember): Set<LocalDate> =
        payments.filter { it.memberId == m.id }.map { it.weekStart }.toSet()

    /** The draws on the syndicate's days, newest first. */
    private fun AppData.playedDraws(s: StoredSyndicate): List<StoredDraw> {
        val days = s.days()
        return draws.filter { DrawDay.of(it.drawDate) in days }
    }

    // -------------------------------------------------------------- screens

    fun rules(today: LocalDate): GameRules {
        val rules = Lotto.rulesFor(today)
        return GameRules(rules.name, rules.numbersPerLine, rules.maxNumber, rules.drawDays.map { it.label })
    }

    fun syndicateList(data: AppData) = SyndicateListScreen(
        data.syndicates.map {
            SyndicateRow(it.id, it.name, "${Texts.euro(it.weeklyCostCents.toLong())} per week · ${it.daysText()}")
        },
    )

    fun members(data: AppData, s: StoredSyndicate, today: LocalDate): MembersScreen {
        val split = data.playedDraws(s).firstOrNull()?.let { draw -> split(data, s, draw) }
        val rows = data.membersOf(s).map { member ->
            val b = Ledger.balance(member.joined, data.paidWeeks(member), s.weeklyCostCents, today)
            MemberRow(
                id = member.id,
                name = member.name,
                phone = member.phone,
                whatsappNumber = member.phone?.let { PhoneNumbers.international(it) },
                paidThisWeek = b.paidThisWeek,
                weeksOwed = b.weeksOwed,
                amountOwedCents = b.amountOwedCents,
                status = if (b.weeksOwed == 0) "All paid up"
                else "Owes ${Texts.euro(b.amountOwedCents)} (${Texts.weeks(b.weeksOwed)})",
                message = Texts.memberMessage(member.name, s.name, b.amountOwedCents, b.weeksOwed, winNote(split, member.id)),
            )
        }
        val thisWeek = Weeks.weekStart(today)
        return MembersScreen(
            syndicate = s.toModel(),
            weekStart = thisWeek.toString(),
            summary = SummaryCard(
                title = "Week of ${Texts.shortDate(thisWeek)}",
                lines = listOf(
                    "${rows.count { it.paidThisWeek }} of ${rows.size} members paid this week",
                    "Total owed: ${Texts.euro(rows.sumOf { it.amountOwedCents })}",
                ),
            ),
            members = rows,
            emptyText = if (rows.isEmpty()) "No members yet. Tap + to add the first one." else null,
        )
    }

    fun lines(data: AppData, s: StoredSyndicate): LinesScreen {
        val lines = data.linesOf(s)
        return LinesScreen(
            syndicate = s.toModel(),
            summary = SummaryCard("Lines played", listOf("${Texts.lines(lines.size)} every draw (${s.daysText()})")),
            lines = lines.map { LineRow(it.id, it.numbers) },
            emptyText = if (lines.isEmpty()) "No lines yet. Tap + to add the numbers you play." else null,
        )
    }

    fun results(data: AppData, s: StoredSyndicate): ResultsScreen {
        val played = data.playedDraws(s)
        val latest = played.firstOrNull() ?: return ResultsScreen(
            syndicate = s.toModel(),
            results = emptyList(),
            previousDraws = emptyList(),
            emptyText = "No draw results yet. They're fetched from lottery.ie after each draw.",
        )
        val rules = Lotto.rulesFor(latest.drawDate)
        val rows = data.linesOf(s)
            .map { it to Lotto.checkLine(it.numbers, latest.numbers, latest.bonus, rules) }
            .sortedWith(compareBy({ Lotto.tierRank(it.second, rules) }, { -it.second.matchCount }))
            .map { (line, result) ->
                LineResultRow(
                    lineId = line.id,
                    numbers = line.numbers,
                    matched = result.matched.sorted(),
                    bonusMatched = result.bonusMatched,
                    text = "Matched ${result.label}",
                    prizeTier = result.tier?.label,
                    prizeAmount = result.tier?.let { prizeAmount(latest, it.code) },
                    won = result.tier != null,
                )
            }
        val split = split(data, s, latest)
        val best = rows.firstOrNull()
        val verdict = when {
            best == null -> "Add your lines to check them automatically."
            best.prizeTier != null ->
                "Possible win: ${best.prizeTier}${best.prizeAmount?.let { ", $it" }.orEmpty()}! Check the ticket."
            else -> "No win this time."
        }
        return ResultsScreen(
            syndicate = s.toModel(),
            latestDraw = card(latest),
            verdict = verdict,
            results = rows,
            previousDraws = played.drop(1).take(PREVIOUS_DRAWS_SHOWN).map(::card),
            winnings = split?.let(::winningsCard),
            groupMessage = groupMessage(s, latest, rows, split),
        )
    }

    // ------------------------------------------------------------- results

    private fun card(draw: StoredDraw) = DrawCard(draw.drawDate.toString(), Texts.drawDate(draw.drawDate), draw.numbers, draw.bonus)

    /** What a tier paid per winning line in [draw], as published; null until it is known. */
    private fun prizeAmount(draw: StoredDraw, tierCode: String): String? {
        val prize = draw.prizeFor(tierCode) ?: return null
        return prize.prizeCents?.let(Texts::amount) ?: prize.prizeText.ifEmpty { null }
    }

    /** How the syndicate's winnings in [draw] are shared, or null if no line won anything. */
    fun split(data: AppData, s: StoredSyndicate, draw: StoredDraw): Winnings.Split? {
        val rules = Lotto.rulesFor(draw.drawDate)
        val prizes = data.linesOf(s).mapNotNull { line ->
            Lotto.checkLine(line.numbers, draw.numbers, draw.bonus, rules).tier?.let { tier ->
                draw.prizeFor(tier.code)?.let { Winnings.LinePrize(it.prizeCents, it.prizeText) } ?: Winnings.LinePrize()
            }
        }
        if (prizes.isEmpty()) return null
        val members = data.membersOf(s).map { Winnings.MemberIn(it.id, it.name, it.joined, data.paidWeeks(it)) }
        return Winnings.split(draw.drawDate, prizes, members)
    }

    /** One sentence for a member's message about the latest win, or null if there's nothing to say. */
    fun winNote(split: Winnings.Split?, memberId: String): String? {
        if (split == null) return null
        val whenText = "the ${Texts.drawDate(split.drawDate)} draw"
        val reason = split.leftOut.firstOrNull { it.id == memberId }?.reason
        if (reason == Winnings.JOINED_AFTER) return null // the win is nothing to do with them
        if (reason != null) {
            val won = if (split.allPublished && split.totalCents > 0) "${Texts.amount(split.totalCents)} " else ""
            return "We won ${won}in $whenText, but you $reason, so there's no share for you this time."
        }
        if (!split.allPublished) return "Good news: we won in $whenText! I'll send your share once the prize amounts are out."
        if (split.totalCents == 0L) {
            return if (split.nonCash.isNotEmpty()) "Good news: we won ${Texts.names(split.nonCash)} in $whenText." else null
        }
        return "Good news: we won ${Texts.amount(split.totalCents)} in $whenText, " +
            "and your share is ${Texts.euro(split.shareOf(memberId) ?: 0)}."
    }

    /** The latest result for the syndicate's group chat. It never names who paid or who owes. */
    private fun groupMessage(s: StoredSyndicate, draw: StoredDraw, rows: List<LineResultRow>, split: Winnings.Split?): String {
        val out = mutableListOf(
            "${s.name}: ${Texts.drawDate(draw.drawDate)} results",
            "Numbers: ${draw.numbers.joinToString(" ")} + bonus ${draw.bonus}",
        )
        val tiers = rows.mapNotNull { it.prizeTier }
        when {
            rows.isEmpty() -> out += "We don't have any lines entered yet."
            tiers.isEmpty() -> out += "No win this time across our ${Texts.lines(rows.size)}."
            split == null || !split.allPublished -> out += "We won (${Texts.names(tiers)})! The prize amount isn't published yet."
            else -> {
                val won = if (split.totalCents > 0) Texts.amount(split.totalCents) else Texts.names(split.nonCash)
                out += "We won $won (${Texts.names(tiers)})!"
                if (split.totalCents > 0 && split.sharers.isNotEmpty()) {
                    val count = split.sharers.size
                    val who = if (count == 1) "the 1 member" else "the $count members"
                    out += "Shared by $who who paid for the week of ${Texts.shortDate(split.week)}: ${Texts.euro(split.eachCents)} each."
                }
            }
        }
        out += "Reminder: it's ${Texts.euro(s.weeklyCostCents.toLong())} a week. If you haven't paid this week yet, please do. Thanks!"
        return out.joinToString("\n")
    }

    private fun winningsCard(split: Winnings.Split): SummaryCard {
        val linesWon = "${split.winningLines} winning ${if (split.winningLines == 1) "line" else "lines"}"
        val out = mutableListOf<String>()
        if (!split.allPublished) {
            out += "${linesWon.replaceFirstChar { it.uppercase() }}. The prize amounts aren't published yet, so the split will show once they are."
        } else {
            out += "Total: ${Texts.amount(split.totalCents)} from $linesWon"
            split.nonCash.forEach { out += "Also won: $it (not shared as cash)" }
            val week = Texts.shortDate(split.week)
            if (split.sharers.isEmpty()) {
                out += "No member had joined and paid for the week of $week, so there's no one to share it with yet."
            } else if (split.totalCents > 0) {
                val count = split.sharers.size
                out += "Shared by ${if (count == 1) "the 1 member" else "$count members"} who paid for the week of $week: " +
                    "${Texts.euro(split.eachCents)} each"
                out += Texts.names(split.sharers.map { it.name })
                if (split.leftOverCents > 0) out += "${Texts.cents(split.leftOverCents)} left over for the syndicate kitty"
            }
        }
        if (split.leftOut.isNotEmpty()) out += "Not included: " + split.leftOut.joinToString("; ") { "${it.name} (${it.reason})" }
        return SummaryCard("Winnings · ${Texts.drawDate(split.drawDate)}", out)
    }
}
