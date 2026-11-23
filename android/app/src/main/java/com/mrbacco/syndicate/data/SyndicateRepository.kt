/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/data/SyndicateRepository.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-10-20
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.data

import com.mrbacco.syndicate.data.local.AppData
import com.mrbacco.syndicate.data.local.LocalStore
import com.mrbacco.syndicate.data.local.StoredLine
import com.mrbacco.syndicate.data.local.StoredMember
import com.mrbacco.syndicate.data.local.StoredPayment
import com.mrbacco.syndicate.data.local.StoredSyndicate
import com.mrbacco.syndicate.data.local.UnreadableDataException
import com.mrbacco.syndicate.data.model.GameRules
import com.mrbacco.syndicate.data.model.LinesScreen
import com.mrbacco.syndicate.data.model.MembersScreen
import com.mrbacco.syndicate.data.model.ResultsScreen
import com.mrbacco.syndicate.data.model.SyndicateListScreen
import com.mrbacco.syndicate.data.results.LotteryIe
import com.mrbacco.syndicate.data.results.Results
import com.mrbacco.syndicate.data.results.ScrapeException
import com.mrbacco.syndicate.domain.Lotto
import com.mrbacco.syndicate.domain.PhoneNumbers
import com.mrbacco.syndicate.domain.Weeks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

/** Something went wrong, with a message that can be shown to the user as it is. */
class UserMessageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The single entry point the screens use for data. Everything is kept on the phone (LocalStore);
 * only the draw results come from the internet, fetched from lottery.ie when one is due.
 * Problems come back as [UserMessageException].
 */
