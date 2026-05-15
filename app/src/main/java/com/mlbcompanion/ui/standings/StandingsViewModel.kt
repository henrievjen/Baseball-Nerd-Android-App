package com.baseballnerd.app.ui.standings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baseballnerd.app.data.model.StandingsDivision
import com.baseballnerd.app.data.model.WildCardEntry
import com.baseballnerd.app.data.repository.GamesRepository
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class StandingsUiState {
    object Loading : StandingsUiState()
    data class Success(
        val divisions: List<StandingsDivision>,
        val wildCard: List<WildCardEntry>
    ) : StandingsUiState()
    data class Error(val message: String) : StandingsUiState()
}

class StandingsViewModel : ViewModel() {
    private val repository = GamesRepository()

    private val _uiState = MutableLiveData<StandingsUiState>(StandingsUiState.Loading)
    val uiState: LiveData<StandingsUiState> = _uiState

    init { load() }

    fun load(season: String = Calendar.getInstance().get(Calendar.YEAR).toString()) {
        _uiState.value = StandingsUiState.Loading
        viewModelScope.launch {
            runCatching {
                repository.getStandings(season)
            }.onSuccess { (divisions, wc) ->
                _uiState.postValue(StandingsUiState.Success(divisions, wc))
            }.onFailure { e ->
                _uiState.postValue(StandingsUiState.Error(e.message ?: "Failed to load standings"))
            }
        }
    }
}
