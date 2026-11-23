/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/domain/Winnings.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.domain

import java.time.LocalDate

/**
 * Who shares a win, and how much each gets.
 *
 * A member shares in a draw's winnings if they had joined by the draw and had paid for the week the
 * draw fell in. The cash prizes of all winning lines are added up and divided equally; cents that
 * can't be divided evenly are left over for the syndicate's kitty. Prizes that aren't cash (such as a
 * free ticket) are listed but not divided.
 */
object Winnings {

    data class MemberIn(val id: String, val name: String, val joined: LocalDate, val paidWeeks: Set<LocalDate>)

    /** What one winning line won: cash in cents, or a non-cash prize's text. Both null = not published yet. */
    data class LinePrize(val cents: Long? = null, val text: String? = null) {
        val published: Boolean get() = cents != null || !text.isNullOrEmpty()
    }

    data class LeftOut(val id: String, val name: String, val reason: String)

    data class Split(
        val drawDate: LocalDate,
        val winningLines: Int,
        val totalCents: Long,
        val nonCash: List<String>,
        val allPublished: Boolean,
        val sharers: List<MemberIn>,
        val leftOut: List<LeftOut>,
        val eachCents: Long,
        val leftOverCents: Long,
    ) {
        val week: LocalDate get() = Weeks.weekStart(drawDate)

        /** A member's share in cents, or null if they don't share in this draw. */
        fun shareOf(memberId: String): Long? = if (sharers.any { it.id == memberId }) eachCents else null
    }

    const val JOINED_AFTER = "joined after this draw"
    const val NOT_PAID = "hadn't paid for that week"

    fun whyLeftOut(member: MemberIn, drawDate: LocalDate): String? = when {
        member.joined > drawDate -> JOINED_AFTER
        Weeks.weekStart(drawDate) !in member.paidWeeks -> NOT_PAID
        else -> null
    }

    /** [prizes] has one entry per winning line. */
    fun split(drawDate: LocalDate, prizes: List<LinePrize>, members: List<MemberIn>): Split {
        var total = 0L
        val nonCash = mutableListOf<String>()
        var allPublished = true
        for (prize in prizes) {
            when {
                !prize.published -> allPublished = false
                prize.cents != null -> total += prize.cents
                else -> nonCash += prize.text.orEmpty()
            }
        }
        val sharers = mutableListOf<MemberIn>()
        val leftOut = mutableListOf<LeftOut>()
        for (member in members) {
            val reason = whyLeftOut(member, drawDate)
            if (reason != null) leftOut += LeftOut(member.id, member.name, reason) else sharers += member
        }
        val divide = sharers.isNotEmpty() && allPublished
        return Split(
            drawDate = drawDate,
            winningLines = prizes.size,
            totalCents = total,
            nonCash = nonCash,
            allPublished = allPublished,
            sharers = sharers,
            leftOut = leftOut,
            eachCents = if (divide) total / sharers.size else 0,
            leftOverCents = if (divide) total % sharers.size else 0,
        )
    }
}
