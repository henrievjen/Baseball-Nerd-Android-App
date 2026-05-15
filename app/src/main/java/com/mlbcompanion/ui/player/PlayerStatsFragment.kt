package com.baseballnerd.app.ui.player

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.baseballnerd.app.R
import com.baseballnerd.app.data.model.BoxscorePerson
import com.baseballnerd.app.data.model.PlayerStatLine
import com.baseballnerd.app.data.model.PlaySummary
import com.baseballnerd.app.data.model.StatSplit
import com.baseballnerd.app.data.repository.GamesRepository
import com.baseballnerd.app.databinding.FragmentPlayerStatsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class PlayerStatsFragment : Fragment() {

    private var _binding: FragmentPlayerStatsBinding? = null
    private val binding get() = _binding!!

    private val args: PlayerStatsFragmentArgs by navArgs()
    private val viewModel: PlayerStatsViewModel by viewModels {
        PlayerStatsViewModelFactory(args.playerId, GamesRepository())
    }

    private var spinnerInitialized = false
    private val expandedGames = mutableSetOf<Long>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        viewModel.availableYears.observe(viewLifecycleOwner) { years ->
            if (years.isEmpty()) return@observe
            spinnerInitialized = false
            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_year,
                years
            )
            adapter.setDropDownViewResource(R.layout.item_spinner_year_dropdown)
            binding.spinnerYear.adapter = adapter
            val currentYear = viewModel.selectedYear.value
            val idx = years.indexOf(currentYear).coerceAtLeast(0)
            binding.spinnerYear.setSelection(idx, false)
            spinnerInitialized = true
        }

        binding.spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!spinnerInitialized) return
                val year = binding.spinnerYear.selectedItem as? String ?: return
                expandedGames.clear()
                viewModel.loadStatsForYear(year)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        viewModel.playerData.observe(viewLifecycleOwner) { person ->
            if (person != null) bindPlayerData(person)
        }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            binding.tvError.visibility = if (error != null) View.VISIBLE else View.GONE
            binding.tvError.text = error ?: ""
        }

        viewModel.gamePlays.observe(viewLifecycleOwner) { _ ->
            // Re-render the game log to show loaded plays
            viewModel.playerData.value?.let { renderGameLog(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.selectedYear.value?.let { viewModel.loadStatsForYear(it) }
    }

    private fun bindPlayerData(person: BoxscorePerson) {
        binding.tvPlayerName.text = person.fullName

        val position = person.primaryPosition?.abbreviation?.takeIf { it.isNotBlank() }
        val number = person.primaryNumber?.takeIf { it.isNotBlank() }
        val bats = person.batSide?.code?.takeIf { it.isNotBlank() }
        val throws = person.pitchHand?.code?.takeIf { it.isNotBlank() }

        val parts = mutableListOf<String>()
        number?.let { parts.add("#$it") }
        position?.let { parts.add(it) }
        if (bats != null && throws != null) {
            parts.add("B/T: $bats/$throws")
        }

        val infoText = parts.joinToString(" · ")
        binding.tvPlayerInfo.text = infoText
        binding.tvPlayerInfo.visibility = if (infoText.isBlank()) View.GONE else View.VISIBLE

        binding.ivPlayerPhoto.load(
            "https://midfield.mlbstatic.com/v1/people/${person.id}/spots/120"
        ) {
            crossfade(true)
            placeholder(R.drawable.ic_baseball_placeholder)
            error(R.drawable.ic_baseball_placeholder)
        }

        renderSeasonStats(person)
        renderPlayerBio(person)
        renderGameLog(person)
    }

    private fun renderPlayerBio(person: BoxscorePerson) {
        val container = binding.layoutStatsContainer

        // ── Career Stats ─────────────────────────────────────────────────────
        val stats = person.stats ?: emptyList()

        val careerHittingSplit = stats
            .firstOrNull {
                it.type?.displayName?.lowercase()?.replace(" ", "") == "career" &&
                        it.group?.displayName?.lowercase() == "hitting"
            }?.splits?.let { splits ->
                splits.firstOrNull { it.gameType == "R" } ?: splits.firstOrNull()
            }

        val careerPitchingSplit = stats
            .firstOrNull {
                it.type?.displayName?.lowercase()?.replace(" ", "") == "career" &&
                        it.group?.displayName?.lowercase() == "pitching"
            }?.splits?.let { splits ->
                splits.firstOrNull { it.gameType == "R" } ?: splits.firstOrNull()
            }

        val hasCareerHitting = careerHittingSplit?.stat?.let {
            (it.atBats ?: 0) > 0 || (it.plateAppearances ?: 0) > 0
        } == true
        val hasCareerPitching = careerPitchingSplit?.stat?.let {
            (it.gamesPlayed ?: 0) > 0 || it.inningsPitched != null
        } == true

        if (hasCareerHitting || hasCareerPitching) {
            val careerContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dpToPx(16), 0, dpToPx(8))
            }
            addSectionHeader(careerContainer, "CAREER STATS")

            if (hasCareerHitting) {
                addSubsectionHeader(careerContainer, "HITTING")
                val line = careerHittingSplit!!.stat!!
                val grid = createStatsGrid()
                addStatToGrid(grid, "AVG",  line.avg  ?: ".---", highlight = true)
                addStatToGrid(grid, "OBP",  line.obp  ?: ".---")
                addStatToGrid(grid, "SLG",  line.slg  ?: ".---")
                addStatToGrid(grid, "OPS",  line.ops  ?: ".---", highlight = true)
                addStatToGrid(grid, "G",   (line.gamesPlayed  ?: 0).toString())
                addStatToGrid(grid, "AB",  (line.atBats        ?: 0).toString())
                addStatToGrid(grid, "H",   (line.hits          ?: 0).toString())
                addStatToGrid(grid, "HR",  (line.homeRuns      ?: 0).toString(), highlight = true)
                addStatToGrid(grid, "RBI", (line.rbi           ?: 0).toString(), highlight = true)
                addStatToGrid(grid, "SB",  (line.stolenBases   ?: 0).toString())
                addStatToGrid(grid, "BB",  (line.baseOnBalls   ?: 0).toString())
                addStatToGrid(grid, "SO",  (line.strikeOuts    ?: 0).toString())
                careerContainer.addView(grid)
            }

            if (hasCareerPitching) {
                addSubsectionHeader(careerContainer, "PITCHING")
                val line = careerPitchingSplit!!.stat!!
                val grid = createStatsGrid()
                addStatToGrid(grid, "ERA",  line.era   ?: "-.--", highlight = true)
                addStatToGrid(grid, "WHIP", line.whip  ?: "-.--", highlight = true)
                addStatToGrid(grid, "W-L",  "${line.wins ?: 0}-${line.losses ?: 0}")
                addStatToGrid(grid, "G",   (line.gamesPlayed  ?: 0).toString())
                addStatToGrid(grid, "GS",  (line.gamesStarted ?: 0).toString())
                addStatToGrid(grid, "IP",   line.inningsPitched ?: "0.0")
                addStatToGrid(grid, "SO",  (line.strikeOuts    ?: 0).toString(), highlight = true)
                addStatToGrid(grid, "BB",  (line.baseOnBalls   ?: 0).toString())
                addStatToGrid(grid, "SV",  (line.saves         ?: 0).toString())
                careerContainer.addView(grid)
            }

            container.addView(careerContainer)
        }

        // ── Player Info ───────────────────────────────────────────────────────
        // Single-column list so all fields stack vertically.
        val infoList = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dpToPx(8), 0, dpToPx(8), dpToPx(8)) }
        }

        // Helper: adds a single full-width label/value row to infoList
        fun addInfoRow(label: String, value: String?) {
            if (value.isNullOrBlank()) return
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, dpToPx(4), 0, dpToPx(4))
                layoutParams = lp
            }
            row.addView(TextView(requireContext()).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                textSize = 13f
            })
            row.addView(TextView(requireContext()).apply {
                text = value
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                textSize = 13f
                gravity = Gravity.END
            })
            infoList.addView(row)
        }

        addInfoRow("Born", formatBioDate(person.birthDate))
        val birthPlace = listOfNotNull(
            person.birthCity?.takeIf { it.isNotBlank() },
            person.birthStateProvince?.takeIf { it.isNotBlank() },
            person.birthCountry?.takeIf { it.isNotBlank() }
        ).joinToString(", ")
        addInfoRow("From", birthPlace.takeIf { it.isNotBlank() })
        addInfoRow("MLB Debut", formatBioDate(person.mlbDebutDate))
        if (person.deathDate == null) {
            person.currentAge?.takeIf { it > 0 }?.let { addInfoRow("Age", it.toString()) }
        }
        person.height?.takeIf { it.isNotBlank() }?.let { h ->
            val w = person.weight?.takeIf { it > 0 }?.let { "/ $it lbs" } ?: ""
            addInfoRow("Ht/Wt", "$h $w".trim())
        }

        val bioContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(16), 0, dpToPx(8))
        }
        addSectionHeader(bioContainer, "PLAYER INFO")
        if (infoList.childCount > 0) {
            bioContainer.addView(infoList)
        } else {
            bioContainer.addView(TextView(requireContext()).apply {
                text = "Player info unavailable for this season"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                textSize = 13f
                setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            })
        }
        container.addView(bioContainer)
    }

    private fun formatBioDate(dateStr: String?): String? {
        if (dateStr.isNullOrBlank() || dateStr.startsWith("0000")) return null
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("MM/dd/yyyy", Locale.US)
            formatter.format(parser.parse(dateStr.take(10))!!)
        } catch (e: Exception) {
            if (dateStr.isNotBlank()) dateStr else null
        }
    }

    private fun renderSeasonStats(person: BoxscorePerson) {
        val container = binding.layoutStatsContainer
        container.removeAllViews()

        val stats = person.stats ?: emptyList()

        val seasonHittingSplit = stats
            .firstOrNull {
                it.type?.displayName?.lowercase()?.replace(" ", "") == "season" &&
                        it.group?.displayName?.lowercase() == "hitting"
            }?.splits?.let { splits ->
                splits.firstOrNull { it.gameType == "R" } ?: splits.firstOrNull()
            }

        val seasonPitchingSplit = stats
            .firstOrNull {
                it.type?.displayName?.lowercase()?.replace(" ", "") == "season" &&
                        it.group?.displayName?.lowercase() == "pitching"
            }?.splits?.let { splits ->
                splits.firstOrNull { it.gameType == "R" } ?: splits.firstOrNull()
            }

        if (seasonHittingSplit == null && seasonPitchingSplit == null) {
            addEmptyMessage(container, "No season stats available for this year.")
            return
        }

        seasonHittingSplit?.stat?.let { line ->
            if ((line.atBats ?: 0) > 0 || (line.plateAppearances ?: 0) > 0) {
                renderHittingStats(container, line)
            }
        }

        seasonPitchingSplit?.stat?.let { line ->
            if ((line.gamesPlayed ?: 0) > 0 || line.inningsPitched != null) {
                renderPitchingStats(container, line)
            }
        }
    }

    private fun renderHittingStats(container: LinearLayout, line: PlayerStatLine) {
        addSectionHeader(container, "HITTING")
        val grid = createStatsGrid()

        addStatToGrid(grid, "AVG",  line.avg  ?: ".---", highlight = true)
        addStatToGrid(grid, "OBP",  line.obp  ?: ".---")
        addStatToGrid(grid, "SLG",  line.slg  ?: ".---")
        addStatToGrid(grid, "OPS",  line.ops  ?: ".---", highlight = true)
        addStatToGrid(grid, "AB",  (line.atBats        ?: 0).toString())
        addStatToGrid(grid, "R",   (line.runs          ?: 0).toString())
        addStatToGrid(grid, "H",   (line.hits          ?: 0).toString())
        addStatToGrid(grid, "2B",  (line.doubles       ?: 0).toString())
        addStatToGrid(grid, "3B",  (line.triples       ?: 0).toString())
        addStatToGrid(grid, "HR",  (line.homeRuns      ?: 0).toString(), highlight = true)
        addStatToGrid(grid, "RBI", (line.rbi           ?: 0).toString(), highlight = true)
        addStatToGrid(grid, "SB",  (line.stolenBases   ?: 0).toString())
        addStatToGrid(grid, "BB",  (line.baseOnBalls   ?: 0).toString())
        addStatToGrid(grid, "SO",  (line.strikeOuts    ?: 0).toString())
        addStatToGrid(grid, "HBP", (line.hitByPitch    ?: 0).toString())
        addStatToGrid(grid, "SF",  (line.sacFlies      ?: 0).toString())
        addStatToGrid(grid, "TB",  (line.totalBases    ?: 0).toString())

        container.addView(grid)
    }

    private fun renderPitchingStats(container: LinearLayout, line: PlayerStatLine) {
        addSectionHeader(container, "PITCHING")
        val grid = createStatsGrid()

        addStatToGrid(grid, "ERA",  line.era   ?: "-.--", highlight = true)
        addStatToGrid(grid, "WHIP", line.whip  ?: "-.--",  highlight = true)
        addStatToGrid(grid, "W-L",  "${line.wins ?: 0}-${line.losses ?: 0}")
        addStatToGrid(grid, "GS",  (line.gamesStarted  ?: 0).toString())
        addStatToGrid(grid, "IP",   line.inningsPitched ?: "0.0")
        addStatToGrid(grid, "SO",  (line.strikeOuts    ?: 0).toString(), highlight = true)
        addStatToGrid(grid, "BB",  (line.baseOnBalls   ?: 0).toString())
        addStatToGrid(grid, "H",   (line.hits ?: line.pitchingHits ?: 0).toString())
        addStatToGrid(grid, "R",   (line.runs ?: line.pitchingRuns ?: 0).toString())
        addStatToGrid(grid, "ER",  (line.earnedRuns    ?: 0).toString())
        addStatToGrid(grid, "HR",  (line.pitchingHomeRuns ?: line.homeRuns ?: 0).toString())
        addStatToGrid(grid, "SV",  (line.saves         ?: 0).toString())
        addStatToGrid(grid, "HLD", (line.holds         ?: 0).toString())
        addStatToGrid(grid, "BS",  (line.blownSaves    ?: 0).toString())
        line.strikeOutsPer9Inn?.let { addStatToGrid(grid, "K/9",  it) }
        line.walksPer9Inn?.let     { addStatToGrid(grid, "BB/9", it) }
        line.hitsPer9Inn?.let      { addStatToGrid(grid, "H/9",  it) }

        container.addView(grid)
    }

    private fun createStatsGrid(): GridLayout {
        return GridLayout(requireContext()).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(8), 0, dpToPx(8), dpToPx(8))
            }
        }
    }

    private fun addStatToGrid(
        grid: GridLayout,
        label: String,
        value: String?,
        highlight: Boolean = false
    ) {
        if (value.isNullOrBlank()) return

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            val params = GridLayout.LayoutParams()
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            layoutParams = params
        }

        row.addView(TextView(requireContext()).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        })

        row.addView(TextView(requireContext()).apply {
            text = value
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = if (highlight) 14f else 13f
            gravity = Gravity.END
        })

        grid.addView(row)
    }

    private fun renderGameLog(person: BoxscorePerson) {
        val container = binding.layoutGameLogContainer
        container.removeAllViews()

        val stats = person.stats ?: emptyList()

        val hittingSplits = stats.find {
            val type = it.type?.displayName?.lowercase()?.replace(" ", "")
            val group = it.group?.displayName?.lowercase()
            type == "gamelog" && group == "hitting"
        }?.splits ?: emptyList()

        val pitchingSplits = stats.find {
            val type = it.type?.displayName?.lowercase()?.replace(" ", "")
            val group = it.group?.displayName?.lowercase()
            type == "gamelog" && group == "pitching"
        }?.splits ?: emptyList()

        val allGamesMap = mutableMapOf<Long, StatSplit>()

        hittingSplits.forEach { split ->
            val pk = split.game?.gamePk ?: split.game?.id?.toLong()
            if (pk != null) allGamesMap[pk] = split
        }

        pitchingSplits.forEach { split ->
            val pk = split.game?.gamePk ?: split.game?.id?.toLong()
            if (pk != null) {
                val existing = allGamesMap[pk]
                if (existing != null) {
                    allGamesMap[pk] = existing.copy(stat = mergeStatLines(existing.stat, split.stat))
                } else {
                    allGamesMap[pk] = split
                }
            }
        }

        val allGames = allGamesMap.values
            .sortedByDescending { split ->
                split.gameDate ?: split.date?.let { d ->
                    val season = split.season ?: "2000"
                    "$season-${d.replace("/", "-")}"
                } ?: ""
            }

        if (allGames.isEmpty()) {
            addEmptyMessageToLog("No games logged for this season.")
            return
        }

        allGames.forEach { renderGameLogRow(container, it, person) }
    }

    private fun mergeStatLines(s1: PlayerStatLine?, s2: PlayerStatLine?): PlayerStatLine? {
        if (s1 == null) return s2
        if (s2 == null) return s1
        return PlayerStatLine(
            gamesPlayed = s1.gamesPlayed ?: s2.gamesPlayed,
            atBats = s1.atBats ?: s2.atBats,
            runs = s1.runs ?: s2.runs,
            hits = s1.hits ?: s2.hits,
            homeRuns = s1.homeRuns ?: s2.homeRuns,
            rbi = s1.rbi ?: s2.rbi,
            stolenBases = s1.stolenBases ?: s2.stolenBases,
            avg = s1.avg ?: s2.avg,
            obp = s1.obp ?: s2.obp,
            slg = s1.slg ?: s2.slg,
            ops = s1.ops ?: s2.ops,
            strikeOuts = s1.strikeOuts ?: s2.strikeOuts,
            baseOnBalls = s1.baseOnBalls ?: s2.baseOnBalls,
            doubles = s1.doubles ?: s2.doubles,
            triples = s1.triples ?: s2.triples,
            plateAppearances = s1.plateAppearances ?: s2.plateAppearances,
            totalBases = s1.totalBases ?: s2.totalBases,
            sacFlies = s1.sacFlies ?: s2.sacFlies,
            hitByPitch = s1.hitByPitch ?: s2.hitByPitch,
            era = s1.era ?: s2.era,
            wins = s1.wins ?: s2.wins,
            losses = s1.losses ?: s2.losses,
            inningsPitched = s1.inningsPitched ?: s2.inningsPitched,
            gamesStarted = s1.gamesStarted ?: s2.gamesStarted,
            saves = s1.saves ?: s2.saves,
            holds = s1.holds ?: s2.holds,
            blownSaves = s1.blownSaves ?: s2.blownSaves,
            whip = s1.whip ?: s2.whip,
            earnedRuns = s1.earnedRuns ?: s2.earnedRuns,
            pitchingHits = s1.pitchingHits ?: s2.pitchingHits,
            pitchingRuns = s1.pitchingRuns ?: s2.pitchingRuns,
            pitchingHomeRuns = s1.pitchingHomeRuns ?: s2.pitchingHomeRuns,
            strikeOutsPer9Inn = s1.strikeOutsPer9Inn ?: s2.strikeOutsPer9Inn,
            walksPer9Inn = s1.walksPer9Inn ?: s2.walksPer9Inn,
            hitsPer9Inn = s1.hitsPer9Inn ?: s2.hitsPer9Inn,
            intentionalWalks = s1.intentionalWalks ?: s2.intentionalWalks,
            hitBatters = s1.hitBatters ?: s2.hitBatters
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderGameLogRow(container: LinearLayout, split: StatSplit, person: BoxscorePerson) {
        val gamePk = split.game?.gamePk ?: split.game?.id?.toLong() ?: 0L
        val badgeInfo: Pair<String, Int>? = when (split.gameType) {
            "S" -> "ST" to R.color.st_color
            "E" -> "PRE" to R.color.pre_color
            "R" -> null
            else -> null
        }

        val entryWrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val playsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (expandedGames.contains(gamePk)) View.VISIBLE else View.GONE
            setPadding(dpToPx(14), 0, dpToPx(14), dpToPx(12))
        }

        val infoRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), 0)

            isClickable = true
            isFocusable = true
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            setBackgroundResource(typedValue.resourceId)

            setOnClickListener {
                if (gamePk == 0L) return@setOnClickListener
                if (expandedGames.contains(gamePk)) {
                    expandedGames.remove(gamePk)
                    playsContainer.visibility = View.GONE
                } else {
                    expandedGames.add(gamePk)
                    playsContainer.visibility = View.VISIBLE
                    if (viewModel.gamePlays.value?.get(gamePk) == null) {
                        viewModel.loadPlaysForGame(gamePk)
                    }
                }
            }
        }

        val dateText = TextView(requireContext()).apply {
            text = formatDate(split.date ?: split.gameDate?.take(10))
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dpToPx(8) }
        }
        infoRow.addView(dateText)

        if (badgeInfo != null) {
            val badge = TextView(requireContext()).apply {
                text = badgeInfo.first
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, badgeInfo.second))
                background = ContextCompat.getDrawable(context, R.drawable.bg_level_badge)
                setPadding(dpToPx(5), dpToPx(2), dpToPx(5), dpToPx(2))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dpToPx(8) }
            }
            infoRow.addView(badge)
        }

        val homeAwayPrefix = when (split.isHome) {
            true  -> "vs "
            false -> "@ "
            null  -> ""
        }
        val opponentText = TextView(requireContext()).apply {
            text = "$homeAwayPrefix${split.opponent?.name ?: "Opponent"}"
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        infoRow.addView(opponentText)

        val winLoss = split.isWin?.let { if (it) " W" else " L" } ?: ""
        val resultText = TextView(requireContext()).apply {
            text = winLoss
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(
                context,
                if (split.isWin == true) R.color.live_green else R.color.mlb_red
            ))
        }
        infoRow.addView(resultText)

        entryWrapper.addView(infoRow)

        val statsRowData = split.stat
        if (statsRowData != null) {
            val hsv = HorizontalScrollView(requireContext()).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(12))

                var startX = 0f
                var startY = 0f
                val touchSlop = 10f

                setOnTouchListener { _, event ->
                    @Suppress("ClickableViewAccessibility")
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.x
                            startY = event.y
                            false
                        }
                        MotionEvent.ACTION_UP -> {
                            val diffX = abs(event.x - startX)
                            val diffY = abs(event.y - startY)
                            if (diffX < touchSlop && diffY < touchSlop) {
                                infoRow.performClick()
                            }
                            false
                        }
                        else -> false
                    }
                }
            }

            val statsRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            if (statsRowData.inningsPitched != null) {
                addStatBox(statsRow, "IP",  statsRowData.inningsPitched!!)
                addStatBox(statsRow, "H",   (statsRowData.pitchingHits ?: statsRowData.hits ?: 0).toString())
                addStatBox(statsRow, "R",   (statsRowData.pitchingRuns ?: statsRowData.runs ?: 0).toString())
                addStatBox(statsRow, "ER",  (statsRowData.earnedRuns ?: 0).toString())
                addStatBox(statsRow, "HR",  (statsRowData.pitchingHomeRuns ?: statsRowData.homeRuns ?: 0).toString())
                addStatBox(statsRow, "HB",  (statsRowData.hitBatters ?: 0).toString())
                addStatBox(statsRow, "BB",  (statsRowData.baseOnBalls ?: 0).toString())
                addStatBox(statsRow, "IBB", (statsRowData.intentionalWalks ?: 0).toString(), widthDp = 40)
                addStatBox(statsRow, "SO",  (statsRowData.strikeOuts ?: 0).toString())
                addStatBox(statsRow, "AVG", statsRowData.avg ?: ".---", widthDp = 48)
                addStatBox(statsRow, "WHIP", statsRowData.whip ?: "-.--", widthDp = 48)

                val decision = when {
                    statsRowData.wins == 1 -> "W"
                    statsRowData.losses == 1 -> "L"
                    statsRowData.saves == 1 -> "S"
                    statsRowData.holds == 1 -> "H"
                    else -> null
                }
                if (decision != null) addStatBox(statsRow, "DEC", decision)
            } else if ((statsRowData.atBats ?: 0) > 0 || (statsRowData.plateAppearances ?: 0) > 0) {
                addStatBox(statsRow, "AB",  (statsRowData.atBats ?: 0).toString())
                addStatBox(statsRow, "R",   (statsRowData.runs   ?: 0).toString())
                addStatBox(statsRow, "H",   (statsRowData.hits   ?: 0).toString())
                addStatBox(statsRow, "2B",  (statsRowData.doubles ?: 0).toString())
                addStatBox(statsRow, "3B",  (statsRowData.triples ?: 0).toString())
                addStatBox(statsRow, "HR",  (statsRowData.homeRuns ?: 0).toString())
                addStatBox(statsRow, "RBI", (statsRowData.rbi    ?: 0).toString())
                addStatBox(statsRow, "BB",  (statsRowData.baseOnBalls ?: 0).toString())
                addStatBox(statsRow, "SO",  (statsRowData.strikeOuts ?: 0).toString())
                addStatBox(statsRow, "SB",  (statsRowData.stolenBases ?: 0).toString())
                addStatBox(statsRow, "AVG", statsRowData.avg ?: ".---", widthDp = 44)
                addStatBox(statsRow, "OBP", statsRowData.obp ?: ".---", widthDp = 44)
                addStatBox(statsRow, "SLG", statsRowData.slg ?: ".---", widthDp = 44)
                addStatBox(statsRow, "OPS", statsRowData.ops ?: ".---", widthDp = 50)
            }
            hsv.addView(statsRow)
            entryWrapper.addView(hsv)
        }

        entryWrapper.addView(playsContainer)

        if (expandedGames.contains(gamePk)) {
            val plays = viewModel.gamePlays.value?.get(gamePk)
            if (plays == null) {
                playsContainer.addView(TextView(requireContext()).apply {
                    text = "Loading plays..."
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    setPadding(dpToPx(8), dpToPx(8), 0, dpToPx(8))
                })
            } else {
                val playerPlays = plays.filter { it.batterId == args.playerId || it.pitcherId == args.playerId }
                if (playerPlays.isEmpty()) {
                    playsContainer.addView(TextView(requireContext()).apply {
                        text = "No at-bats found for this player."
                        textSize = 12f
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        setPadding(dpToPx(8), dpToPx(8), 0, dpToPx(8))
                    })
                } else {
                    playerPlays.forEach { play ->
                        playsContainer.addView(buildPlayDetailView(play))
                    }
                }
            }
        }

        container.addView(entryWrapper)

        container.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
            )
            setBackgroundColor(0x15FFFFFF)
        })
    }

    private fun buildPlayDetailView(play: PlaySummary): View {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(10))
            background = ContextCompat.getDrawable(context, R.drawable.bg_play_item)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, dpToPx(6))
            layoutParams = lp
        }

        val header = TextView(requireContext()).apply {
            val role = if (play.batterId == args.playerId) "vs ${play.pitcherName ?: "Pitcher"}" else "vs ${play.batterName ?: "Batter"}"
            text = "INNING ${play.inning} · $role"
            textSize = 10f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.05f
        }
        container.addView(header)

        val desc = TextView(requireContext()).apply {
            text = play.description
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(0, dpToPx(4), 0, dpToPx(6))
        }
        container.addView(desc)

        if (play.pitches.isNotEmpty()) {
            val pitchRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            play.pitches.forEach { pitch ->
                val dot = View(requireContext()).apply {
                    val size = dpToPx(10)
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dpToPx(6) }
                    val colorRes = when (pitch.callCode.uppercase()) {
                        "B" -> R.color.live_green
                        "S", "C" -> R.color.mlb_red
                        "X" -> R.color.mlb_gold
                        else -> R.color.text_secondary
                    }
                    background = ContextCompat.getDrawable(context, R.drawable.shape_out_filled)
                    backgroundTintList = ContextCompat.getColorStateList(context, colorRes)
                }
                pitchRow.addView(dot)
            }
            container.addView(pitchRow)
        }

        return container
    }

    private fun addEmptyMessageToLog(msg: String) {
        binding.layoutGameLogContainer.addView(TextView(requireContext()).apply {
            text = msg
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(24), 0, dpToPx(24))
        })
    }

    private fun addStatBox(container: LinearLayout, label: String, value: String, widthDp: Int = 38) {
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(widthDp),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        box.addView(TextView(requireContext()).apply {
            text = label
            textSize = 9f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        })

        box.addView(TextView(requireContext()).apply {
            text = value
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        })

        container.addView(box)
    }

    private fun addSubsectionHeader(container: LinearLayout, title: String) {
        container.addView(TextView(requireContext()).apply {
            text = title
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(4))
        })
    }

    private fun addSectionHeader(container: LinearLayout, title: String) {
        container.addView(TextView(requireContext()).apply {
            text = title
            setTextColor(ContextCompat.getColor(context, R.color.mlb_red))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(8))
        })
    }

    private fun addEmptyMessage(container: LinearLayout, msg: String) {
        container.addView(TextView(requireContext()).apply {
            text = msg
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(32), 0, dpToPx(32))
        })
    }

    private fun formatDate(dateStr: String?): String {
        if (dateStr == null) return ""
        return try {
            val isoDate = dateStr.take(10)
            if (isoDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val formatter = SimpleDateFormat("MMM d", Locale.US)
                return formatter.format(parser.parse(isoDate)!!)
            }
            if (dateStr.matches(Regex("\\d{2}/\\d{2}"))) {
                val parser = SimpleDateFormat("MM/dd", Locale.US)
                val formatter = SimpleDateFormat("MMM d", Locale.US)
                return formatter.format(parser.parse(dateStr)!!)
            }
            dateStr
        } catch (e: Exception) { dateStr }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PlayerStatsViewModel(
    private val playerId: Int,
    private val repository: GamesRepository
) : ViewModel() {

    private val _playerData     = androidx.lifecycle.MutableLiveData<BoxscorePerson?>()
    val playerData: androidx.lifecycle.LiveData<BoxscorePerson?> = _playerData

    private val _loading        = androidx.lifecycle.MutableLiveData<Boolean>()
    val loading: androidx.lifecycle.LiveData<Boolean> = _loading

    private val _error          = androidx.lifecycle.MutableLiveData<String?>()
    val error: androidx.lifecycle.LiveData<String?> = _error

    private val _availableYears = androidx.lifecycle.MutableLiveData<List<String>>()
    val availableYears: androidx.lifecycle.LiveData<List<String>> = _availableYears

    val selectedYear = androidx.lifecycle.MutableLiveData<String>()

    private val _gamePlays = androidx.lifecycle.MutableLiveData<Map<Long, List<PlaySummary>>>(emptyMap())
    val gamePlays: androidx.lifecycle.LiveData<Map<Long, List<PlaySummary>>> = _gamePlays

    init { loadCareerThenCurrentYear() }

    private fun loadCareerThenCurrentYear() {
        viewModelScope.launch {
            _loading.value = true
            _error.value   = null

            val seasons: List<String> = runCatching { repository.getPlayerSeasons(playerId) }
                .getOrElse { emptyList() }
            val years = seasons.takeIf { it.isNotEmpty() } ?: fallbackYears()

            _availableYears.value = years

            if (years.isNotEmpty()) {
                val year = years.first()
                selectedYear.value = year
                loadStatsForYear(year)
            }
        }
    }

    fun loadStatsForYear(year: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value   = null
            selectedYear.value = year
            _gamePlays.value = emptyMap()

            runCatching { repository.getPlayerStats(playerId, year) }
                .onSuccess { person ->
                    if (person == null) _error.value = "No data returned for $year."
                    else                _playerData.value = person
                }
                .onFailure { _error.value = "Couldn't load stats for $year." }

            _loading.value = false
        }
    }

    fun loadPlaysForGame(gamePk: Long) {
        viewModelScope.launch {
            runCatching { repository.getGamePlays(gamePk) }.onSuccess { plays ->
                val current = _gamePlays.value ?: emptyMap()
                _gamePlays.value = current + (gamePk to plays)
            }
        }
    }

    private fun fallbackYears(): List<String> {
        val current = Calendar.getInstance().get(Calendar.YEAR)
        return (current downTo current - 9).map { it.toString() }
    }
}

class PlayerStatsViewModelFactory(
    private val playerId: Int,
    private val repository: GamesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PlayerStatsViewModel(playerId, repository) as T
}
