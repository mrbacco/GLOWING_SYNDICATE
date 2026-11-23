/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/data/results/Results.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.data.results

import com.mrbacco.syndicate.data.local.AppData
import com.mrbacco.syndicate.data.local.StoredDraw
import com.mrbacco.syndicate.domain.Lotto
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** When a result is expected, and how fetched results are saved. */
object Results {

    /** Lotto is drawn at 19:45 Irish time; from 20:30 on a draw day the result is "due". */
    val RESULT_DUE: LocalTime = LocalTime.of(20, 30)

    /** The most recent draw whose result should be published by [now] (Irish time). */
    fun latestDueDraw(now: ZonedDateTime): LocalDate? {
        val today = now.toLocalDate()
        return (0L..7L).map { today.minusDays(it) }.firstOrNull { day ->
            Lotto.rulesFor(day).isDrawDay(day) && (day < today || now.toLocalTime() >= RESULT_DUE)
        }
    }

    /** True if a result should be out by [now] but isn't saved yet. */
    fun isDue(now: ZonedDateTime, latestStored: LocalDate?): Boolean {
        val expected = latestDueDraw(now) ?: return false
        return latestStored == null || latestStored < expected
    }

    /**
     * Adds fetched draws to [data]: the official results win, adding missing draws and correcting
     * changed ones (prize amounts are often published after the numbers). Returns the new data and
     * how many draws were added or changed.
     */
    fun merge(data: AppData, fetched: List<StoredDraw>): Pair<AppData, Int> {
        val byDate = data.draws.associateBy { it.drawDate }.toMutableMap()
        var changed = 0
        for (draw in fetched) {
            val stored = byDate[draw.drawDate]
            if (stored == null || stored.numbers != draw.numbers || stored.bonus != draw.bonus ||
                stored.prizes.toSet() != draw.prizes.toSet()
            ) {
                byDate[draw.drawDate] = draw
                changed++
            }
        }
        if (changed == 0) return data to 0
        return data.copy(draws = byDate.values.sortedByDescending { it.drawDate }) to changed
    }
}
