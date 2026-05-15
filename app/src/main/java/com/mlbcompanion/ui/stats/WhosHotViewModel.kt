package com.baseballnerd.app.ui.stats

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.baseballnerd.app.data.model.PlayerStatLine
import com.baseballnerd.app.data.model.StatSplit
import com.baseballnerd.app.data.repository.GamesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ─── UI Models ───────────────────────────────────────────────────────────────

data class HotHitterUiModel(
    val id: Int,
    val name: String,
    val teamId: Int,
    val teamName: String,
    val avg: String,
    val ops: String,
    val ab: Int,
    val runs: Int,
    val hits: Int,
    val doubles: Int,
    val triples: Int,
    val homeRuns: Int,
    val rbi: Int,
    val bb: Int,
    val so: Int,
    val sb: Int,
    val obp: String,
    val slg: String,
    val sortScore: Double,
    val isLast3: Boolean = false,
    val recentLogs: List<StatSplit> = emptyList()
)

data class HotPitcherUiModel(
    val id: Int,
    val name: String,
    val teamId: Int,
    val teamName: String,
    val ip: String,
    val hits: Int,
    val runs: Int,
    val earnedRuns: Int,
    val homeRuns: Int,
    val hitBatters: Int,
    val walks: Int,
    val intentionalWalks: Int,
    val strikeouts: Int,
    val avg: String,
    val whip: String,
    val era: String,
    val wins: Int,
    val losses: Int,
    val saves: Int,
    val holds: Int,
    val sortScore: Double,
    val isLast3: Boolean = false,
    val recentLogs: List<StatSplit> = emptyList()
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

class WhosHotViewModel(private val repository: GamesRepository) : ViewModel() {

    private val _hotHitters  = MutableLiveData<List<HotHitterUiModel>>()
    val hotHitters: LiveData<List<HotHitterUiModel>> = _hotHitters

    private val _hotPitchers = MutableLiveData<List<HotPitcherUiModel>>()
    val hotPitchers: LiveData<List<HotPitcherUiModel>> = _hotPitchers

    private val _loading = MutableLiveData<Boolean>()
    val loading: androidx.lifecycle.LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value   = null
            try {
                coroutineScope {
                    val cal = Calendar.getInstance()
                    val currentYear = cal.get(Calendar.YEAR).toString()
                    val prevYear = (cal.get(Calendar.YEAR) - 1).toString()
                    
                    // Threshold for "recent" activity: 2 days ago
                    val thresholdDate = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -2)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time

                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }

                    fun isRecent(dateStr: String?): Boolean {
                        if (dateStr == null) return false
                        return try {
                            val gameDate = sdf.parse(dateStr.take(10))
                            gameDate != null && !gameDate.before(thresholdDate)
                        } catch (e: Exception) { false }
                    }
                    
                    // 1. Candidate Pool: Build a robust list of active players from multiple categories
                    val hitterPoolDef = async {
                        val cats = listOf("onBasePlusSlugging", "hits", "homeRuns", "runsBattedIn")
                        val ids = mutableSetOf<Int>()
                        for (year in listOf(currentYear, prevYear)) {
                            for (cat in cats) {
                                runCatching { repository.getStatsLeaders(cat, year, "hitting") }
                                    .getOrNull()?.forEach { it.person?.id?.let { id -> ids.add(id) } }
                            }
                            if (ids.size >= 60) break
                        }
                        ids.toList()
                    }
                    
                    val pitcherPoolDef = async {
                        val cats = listOf("earnedRunAverage", "strikeOuts", "wins", "saves")
                        val ids = mutableSetOf<Int>()
                        for (year in listOf(currentYear, prevYear)) {
                            for (cat in cats) {
                                runCatching { repository.getStatsLeaders(cat, year, "pitching") }
                                    .getOrNull()?.forEach { it.person?.id?.let { id -> ids.add(id) } }
                            }
                            if (ids.size >= 60) break
                        }
                        ids.toList()
                    }

                    val hitterIds = hitterPoolDef.await()
                    val pitcherIds = pitcherPoolDef.await()

                    // 2. Fetch detailed stats (including gameLogs) for all pool candidates
                    val hitterPersons  = repository.getPeopleLast3(hitterIds, currentYear)
                    val pitcherPersons = repository.getPeopleLast3(pitcherIds, currentYear)

                    // 3. Process Hitters: Average OPS over the last 3 games with activity
                    val hitters = hitterPersons.mapNotNull { person ->
                        val pid = person.id ?: return@mapNotNull null
                        
                        val hittingStats = person.stats?.filter { 
                            it.group?.displayName?.equals("hitting", true) == true ||
                            it.group?.displayName?.equals("batting", true) == true
                        } ?: emptyList()
                        
                        val allLogs = hittingStats.flatMap { it.splits ?: emptyList() }
                            .filter { it.stat != null }
                            .sortedByDescending { it.gameDate ?: it.date ?: "" }
                        
                        // Check for recent activity (within 2 days)
                        val mostRecentGame = allLogs.firstOrNull()?.gameDate ?: allLogs.firstOrNull()?.date
                        if (!isRecent(mostRecentGame)) return@mapNotNull null

                        val recentLogs = allLogs.filter { (it.stat?.plateAppearances ?: it.stat?.atBats ?: 0) > 0 }.take(3)
                        if (recentLogs.isEmpty()) return@mapNotNull null

                        val opsValues = recentLogs.mapNotNull { it.stat?.ops?.toDoubleOrNull() }
                        val avgOps = if (opsValues.isNotEmpty()) opsValues.average() else 0.0

                        val aggregate = aggregateHitting(recentLogs) ?: return@mapNotNull null
                        
                        HotHitterUiModel(
                            id = pid,
                            name = person.fullName ?: "Unknown",
                            teamId = person.currentTeam?.id ?: 0,
                            teamName = person.currentTeam?.name ?: "",
                            avg = aggregate.avg ?: ".---",
                            ops = String.format(Locale.US, "%.3f", avgOps),
                            ab = aggregate.atBats ?: 0,
                            runs = aggregate.runs ?: 0,
                            hits = aggregate.hits ?: 0,
                            doubles = aggregate.doubles ?: 0,
                            triples = aggregate.triples ?: 0,
                            homeRuns = aggregate.homeRuns ?: 0,
                            rbi = aggregate.rbi ?: 0,
                            bb = aggregate.baseOnBalls ?: 0,
                            so = aggregate.strikeOuts ?: 0,
                            sb = aggregate.stolenBases ?: 0,
                            obp = aggregate.obp ?: ".---",
                            slg = aggregate.slg ?: ".---",
                            sortScore = avgOps,
                            isLast3 = true,
                            recentLogs = recentLogs
                        )
                    }.sortedByDescending { it.sortScore }.take(15)

                    // 4. Process Pitchers
                    val pitchers = pitcherPersons.mapNotNull { person ->
                        val pid = person.id ?: return@mapNotNull null
                        
                        val pitchingStats = person.stats?.filter { 
                            it.group?.displayName?.equals("pitching", true) == true 
                        } ?: emptyList()
                        
                        val allLogs = pitchingStats.flatMap { it.splits ?: emptyList() }
                            .filter { it.stat != null }
                            .sortedByDescending { it.gameDate ?: it.date ?: "" }
                        
                        // Check for recent activity (within 2 days)
                        val mostRecentGame = allLogs.firstOrNull()?.gameDate ?: allLogs.firstOrNull()?.date
                        if (!isRecent(mostRecentGame)) return@mapNotNull null

                        val recentLogs = allLogs.filter { 
                            it.stat?.inningsPitched != null && it.stat.inningsPitched != "0.0" 
                        }.take(3)
                        
                        if (recentLogs.isEmpty()) return@mapNotNull null

                        val aggregate = aggregatePitching(recentLogs) ?: return@mapNotNull null
                        
                        // Requirement: Only show pitchers that have 9 or more innings pitched (IP) over their last 3 games.
                        val totalIpDecimal = calculateDecimalIp(aggregate.inningsPitched ?: "0.0")
                        if (totalIpDecimal < 9.0) return@mapNotNull null

                        val totalStrikeouts = aggregate.strikeOuts ?: 0
                        val whipValue = aggregate.whip?.toDoubleOrNull() ?: 9.99
                        
                        HotPitcherUiModel(
                            id = pid,
                            name = person.fullName ?: "Unknown",
                            teamId = person.currentTeam?.id ?: 0,
                            teamName = person.currentTeam?.name ?: "",
                            ip = aggregate.inningsPitched ?: "0.0",
                            hits = aggregate.pitchingHits ?: aggregate.hits ?: 0,
                            runs = aggregate.pitchingRuns ?: aggregate.runs ?: 0,
                            earnedRuns = aggregate.earnedRuns ?: 0,
                            homeRuns = aggregate.pitchingHomeRuns ?: aggregate.homeRuns ?: 0,
                            hitBatters = aggregate.hitBatters ?: 0,
                            walks = aggregate.baseOnBalls ?: 0,
                            intentionalWalks = aggregate.intentionalWalks ?: 0,
                            strikeouts = totalStrikeouts,
                            avg = aggregate.avg ?: ".---",
                            whip = aggregate.whip ?: "-.--",
                            era = aggregate.era ?: "-.--",
                            wins = aggregate.wins ?: 0,
                            losses = aggregate.losses ?: 0,
                            saves = aggregate.saves ?: 0,
                            holds = aggregate.holds ?: 0,
                            // Requirement: Sort the Hot Pitchers by WHIP instead of SO. Lower WHIP is better.
                            sortScore = whipValue,
                            isLast3 = true,
                            recentLogs = recentLogs
                        )
                    }.sortedBy { it.sortScore }.take(15) // Use sortedBy for ascending WHIP

                    _hotHitters.value  = hitters
                    _hotPitchers.value = pitchers
                }
            } catch (e: Exception) {
                _error.value = "Could not load data. Please try again."
            } finally {
                _loading.value = false
            }
        }
    }

    private fun calculateDecimalIp(ipStr: String): Double {
        val parts = ipStr.split(".")
        val whole = parts[0].toDoubleOrNull() ?: 0.0
        val frac = if (parts.size > 1) (parts[1].toDoubleOrNull() ?: 0.0) else 0.0
        return whole + (frac / 3.0)
    }

    private fun aggregateHitting(splits: List<StatSplit>): PlayerStatLine? {
        val stats = splits.mapNotNull { it.stat }
        if (stats.isEmpty()) return null
        
        var ab = 0; var r = 0; var h = 0; var d2 = 0; var d3 = 0; var hr = 0; var rbi = 0
        var bb = 0; var so = 0; var sb = 0; var pa = 0; var hbp = 0; var sf = 0
        var avgSum = 0.0; var obpSum = 0.0; var slgSum = 0.0

        for (s in stats) {
            ab += s.atBats ?: 0
            r += s.runs ?: 0
            h += s.hits ?: 0
            d2 += s.doubles ?: 0
            d3 += s.triples ?: 0
            hr += s.homeRuns ?: 0
            rbi += s.rbi ?: 0
            bb += s.baseOnBalls ?: 0
            so += s.strikeOuts ?: 0
            sb += s.stolenBases ?: 0
            pa += s.plateAppearances ?: 0
            hbp += s.hitByPitch ?: 0
            sf += s.sacFlies ?: 0
            avgSum += s.avg?.toDoubleOrNull() ?: 0.0
            obpSum += s.obp?.toDoubleOrNull() ?: 0.0
            slgSum += s.slg?.toDoubleOrNull() ?: 0.0
        }

        val count = stats.size.toDouble()
        return PlayerStatLine(
            atBats = ab, runs = r, hits = h, doubles = d2, triples = d3, homeRuns = hr, rbi = rbi,
            baseOnBalls = bb, strikeOuts = so, stolenBases = sb, plateAppearances = pa, hitByPitch = hbp, sacFlies = sf,
            avg = String.format(Locale.US, ".%03d", ((avgSum / count) * 1000).toInt()),
            obp = String.format(Locale.US, "%.3f", obpSum / count),
            slg = String.format(Locale.US, "%.3f", slgSum / count)
        )
    }

    private fun aggregatePitching(splits: List<StatSplit>): PlayerStatLine? {
        val stats = splits.mapNotNull { it.stat }
        if (stats.isEmpty()) return null

        var ipTotal = 0.0; var h = 0; var r = 0; var er = 0; var hr = 0; var hb = 0
        var bb = 0; var ibb = 0; var so = 0; var w = 0; var l = 0; var s = 0; var hld = 0
        var whipSum = 0.0; var avgSum = 0.0; var eraSum = 0.0

        for (st in stats) {
            val ip = st.inningsPitched?.toDoubleOrNull() ?: 0.0
            val whole = ip.toInt()
            val frac = ((ip - whole) * 10).toInt()
            ipTotal += whole + (frac / 3.0)
            
            h += st.pitchingHits ?: st.hits ?: 0
            r += st.pitchingRuns ?: st.runs ?: 0
            er += st.earnedRuns ?: 0
            hr += st.pitchingHomeRuns ?: st.homeRuns ?: 0
            hb += st.hitBatters ?: 0
            bb += st.baseOnBalls ?: 0
            ibb += st.intentionalWalks ?: 0
            so += st.strikeOuts ?: 0
            w += st.wins ?: 0
            l += st.losses ?: 0
            s += st.saves ?: 0
            hld += st.holds ?: 0
            whipSum += st.whip?.toDoubleOrNull() ?: 0.0
            avgSum += st.avg?.toDoubleOrNull() ?: 0.0
            eraSum += st.era?.toDoubleOrNull() ?: 0.0
        }
        
        val count = stats.size.toDouble()
        val wholeIp = ipTotal.toInt()
        val fracIp = ((ipTotal - wholeIp) * 3 + 0.1).toInt()
        val ipDisplay = String.format(Locale.US, "%d.%d", wholeIp, fracIp)
        
        return PlayerStatLine(
            inningsPitched = ipDisplay,
            pitchingHits = h, pitchingRuns = r, earnedRuns = er, pitchingHomeRuns = hr, hitBatters = hb,
            baseOnBalls = bb, intentionalWalks = ibb, strikeOuts = so, wins = w, losses = l, saves = s, holds = hld,
            whip = String.format(Locale.US, "%.2f", whipSum / count),
            avg = String.format(Locale.US, ".%03d", ((avgSum / count) * 1000).toInt()),
            era = String.format(Locale.US, "%.2f", eraSum / count)
        )
    }
}

class WhosHotViewModelFactory(private val repository: GamesRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WhosHotViewModel(repository) as T
}
