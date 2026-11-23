/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/domain/Texts.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.domain

import java.time.LocalDate
import java.util.Locale

/** Text shown to people: money, dates and messages. */
object Texts {

    private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** 500 -> "€5.00". */
    fun euro(cents: Long): String = "€${cents / 100}.${"%02d".format(Locale.ROOT, cents % 100)}"

    /** A prize: whole euros with thousands separators when it has no cents. 290433800 -> "€2,904,338". */
    fun amount(cents: Long): String {
        val euros = String.format(Locale.UK, "%,d", cents / 100)
        return if (cents % 100 == 0L) "€$euros" else "€$euros.${"%02d".format(Locale.ROOT, cents % 100)}"
    }

    /** "Mon 19 Oct 2026". */
    fun drawDate(day: LocalDate): String =
        "${DAYS[day.dayOfWeek.value - 1]} ${day.dayOfMonth} ${MONTHS[day.monthValue - 1]} ${day.year}"

    /** "19 Oct". */
    fun shortDate(day: LocalDate): String = "${day.dayOfMonth} ${MONTHS[day.monthValue - 1]}"

    fun weeks(count: Int): String = if (count == 1) "1 week" else "$count weeks"

    fun lines(count: Int): String = if (count == 1) "1 line" else "$count lines"

    fun cents(count: Long): String = if (count == 1L) "1 cent" else "$count cents"

    /** ["Aoife", "Ciarán", "Niamh"] -> "Aoife, Ciarán and Niamh". */
    fun names(items: List<String>): String =
        if (items.size <= 1) items.joinToString("") else "${items.dropLast(1).joinToString(", ")} and ${items.last()}"

    /**
     * A message for one member, sent by SMS or WhatsApp: a payment reminder if they owe money,
     * otherwise a thank-you. [winNote] is a sentence about the latest win and their share.
     */
    fun memberMessage(name: String, syndicate: String, owedCents: Long, weeksOwed: Int, winNote: String? = null): String {
        val owes = "you owe ${euro(owedCents)} (${weeks(weeksOwed)})"
        return when {
            winNote != null && owedCents == 0L -> "Hi $name, news from $syndicate: $winNote You're all paid up, thanks!"
            winNote != null -> "Hi $name, news from $syndicate: $winNote A quick reminder too: $owes. Thanks!"
            owedCents == 0L -> "Hi $name, you're all paid up for $syndicate. Thanks!"
            else -> "Hi $name, a quick reminder for $syndicate: $owes. Thanks!"
        }
    }
}
