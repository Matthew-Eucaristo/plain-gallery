package app.plaingallery.feature.tidy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.plaingallery.core.data.MediaRepository
import app.plaingallery.core.model.Media
import kotlinx.coroutines.launch

enum class TidyOrder { Oldest, Largest }

enum class Decision { Keep, Discard, Skip }

/**
 * A deck over a snapshot of the inbox, so cards never shift while you swipe. Keeps are saved at once
 * (they only move items to Photos); deletions are collected and sent to Trash together at the end.
 */
class TidyViewModel(private val repository: MediaRepository) : ViewModel() {
    var order by mutableStateOf(TidyOrder.Oldest)
        private set
    var deck: List<Media> by mutableStateOf(buildDeck(TidyOrder.Oldest))
        private set
    var position by mutableIntStateOf(0)
        private set
    private val decisions = mutableStateListOf<Pair<Media, Decision>>()

    val current: Media? get() = deck.getOrNull(position)
    val upcoming: List<Media> get() = deck.drop(position + 1).take(PREFETCH)
    val canUndo: Boolean get() = decisions.isNotEmpty()
    val canReorder: Boolean get() = decisions.isEmpty()
    val toDiscard: List<Media> get() = decisions.filter { it.second == Decision.Discard }.map { it.first }
    val keptCount: Int get() = decisions.count { it.second == Decision.Keep }

    fun reorder(newOrder: TidyOrder) {
        if (!canReorder || newOrder == order) return
        order = newOrder
        deck = buildDeck(newOrder)
        position = 0
    }

    fun decide(decision: Decision) {
        val media = current ?: return
        decisions += media to decision
        if (decision == Decision.Keep) viewModelScope.launch { repository.keep(listOf(media.id)) }
        position++
    }

    fun undo() {
        val (media, decision) = decisions.removeLastOrNull() ?: return
        if (decision == Decision.Keep) viewModelScope.launch { repository.unkeep(listOf(media.id)) }
        position = (position - 1).coerceAtLeast(0)
    }

    private fun buildDeck(order: TidyOrder): List<Media> {
        val inbox = repository.library.value?.inbox.orEmpty()
        return when (order) {
            TidyOrder.Oldest -> inbox.sortedBy { it.addedAt }
            TidyOrder.Largest -> inbox.sortedByDescending { it.sizeBytes }
        }
    }

    private companion object {
        const val PREFETCH = 3
    }
}
