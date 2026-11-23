/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/ui/detail/SyndicateDetailViewModel.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-10-20
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrbacco.syndicate.data.UserMessageException
import com.mrbacco.syndicate.data.SyndicateRepository
import com.mrbacco.syndicate.data.model.GameRules
import com.mrbacco.syndicate.data.model.LinesScreen
import com.mrbacco.syndicate.data.model.MembersScreen
import com.mrbacco.syndicate.data.model.ResultsScreen
import com.mrbacco.syndicate.ui.components.Loadable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything the three tabs show. */
data class DetailData(
    val members: MembersScreen,
    val lines: LinesScreen,
    val results: ResultsScreen,
    val rules: GameRules,
) {
    val syndicateName: String get() = members.syndicate.name
}

class SyndicateDetailViewModel(
    private val syndicateId: String,
    private val repository: SyndicateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<Loadable<DetailData>>(Loadable.Loading)
    val state: StateFlow<Loadable<DetailData>> = _state.asStateFlow()

    /** A one-off message for a snackbar, e.g. when saving failed. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            reload()
            // Fetch the latest result if a draw is due; quietly, as the saved data is already showing.
            try {
                if (repository.fetchResults()) reload()
            } catch (_: UserMessageException) {
            }
        }
    }

    /** The Refresh button: fetches the latest results from lottery.ie now, then reloads. */
    fun refresh() {
        viewModelScope.launch {
            try {
                repository.fetchResults(userAsked = true)
            } catch (e: UserMessageException) {
                _message.value = e.message
            }
            reload()
        }
    }

    fun setPaid(memberId: String, paid: Boolean) {
        val week = (_state.value as? Loadable.Ready)?.value?.members?.weekStart ?: return
        change { repository.setPaid(memberId, week, paid) }
    }

    fun addMember(name: String, phone: String?) = change { repository.addMember(syndicateId, name, phone) }

    fun updateMember(memberId: String, name: String, phone: String?) =
        change { repository.updateMember(memberId, name, phone) }

    fun deleteMember(memberId: String, name: String) =
        change(done = "Removed $name") { repository.deleteMember(memberId) }

    fun addLine(numbers: List<Int>) = change { repository.addLine(syndicateId, numbers) }

    fun updateLine(lineId: String, numbers: List<Int>) = change { repository.updateLine(lineId, numbers) }

    fun deleteLine(lineId: String) = change(done = "Line deleted") { repository.deleteLine(lineId) }

    fun messageShown() {
        _message.value = null
    }

    /**
     * Saves a change, then reloads, because one change can alter several things on screen.
     * [done] is shown in the snackbar once the change has been saved.
     */
    private fun change(done: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                done?.let { _message.value = it }
            } catch (e: UserMessageException) {
                _message.value = e.message
            }
            reload()
        }
    }

    private suspend fun reload() {
        try {
            _state.value = Loadable.Ready(
                coroutineScope {
                    val members = async { repository.membersScreen(syndicateId) }
                    val lines = async { repository.linesScreen(syndicateId) }
                    val results = async { repository.resultsScreen(syndicateId) }
                    DetailData(members.await(), lines.await(), results.await(), repository.rules())
                }
            )
        } catch (e: UserMessageException) {
            // Keep showing what we have; only an empty screen turns into the error screen.
            if (_state.value is Loadable.Ready) _message.value = e.message
            else _state.value = Loadable.Failed(e.message.orEmpty())
        }
    }
}
