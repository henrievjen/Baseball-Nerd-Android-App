package com.baseballnerd.app.ui.schedule

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.baseballnerd.app.data.model.GameCardModel
import com.baseballnerd.app.data.repository.GamesRepository
import kotlinx.coroutines.launch
import java.util.*

class TeamScheduleViewModel(
    private val teamId: Int,
    private val repository: GamesRepository
) : ViewModel() {

    private val _schedule = MutableLiveData<List<GameCardModel>>()
    val schedule: LiveData<List<GameCardModel>> = _schedule

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var currentYear = Calendar.getInstance().get(Calendar.YEAR)

    init {
        loadSchedule(currentYear)
    }

    fun loadSchedule(year: Int) {
        currentYear = year
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                repository.getTeamSchedule(teamId, year)
            }.onSuccess {
                _schedule.value = it
                if (it.isEmpty()) {
                    _error.value = "No games found for this team in $year."
                }
            }.onFailure {
                _error.value = it.message ?: "Failed to load schedule"
            }
            _loading.value = false
        }
    }
}

class TeamScheduleViewModelFactory(
    private val teamId: Int,
    private val repository: GamesRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TeamScheduleViewModel(teamId, repository) as T
    }
}
