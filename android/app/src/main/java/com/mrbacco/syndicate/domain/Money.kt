/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/domain/Money.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-10-20
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.domain

/** Reads amounts typed by the user. Amounts shown on screen are worded by Texts. */
object Money {

    /** "5", "5.5", "5,50" or "€5.00" -> 500 cents. Returns null for invalid or non-positive amounts. */
    fun parseEuro(text: String): Int? {
        val cleaned = text.trim().removePrefix("€").trim().replace(',', '.')
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0 || value.scale() > 2) return null
        return value.movePointRight(2).toInt()
    }
}
