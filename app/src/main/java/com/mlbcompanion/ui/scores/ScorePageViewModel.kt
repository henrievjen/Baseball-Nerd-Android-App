package com.baseballnerd.app.ui.scores

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baseballnerd.app.data.model.GameCardModel
import com.baseballnerd.app.data.repository.GamesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class ScoresUiState {
    object Loading : ScoresUiState()
    data class Success(val games: List<GameCardModel>) : ScoresUiState()
    data class Error(val message: String) : ScoresUiState()
}

class ScorePageViewModel(
    private val date: String,
    private val repository: GamesRepository
) : ViewModel() {

    private val _uiState = MutableLiveData<ScoresUiState>(ScoresUiState.Loading)
    val uiState: LiveData<ScoresUiState> = _uiState

    private var pollingJob: Job? = null
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val isToday = date == sdf.format(Date())

    companion object {
        private const val POLL_INTERVAL_MS = 15_000L
    }

    init {
        fetchGames()
        if (isToday) {
            startPolling()
        }
    }

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchGames()
                
                // Only continue looping if it's today's games
                if (!isToday) break

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    fun refresh() {
        viewModelScope.launch {
            fetchGames()
        }
    }

    private fun fetchGames() {
        viewModelScope.launch {
            runCatching {
                repository.getGamesForDate(date)
            }.onSuccess { games ->
                // Sort: Live games first, then by gameDate (chronological)
                val sortedGames = games.sortedWith(
                    compareByDescending<GameCardModel> { it.gameState == "Live" }
                        .thenBy { it.gameDate }
                )
                _uiState.postValue(ScoresUiState.Success(sortedGames))
            }.onFailure { e ->
                if (_uiState.value !is ScoresUiState.Success) {
                    _uiState.postValue(ScoresUiState.Error(e.message ?: "Failed to load games"))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