class SyndicateRepository(
    private val store: LocalStore,
    private val downloadResults: () -> String = { LotteryIe.download() },
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(LotteryIe.IRELAND) },
) {
    private val today: LocalDate get() = now().toLocalDate()

    /** When results were last fetched (System.currentTimeMillis); 0 = not since the app started. */
    @Volatile private var lastFetchAttempt = 0L

    // ------------------------------------------------------------- screens

    suspend fun syndicateList(): SyndicateListScreen = ScreenBuilder.syndicateList(read())
    suspend fun membersScreen(syndicateId: String): MembersScreen = read().let { ScreenBuilder.members(it, it.syndicate(syndicateId), today) }
    suspend fun linesScreen(syndicateId: String): LinesScreen = read().let { ScreenBuilder.lines(it, it.syndicate(syndicateId)) }
    suspend fun resultsScreen(syndicateId: String): ResultsScreen = read().let { ScreenBuilder.results(it, it.syndicate(syndicateId)) }
    fun rules(): GameRules = ScreenBuilder.rules(today)

    // ------------------------------------------------------------- changes

    suspend fun addSyndicate(name: String, weeklyCostCents: Int) = update { data ->
        if (weeklyCostCents !in 1..MAX_WEEKLY_COST_CENTS) fail("Enter a weekly cost from €0.01 to €1,000.")
        val syndicate = StoredSyndicate(
            id = newId(),
            name = cleanName(name),
            weeklyCostCents = weeklyCostCents,
            drawDays = Lotto.rulesFor(today).drawDays.map { it.code },
        )
        data.copy(syndicates = data.syndicates + syndicate)
    }

    suspend fun addMember(syndicateId: String, name: String, phone: String?) = update { data ->
        val syndicate = data.syndicate(syndicateId)
        val member = StoredMember(newId(), syndicate.id, cleanName(name), cleanPhone(phone), joined = today)
        data.copy(members = data.members + member)
    }

    /** A null or empty [phone] removes the member's number. */
    suspend fun updateMember(memberId: String, name: String, phone: String?) = update { data ->
        val member = data.member(memberId)
        val changed = member.copy(name = cleanName(name), phone = cleanPhone(phone))
        data.copy(members = data.members.map { if (it.id == memberId) changed else it })
    }

    suspend fun deleteMember(memberId: String) = update { data ->
        data.member(memberId)
        data.copy(
            members = data.members.filterNot { it.id == memberId },
            payments = data.payments.filterNot { it.memberId == memberId },
        )
    }

    /** Marks the week containing [weekStart] ("2026-11-09") as paid or not. Doing it twice is fine. */
    suspend fun setPaid(memberId: String, weekStart: String, paid: Boolean) = update { data ->
        data.member(memberId)
        val week = Weeks.weekStart(LocalDate.parse(weekStart))
        val others = data.payments.filterNot { it.memberId == memberId && it.weekStart == week }
        data.copy(payments = if (paid) others + StoredPayment(memberId, week) else others)
    }

    suspend fun addLine(syndicateId: String, numbers: List<Int>) = update { data ->
        val syndicate = data.syndicate(syndicateId)
        data.copy(lines = data.lines + StoredLine(newId(), syndicate.id, checkNumbers(numbers)))
    }

    suspend fun updateLine(lineId: String, numbers: List<Int>) = update { data ->
        if (data.lines.none { it.id == lineId }) fail("That line no longer exists.")
        val checked = checkNumbers(numbers)
        data.copy(lines = data.lines.map { if (it.id == lineId) it.copy(numbers = checked) else it })
    }

    suspend fun deleteLine(lineId: String) = update { data ->
        data.copy(lines = data.lines.filterNot { it.id == lineId })
    }

    // ------------------------------------------------------------- results

    /**
     * Fetches the latest results from lottery.ie if one is due and not saved yet. Returns true if
     * anything new was saved. With [userAsked] it also fetches when nothing seems due (to pick up
     * prize amounts published later) and reports problems; otherwise it stays quiet and tries at
     * most every [AUTO_FETCH_GAP_MS], so lottery.ie is never hammered.
     */
    suspend fun fetchResults(userAsked: Boolean = false): Boolean {
        val data = read()
        val clock = System.currentTimeMillis()
        val gap = if (userAsked) USER_FETCH_GAP_MS else AUTO_FETCH_GAP_MS
        if (clock - lastFetchAttempt < gap) return false
        if (!userAsked && !Results.isDue(now(), data.draws.firstOrNull()?.drawDate)) return false
        lastFetchAttempt = clock
        val fetched = try {
            withContext(Dispatchers.IO) { LotteryIe.parse(downloadResults()) }
        } catch (e: ScrapeException) {
            if (userAsked) throw UserMessageException("Couldn't get the latest results from lottery.ie. Check your internet connection and try again.", e)
            return false
        }
        var changed = 0
        update { current -> Results.merge(current, fetched.draws).also { changed = it.second }.first }
        return changed > 0
    }

    // -------------------------------------------------------------- helpers

    private suspend fun read(): AppData = try {
        store.read()
    } catch (e: UnreadableDataException) {
        throw UserMessageException("Your saved data couldn't be read. It has been kept as it is; please contact support.", e)
    } catch (e: IOException) {
        throw UserMessageException("Your saved data couldn't be opened. Please try again.", e)
    }

    private suspend fun update(change: (AppData) -> AppData) {
        try {
            store.update(change)
        } catch (e: UnreadableDataException) {
            throw UserMessageException("Your saved data couldn't be read, so nothing was changed.", e)
        } catch (e: IOException) {
            throw UserMessageException("Couldn't save the change. Is the phone's storage full?", e)
        }
    }

    private fun AppData.syndicate(id: String): StoredSyndicate =
        syndicates.firstOrNull { it.id == id } ?: fail("That syndicate no longer exists.")

    private fun AppData.member(id: String): StoredMember =
        members.firstOrNull { it.id == id } ?: fail("That member no longer exists.")

    private fun checkNumbers(numbers: List<Int>): List<Int> {
        Lotto.validateNumbers(numbers, Lotto.rulesFor(today))?.let(::fail)
        return numbers.sorted()
    }

    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_PHONE_LENGTH = 30
        /** Up to €1,000 a week per member; anything more is a typo. */
        const val MAX_WEEKLY_COST_CENTS = 100_000
        const val AUTO_FETCH_GAP_MS = 15 * 60 * 1000L
        const val USER_FETCH_GAP_MS = 60 * 1000L

        private fun fail(message: String): Nothing = throw UserMessageException(message)

        private fun newId(): String = UUID.randomUUID().toString()

        internal fun cleanName(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) fail("Please enter a name.")
            if (trimmed.length > MAX_NAME_LENGTH) fail("Names can be up to $MAX_NAME_LENGTH characters.")
            return trimmed
        }

        /** Null for no number. */
        internal fun cleanPhone(phone: String?): String? {
            val trimmed = phone?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            if (trimmed.length > MAX_PHONE_LENGTH || PhoneNumbers.international(trimmed) == null) {
                fail("That doesn't look like a phone number.")
            }
            return trimmed
        }
    }
}
