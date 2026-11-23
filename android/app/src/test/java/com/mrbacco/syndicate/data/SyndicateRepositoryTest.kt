/*
 * File:     android/app/src/test/java/com/mrbacco/syndicate/data/SyndicateRepositoryTest.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.data

import com.mrbacco.syndicate.data.local.LocalStore
import com.mrbacco.syndicate.data.results.LotteryIe
import com.mrbacco.syndicate.data.results.SamplePages
import com.mrbacco.syndicate.data.results.ScrapeException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime

class SyndicateRepositoryTest {

    @get:Rule val folder = TemporaryFolder()

    /** Monday 19 October 2026, 21:00 in Ireland: the evening's draw result is due. */
    private val monday = LocalDateTime.parse("2026-10-19T21:00").atZone(LotteryIe.IRELAND)

    private val prizes = """[{"match": "3", "prizeType": "Cash", "numberOfWinners": 4100, "prize": "€4"}]"""
    private val page = SamplePages.page(
        SamplePages.entry("2026-10-19T18:45:00.000Z", listOf(19, 26, 30, 33, 34, 40), 14, prizes),
        SamplePages.SATURDAY_17,
    )

    private fun file() = File(folder.root, LocalStore.FILE_NAME)

    private fun repository(download: () -> String = { page }) =
        SyndicateRepository(LocalStore(file()), downloadResults = download, now = { monday })

    private fun expectMessage(expected: String, block: suspend () -> Unit) = runBlocking {
        try {
            block()
            fail("Expected \"$expected\"")
        } catch (e: UserMessageException) {
            assertEquals(expected, e.message)
        }
    }

    @Test
    fun startsEmpty() = runBlocking {
        assertEquals(0, repository().syndicateList().syndicates.size)
        assertFalse(file().exists()) // nothing is written until something changes
    }

    @Test
    fun keepsEverythingAfterARestart() = runBlocking {
        val repo = repository()
        repo.addSyndicate("  Office  ", 500)
        val id = repo.syndicateList().syndicates.single().id
        repo.addMember(id, "Aoife", "087 123 4567")
        repo.addLine(id, listOf(41, 3, 12, 24, 27, 38))

        val reopened = repository()
        val row = reopened.syndicateList().syndicates.single()
        assertEquals("Office", row.name)
        assertEquals("€5.00 per week · Mon, Wed, Sat", row.subtitle)
        val member = reopened.membersScreen(id).members.single()
        assertEquals("353871234567", member.whatsappNumber)
        assertEquals("Owes €5.00 (1 week)", member.status)
        assertEquals(listOf(3, 12, 24, 27, 38, 41), reopened.linesScreen(id).lines.single().numbers)
    }

    @Test
    fun paymentsAndMembers() = runBlocking {
        val repo = repository()
        repo.addSyndicate("Office", 500)
        val id = repo.syndicateList().syndicates.single().id
        repo.addMember(id, "Aoife", null)
        val screen = repo.membersScreen(id)
        val memberId = screen.members.single().id
        assertEquals("2026-10-19", screen.weekStart)

        repo.setPaid(memberId, screen.weekStart, true)
        repo.setPaid(memberId, screen.weekStart, true) // twice is fine
        val paid = repo.membersScreen(id)
        assertTrue(paid.members.single().paidThisWeek)
        assertEquals("All paid up", paid.members.single().status)
        assertEquals(listOf("1 of 1 members paid this week", "Total owed: €0.00"), paid.summary.lines)

        repo.setPaid(memberId, screen.weekStart, false)
        assertFalse(repo.membersScreen(id).members.single().paidThisWeek)

        repo.updateMember(memberId, "Aoife B", "")
        assertEquals("Aoife B", repo.membersScreen(id).members.single().name)
        repo.deleteMember(memberId)
        assertEquals(0, repo.membersScreen(id).members.size)
        assertNotNull(repo.membersScreen(id).emptyText)
    }

    @Test
    fun rejectsBadInput() {
        val repo = repository()
        expectMessage("Please enter a name.") { repo.addSyndicate("   ", 500) }
        expectMessage("Enter a weekly cost from €0.01 to €1,000.") { repo.addSyndicate("Office", 100_001) }
        runBlocking { repo.addSyndicate("Office", 500) }
        val id = runBlocking { repo.syndicateList().syndicates.single().id }
        expectMessage("That doesn't look like a phone number.") { repo.addMember(id, "Aoife", "call me") }
        expectMessage("Numbers must be from 1 to 45.") { repo.addLine(id, listOf(1, 2, 3, 4, 5, 46)) }
        expectMessage("Enter 6 different numbers.") { repo.addLine(id, listOf(1, 2, 3, 4, 5, 5)) }
        expectMessage("That syndicate no longer exists.") { repo.addMember("missing", "Aoife", null) }
    }

    @Test
    fun fetchesResultsAndSharesTheWinnings() = runBlocking {
        val repo = repository()
        repo.addSyndicate("Office", 500)
        val id = repo.syndicateList().syndicates.single().id
        listOf("Aoife", "Ciarán", "Niamh").forEach { repo.addMember(id, it, null) }
        repo.addLine(id, listOf(19, 26, 30, 1, 2, 3)) // Match 3
        repo.addLine(id, listOf(33, 34, 40, 4, 5, 6)) // Match 3
        repo.addLine(id, listOf(7, 8, 9, 10, 11, 12)) // nothing
        val week = repo.membersScreen(id).weekStart
        repo.membersScreen(id).members.take(2).forEach { repo.setPaid(it.id, week, true) }

        assertTrue(repo.fetchResults())
        assertFalse(repo.fetchResults()) // not again straight away

        val results = repo.resultsScreen(id)
        assertEquals("Mon 19 Oct 2026", results.latestDraw?.title)
        assertEquals("Possible win: Match 3, €4! Check the ticket.", results.verdict)
        assertEquals(listOf(true, true, false), results.results.map { it.won })
        assertEquals(listOf("Sat 17 Oct 2026"), results.previousDraws.map { it.title })
        val winnings = results.winnings!!.lines
        assertEquals("Total: €8 from 2 winning lines", winnings[0])
        assertEquals("Shared by 2 members who paid for the week of 19 Oct: €4.00 each", winnings[1])
        assertEquals("Not included: Niamh (hadn't paid for that week)", winnings.last())
        assertTrue(results.groupMessage!!.contains("€4.00 each"))
        assertFalse(results.groupMessage!!.contains("Niamh"))

        val messages = repo.membersScreen(id).members.map { it.message }
        assertTrue(messages[0].contains("your share is €4.00"))
        assertTrue(messages[2].contains("there's no share for you this time"))
    }

    @Test
    fun resultsProblemsAreQuietUnlessAskedFor() = runBlocking {
        val repo = repository { throw ScrapeException("offline") }
        assertFalse(repo.fetchResults())
        expectMessage("Couldn't get the latest results from lottery.ie. Check your internet connection and try again.") {
            repository { throw ScrapeException("offline") }.fetchResults(userAsked = true)
        }
    }

    @Test
    fun anUnreadableFileIsNeverOverwritten() {
        file().writeText("{ not json")
        val repo = repository()
        expectMessage("Your saved data couldn't be read. It has been kept as it is; please contact support.") { repo.syndicateList() }
        expectMessage("Your saved data couldn't be read, so nothing was changed.") { repo.addSyndicate("Office", 500) }
        assertEquals("{ not json", file().readText())
    }
}
