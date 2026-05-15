package com.baseballnerd.app.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.baseballnerd.app.data.repository.GamesRepository
import com.baseballnerd.app.databinding.FragmentStatCategoryBinding

class StatCategoryFragment : Fragment() {

    private var _binding: FragmentStatCategoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatsViewModel by viewModels {
        StatsViewModelFactory(GamesRepository())
    }

    private var categoryKey: String? = null
    private var categoryGroup: String? = null
    private var season: String? = null

    companion object {
        private const val ARG_KEY = "category_key"
        private const val ARG_GROUP = "category_group"
        private const val ARG_SEASON = "season"

        fun newInstance(key: String, group: String, season: String): StatCategoryFragment {
            val fragment = StatCategoryFragment()
            val args = Bundle()
            args.putString(ARG_KEY, key)
            args.putString(ARG_GROUP, group)
            args.putString(ARG_SEASON, season)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            categoryKey = it.getString(ARG_KEY)
            categoryGroup = it.getString(ARG_GROUP)
            season = it.getString(ARG_SEASON)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        viewModel.leaders.observe(viewLifecycleOwner) { leaders ->
            (binding.rvLeaders.adapter as LeadersAdapter).submitList(leaders)
        }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Initialize with data from arguments
        categoryKey?.let { key ->
            categoryGroup?.let { group ->
                season?.let { s ->
                    viewModel.setSeason(s)
                    viewModel.setCategory(StatsFragment.StatCategory("", key, group))
                }
            }
        }
    }

    fun updateSeason(newSeason: String) {
        season = newSeason
        viewModel.setSeason(newSeason)
    }

    private fun setupRecyclerView() {
        binding.rvLeaders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLeaders.adapter = LeadersAdapter { leader ->
            leader.person?.id?.let { playerId ->
                val action = StatsFragmentDirections.actionStatsFragmentToPlayerStatsFragment(playerId)
                findNavController().navigate(action)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
