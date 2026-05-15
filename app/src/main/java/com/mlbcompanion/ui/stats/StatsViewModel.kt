package com.baseballnerd.app.ui.stats

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.baseballnerd.app.data.model.StatLeader
import com.baseballnerd.app.data.repository.GamesRepository
import kotlinx.coroutines.launch
import java.util.Calendar

class StatsViewModel(private val repository: GamesRepository) : ViewModel() {
    private val _leaders = MutableLiveData<List<StatLeader>>()
    val leaders: LiveData<List<StatLeader>> = _leaders

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private var currentSeason = Calendar.getInstance().get(Calendar.YEAR).toString()
    private var currentCategory: StatsFragment.StatCategory? = null

    fun setSeason(season: String) {
        currentSeason = season
        loadLeaders()
    }

    fun setCategory(category: StatsFragment.StatCategory) {
        currentCategory = category
        loadLeaders()
    }

    private fun loadLeaders() {
        val cat = currentCategory ?: return
        viewModelScope.launch {
            _loading.value = true
            runCatching {
                repository.getStatsLeaders(cat.key, currentSeason, cat.group)
            }.onSuccess {
                _leaders.value = it
            }.onFailure {
                _leaders.value = emptyList()
            }
            _loading.value = false
        }
    }
}

class StatsViewModelFactory(private val repository: GamesRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return StatsViewModel(repository) as T
    }
}
