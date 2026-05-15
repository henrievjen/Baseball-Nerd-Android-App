package com.baseballnerd.app.ui.stats

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.baseballnerd.app.R
import com.baseballnerd.app.data.api.MlbApiClient
import com.baseballnerd.app.data.api.MlbApiService
import com.baseballnerd.app.data.model.BoxscorePerson
import com.baseballnerd.app.data.model.TeamInfo3 as Team
import com.baseballnerd.app.databinding.FragmentPositionsBinding
import kotlinx.coroutines.launch

data class PlayerPositionStat(
    val player: BoxscorePerson,
    val gamesStarted: Int
)

class PositionsFragment : Fragment() {

    private var _binding: FragmentPositionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PositionsViewModel by viewModels {
        PositionsViewModelFactory(MlbApiClient.service)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPositionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        setupTeamSpinner()
        setupObservers()
    }

    private fun setupTeamSpinner() {
        viewModel.teams.observe(viewLifecycleOwner) { teams: List<Team> ->
            val teamNames = teams.map { it.name }
            val adapter = ArrayAdapter<String>(
                requireContext(),
                R.layout.item_spinner_year,
                teamNames
            )
            adapter.setDropDownViewResource(R.layout.item_spinner_year_dropdown)
            binding.spinnerTeam.adapter = adapter

            binding.spinnerTeam.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?, view: View?, position: Int, id: Long
                    ) {
                        viewModel.selectTeam(teams[position])
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
        }
    }

    private fun setupObservers() {
        viewModel.loading.observe(viewLifecycleOwner) {
            binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
        }
        viewModel.rosterDepth.observe(viewLifecycleOwner) { depth ->
            renderRosterDepth(depth)
        }
    }

    private fun renderRosterDepth(depth: Map<String, List<PlayerPositionStat>>) {
        val container = binding.containerRoster
        container.removeAllViews()

        val positionOrder = listOf("P", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "DH")
        
        positionOrder.forEach { posCode ->
            val playersAtPos = depth[posCode] ?: emptyList()
            addPositionHeader(container, getFullPositionName(posCode))
            
            if (playersAtPos.isEmpty()) {
                addEmptyMessage(container)
            } else {
                playersAtPos.forEach { stat ->
                    container.addView(buildPlayerRow(stat))
                }
            }
            addDivider(container)
        }
    }

    private fun buildPlayerRow(stat: PlayerPositionStat): View {
        val player = stat.player
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            isClickable = true
            isFocusable = true
            val tv = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            setBackgroundResource(tv.resourceId)
            setOnClickListener {
                player.id?.let { id ->
                    findNavController().navigate(
                        PositionsFragmentDirections
                            .actionPositionsFragmentToPlayerStatsFragment(id)
                    )
                }
            }
        }

