/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/domain/Weeks.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Payments are tracked per week; a week always starts on Monday. */
object Weeks {

    fun weekStart(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - 1).toLong())

    /** Every week start from the week containing [start] up to and including the week containing [end]. */
    fun between(start: LocalDate, end: LocalDate): List<LocalDate> {
        val first = weekStart(start)
        val last = weekStart(end)
        val count = ChronoUnit.WEEKS.between(first, last) + 1
        return (0 until count.coerceAtLeast(0)).map { first.plusWeeks(it) }
    }
}

data class MemberBalance(val paidThisWeek: Boolean, val weeksOwed: Int, val amountOwedCents: Long)

/** Who owes what. A member owes for every week from the week they joined up to this week. */
object Ledger {
    fun balance(joined: LocalDate, paidWeeks: Set<LocalDate>, weeklyCostCents: Int, today: LocalDate): MemberBalance {
        val owed = Weeks.between(joined, today).count { it !in paidWeeks }
        return MemberBalance(
            paidThisWeek = Weeks.weekStart(today) in paidWeeks,
            weeksOwed = owed,
            amountOwedCents = owed.toLong() * weeklyCostCents,
        )
    }
}
