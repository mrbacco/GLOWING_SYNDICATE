/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/data/local/Stored.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.data.local

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

// Everything the app keeps, saved on the phone as one JSON file (LocalStore). Money is in cents,
// dates are "2026-11-09". Lists keep the order things were added in.

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

typealias Day = @Serializable(with = LocalDateSerializer::class) LocalDate

@Serializable
data class StoredSyndicate(
    val id: String,
    val name: String,
    val weeklyCostCents: Int,
    /** "MON", "WED", "SAT". */
    val drawDays: List<String>,
)

@Serializable
data class StoredMember(
    val id: String,
    val syndicateId: String,
    val name: String,
    val phone: String? = null,
    val joined: Day,
)

/** A member has paid for the week starting on [weekStart] (always a Monday). */
@Serializable
data class StoredPayment(val memberId: String, val weekStart: Day)

/** One line of numbers the syndicate plays every draw, sorted. */
@Serializable
data class StoredLine(val id: String, val syndicateId: String, val numbers: List<Int>)

/** What one prize tier paid in a draw, as published with the result. */
@Serializable
data class StoredPrize(
    /** e.g. "MATCH_3". */
    val tierCode: String,
    val winners: Int,
    /** Cash prize per winning line, or null when it isn't cash (e.g. a free ticket). */
    val prizeCents: Long? = null,
    /** As published, e.g. "€4". */
    val prizeText: String,
)

/** An official Lotto result: the main numbers (sorted) plus the bonus ball. Shared by all syndicates. */
@Serializable
data class StoredDraw(
    val drawDate: Day,
    val numbers: List<Int>,
    val bonus: Int,
    val prizes: List<StoredPrize> = emptyList(),
) {
    fun prizeFor(tierCode: String): StoredPrize? = prizes.firstOrNull { it.tierCode == tierCode }
}

@Serializable
data class AppData(
    /** Raised whenever the shape of this file changes, so older files can be converted. */
    val version: Int = CURRENT_VERSION,
    val syndicates: List<StoredSyndicate> = emptyList(),
    val members: List<StoredMember> = emptyList(),
    val payments: List<StoredPayment> = emptyList(),
    val lines: List<StoredLine> = emptyList(),
    /** Newest first. */
    val draws: List<StoredDraw> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
