package com.baseballnerd.app.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.baseballnerd.app.databinding.FragmentStatsBinding
import java.util.*

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val categories = listOf(
        StatCategory("AVG", "avg", "hitting"),
        StatCategory("HR", "homeRuns", "hitting"),
        StatCategory("RBI", "runsBattedIn", "hitting"),
        StatCategory("OPS", "onBasePlusSlugging", "hitting"),
        StatCategory("SB", "stolenBases", "hitting"),
        StatCategory("ERA", "earnedRunAverage", "pitching"),
        StatCategory("W", "wins", "pitching"),
        StatCategory("K", "strikeOuts", "pitching"),
        StatCategory("WHIP", "whip", "pitching"),
        StatCategory("SV", "saves", "pitching")
    )

    private var currentSeason = Calendar.getInstance().get(Calendar.YEAR).toString()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSeasonSpinner()
        setupViewPager()
    }

    private fun setupSeasonSpinner() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear downTo 1876).map { it.toString() }
        val adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, years) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? android.widget.TextView)?.setTextColor(android.graphics.Color.WHITE)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSeason.adapter = adapter

        binding.spinnerSeason.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSeason = years[position]
                updateFragmentsSeason()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupViewPager() {
        val adapter = StatsPagerAdapter(this, categories, currentSeason)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = categories[position].label
        }.attach()
    }

    private fun updateFragmentsSeason() {
        // Iterate through active fragments and update them
        childFragmentManager.fragments.forEach { fragment ->
            if (fragment is StatCategoryFragment) {
                fragment.updateSeason(currentSeason)
            }
        }

        // Also update the adapter's reference for future fragment creations
        (binding.viewPager.adapter as? StatsPagerAdapter)?.updateSeason(currentSeason)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class StatCategory(val label: String, val key: String, val group: String)
}

class StatsPagerAdapter(
    fragment: Fragment,
    private val categories: List<StatsFragment.StatCategory>,
    private var season: String
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = categories.size

    override fun createFragment(position: Int): Fragment {
        val category = categories[position]
        return StatCategoryFragment.newInstance(category.key, category.group, season)
    }

    fun updateSeason(newSeason: String) {
        season = newSeason
    }
}
