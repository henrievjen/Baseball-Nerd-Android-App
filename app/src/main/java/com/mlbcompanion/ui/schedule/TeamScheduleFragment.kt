package com.baseballnerd.app.ui.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.baseballnerd.app.R
import com.baseballnerd.app.data.model.GameCardModel
import com.baseballnerd.app.data.repository.GamesRepository
import com.baseballnerd.app.databinding.FragmentTeamScheduleBinding
import com.baseballnerd.app.ui.scores.GameCardAdapter
import java.text.SimpleDateFormat
import java.util.*

class TeamScheduleFragment : Fragment() {

    private var _binding: FragmentTeamScheduleBinding? = null
    private val binding get() = _binding!!

    private val args: TeamScheduleFragmentArgs by navArgs()

    private val viewModel: TeamScheduleViewModel by viewModels {
        TeamScheduleViewModelFactory(args.teamId, GamesRepository())
    }

    private lateinit var adapter: GameCardAdapter
    private var isFirstSelection = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvHeader.text = args.teamName
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        setupYearSpinner()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupYearSpinner() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear downTo 1876).map { it.toString() }
        val yearAdapter = object : ArrayAdapter<String>(requireContext(), R.layout.item_spinner_year, years) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                // The spinner lives in the navy header, so the selected-item text
                // must always be white regardless of the current light/dark theme.
                (view as? TextView)?.setTextColor(ContextCompat.getColor(context, R.color.white))
                return view
            }
        }
        yearAdapter.setDropDownViewResource(R.layout.item_spinner_year_dropdown)
        binding.spinnerYear.adapter = yearAdapter

        binding.spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedYearStr = years[position]
                if (!isFirstSelection) {
                    viewModel.loadSchedule(selectedYearStr.toInt())
                }
                isFirstSelection = false
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRecyclerView() {
        adapter = GameCardAdapter(
            onCardClick = { game: GameCardModel ->
                val action = TeamScheduleFragmentDirections.actionTeamScheduleFragmentToGameDetailFragment(
                    gamePk = game.gamePk,
                    date = game.gameDate.split("T").firstOrNull() ?: ""
                )
                findNavController().navigate(action)
            },
            onPlayerClick = { playerId ->
                val action = TeamScheduleFragmentDirections.actionTeamScheduleFragmentToPlayerStatsFragment(playerId)
                findNavController().navigate(action)
            }
        )
        binding.rvSchedule.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TeamScheduleFragment.adapter
        }
    }

    private fun observeViewModel() {
        viewModel.schedule.observe(viewLifecycleOwner) { games ->
            adapter.submitList(games) {
                val currentYearStr = Calendar.getInstance().get(Calendar.YEAR).toString()
                if (games.isNotEmpty() && binding.spinnerYear.selectedItem?.toString() == currentYearStr) {
                    scrollToUpcomingGame(games)
                }
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = error
            } else {
                binding.tvError.visibility = View.GONE
            }
        }
    }

    private fun scrollToUpcomingGame(games: List<GameCardModel>) {
        val now = Date()
        val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val upcomingIndex = games.indexOfFirst { game ->
            try {
                val gameDate = apiDateFormat.parse(game.gameDate)
                gameDate != null && gameDate.after(now)
            } catch (e: Exception) {
                false
            }
        }

        if (upcomingIndex != -1) {
            (binding.rvSchedule.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(upcomingIndex, 0)
        } else if (games.isNotEmpty()) {
            binding.rvSchedule.scrollToPosition(games.size - 1)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