        // Photo
        val photoSize = dpToPx(40)
        val photo = android.widget.ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(photoSize, photoSize).apply {
                marginEnd = dpToPx(12)
            }
            load("https://midfield.mlbstatic.com/v1/people/${player.id}/spots/120") {
                crossfade(true)
                placeholder(R.drawable.ic_baseball_placeholder)
                error(R.drawable.ic_baseball_placeholder)
            }
            background = ContextCompat.getDrawable(context, R.drawable.shape_team_circle)
            clipToOutline = true
        }
        row.addView(photo)

        // Name and Info
        val textLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textLayout.addView(TextView(requireContext()).apply {
            text = player.fullName
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
        })

        textLayout.addView(TextView(requireContext()).apply {
            val primaryPos = player.primaryPosition?.abbreviation ?: ""
            text = if (primaryPos.isNotEmpty()) "Primary: $primaryPos" else ""
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        })
        row.addView(textLayout)

        // Starts Count
        row.addView(TextView(requireContext()).apply {
            text = getString(R.string.gs_format, stat.gamesStarted)
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
        })

        return row
    }

    private fun addPositionHeader(container: LinearLayout, name: String) {
        container.addView(TextView(requireContext()).apply {
            text = name.uppercase()
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.mlb_red))
            setPadding(dpToPx(16), dpToPx(20), dpToPx(16), dpToPx(8))
            letterSpacing = 0.05f
        })
    }

    private fun addEmptyMessage(container: LinearLayout) {
        container.addView(TextView(requireContext()).apply {
            text = getString(R.string.no_starts_at_position)
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(16))
        })
    }

    private fun addDivider(container: LinearLayout) {
        container.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)).apply {
                setMargins(dpToPx(16), dpToPx(4), dpToPx(16), 0)
            }
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
        })
    }

    private fun getFullPositionName(code: String): String = when(code) {
        "P" -> "Pitchers"
        "C" -> "Catchers"
        "1B" -> "First Base"
        "2B" -> "Second Base"
        "3B" -> "Third Base"
        "SS" -> "Shortstop"
        "LF" -> "Left Field"
        "CF" -> "Center Field"
        "RF" -> "Right Field"
        "DH" -> "Designated Hitter"
        else -> code
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

class PositionsViewModel(private val api: MlbApiService) : ViewModel() {

    private val _teams = androidx.lifecycle.MutableLiveData<List<Team>>()
    val teams: androidx.lifecycle.LiveData<List<Team>> = _teams

    private val _rosterDepth = androidx.lifecycle.MutableLiveData<Map<String, List<PlayerPositionStat>>>()
    val rosterDepth: androidx.lifecycle.LiveData<Map<String, List<PlayerPositionStat>>> = _rosterDepth

    private val _loading = androidx.lifecycle.MutableLiveData<Boolean>()
    val loading: androidx.lifecycle.LiveData<Boolean> = _loading

    init { loadTeams() }

    private fun loadTeams() {
        viewModelScope.launch {
            runCatching { api.getTeams() }.onSuccess { resp ->
                _teams.value = resp.teams?.sortedBy { it.name } ?: emptyList()
            }
        }
    }

    fun selectTeam(team: Team) {
        viewModelScope.launch {
            _loading.value = true
            runCatching {
                val rosterResp = api.getTeamRoster(
                    teamId = team.id,
                    hydrate = "person(stats(type=season,group=[fielding,hitting,pitching]))"
                )
                val players = rosterResp.roster?.mapNotNull { it.person } ?: emptyList()

                val positionMap = mutableMapOf<String, MutableList<PlayerPositionStat>>()
                val fielderCodes = listOf("C", "1B", "2B", "3B", "SS", "LF", "CF", "RF")

                players.forEach { player ->
                    val playerPosGS = mutableMapOf<String, Int>()
                    
                    // 1. Fielding Positions
                    player.stats?.find { it.group?.displayName?.equals("fielding", true) == true }?.splits?.forEach { split ->
                        val pos = split.position?.abbreviation ?: split.stat?.position?.abbreviation
                        val gs = split.stat?.gamesStarted ?: 0
                        if (pos != null && gs > 0) {
                            playerPosGS[pos] = (playerPosGS[pos] ?: 0) + gs
                        }
                    }

                    // 2. Pitchers
                    player.stats?.find { it.group?.displayName?.equals("pitching", true) == true }?.splits?.forEach { split ->
                        val gs = split.stat?.gamesStarted ?: 0
                        if (gs > 0) {
                            playerPosGS["P"] = (playerPosGS["P"] ?: 0) + gs
                        }
                    }

                    // 3. Hitting starts check for DH
                    val hittingStats = player.stats?.find { 
                        it.group?.displayName?.equals("hitting", true) == true || 
                        it.group?.displayName?.equals("batting", true) == true 
                    }
                    val totalHittingGS = hittingStats?.splits?.sumOf { it.stat?.gamesStarted ?: 0 } ?: 0
                    
                    // Sum all non-DH starts
                    val totalNonDHGS = playerPosGS.filter { it.key != "DH" }.values.sum()
                    val inferredDHGS = totalHittingGS - totalNonDHGS
                    
                    if (inferredDHGS > 0 && inferredDHGS > (playerPosGS["DH"] ?: 0)) {
                        playerPosGS["DH"] = inferredDHGS
                    }

                    // Add all found positions to the map
                    playerPosGS.forEach { (pos, gs) ->
                        if (pos in fielderCodes || pos == "P" || pos == "DH") {
                            positionMap.getOrPut(pos) { mutableListOf() }.add(PlayerPositionStat(player, gs))
                        }
                    }
                }

                // Sort each position by games started descending
                positionMap.forEach { (pos, list) ->
                    list.sortByDescending { it.gamesStarted }
                }
                positionMap.toMap()

            }.onSuccess {
                _rosterDepth.value = it
            }.onFailure {
                _rosterDepth.value = emptyMap()
            }
            _loading.value = false
        }
    }
}

class PositionsViewModelFactory(private val api: MlbApiService) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PositionsViewModel(api) as T
}
