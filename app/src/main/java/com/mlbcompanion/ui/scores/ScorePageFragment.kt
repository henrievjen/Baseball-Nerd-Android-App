package com.baseballnerd.app.ui.scores

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.baseballnerd.app.data.repository.GamesRepository
import com.baseballnerd.app.databinding.FragmentScorePageBinding
import com.baseballnerd.app.util.SettingsManager

class ScorePageFragment : Fragment() {

    private var _binding: FragmentScorePageBinding? = null
    private val binding get() = _binding!!

    private val date: String by lazy { arguments?.getString(ARG_DATE) ?: "" }

    private val viewModel: ScorePageViewModel by viewModels {
        ScorePageViewModelFactory(date, GamesRepository())
    }

    private val adapter = GameCardAdapter(
        onCardClick = { game ->
            // Pass both gamePk AND the date so the detail page can fetch the right game
            val args = Bundle().apply {
                putLong("gamePk", game.gamePk)
                putString("date", date)
            }
            findNavController().navigate(com.baseballnerd.app.R.id.gameDetailFragment, args)
        },
        onTeamClick = { teamId, teamName ->
            val args = Bundle().apply {
                putInt("teamId", teamId)
                putString("teamName", teamName)
            }
            findNavController().navigate(com.baseballnerd.app.R.id.teamScheduleFragment, args)
        },
        onPlayerClick = { playerId ->
            val args = Bundle().apply {
                putInt("playerId", playerId)
            }
            findNavController().navigate(com.baseballnerd.app.R.id.playerStatsFragment, args)
        }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScorePageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        applyLayoutSettings()
        
        binding.recyclerViewGames.adapter = adapter

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ScoresUiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.recyclerViewGames.visibility = View.GONE
                    binding.tvError.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                }
                is ScoresUiState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (state.games.isEmpty()) {
                        binding.recyclerViewGames.visibility = View.GONE
                        binding.tvError.visibility = View.VISIBLE
                        binding.tvError.text = "No games scheduled."
                    } else {
                        binding.recyclerViewGames.visibility = View.VISIBLE
                        binding.tvError.visibility = View.GONE
                        adapter.submitList(state.games)
                    }
                }
                is ScoresUiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.recyclerViewGames.visibility = View.GONE
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = "Couldn't load games.\n${state.message}"
                }
            }
        }
        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.refresh() }
    }

    private fun applyLayoutSettings() {
        val settings = SettingsManager.get(requireContext())
        if (settings.scoreViewType == SettingsManager.SCORE_VIEW_CARD) {
            binding.recyclerViewGames.layoutManager = GridLayoutManager(requireContext(), 2)
            adapter.setViewType(GameCardAdapter.VIEW_TYPE_GRID)
        } else {
            binding.recyclerViewGames.layoutManager = LinearLayoutManager(requireContext())
            adapter.setViewType(GameCardAdapter.VIEW_TYPE_LIST)
        }
    }

    override fun onResume() { 
        super.onResume()
        applyLayoutSettings()
        viewModel.startPolling() 
    }

    override fun onPause()  { super.onPause();  viewModel.stopPolling()  }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_DATE = "arg_date"
        fun newInstance(date: String) = ScorePageFragment().apply {
            arguments = Bundle().apply { putString(ARG_DATE, date) }
        }
    }
}

class ScorePageViewModelFactory(
    private val date: String,
    private val repository: GamesRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ScorePageViewModel(date, repository) as T
}
