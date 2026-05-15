package com.baseballnerd.app.ui.standings

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import coil.decode.SvgDecoder
import coil.load
import com.baseballnerd.app.R
import com.baseballnerd.app.data.model.StandingsDivision
import com.baseballnerd.app.data.model.StandingsRecord
import com.baseballnerd.app.data.model.WildCardEntry
import com.baseballnerd.app.databinding.FragmentStandingsBinding
import com.baseballnerd.app.util.TeamColors

class StandingsFragment : Fragment() {

    private var _binding: FragmentStandingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StandingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStandingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = StandingsPagerAdapter(this)
        binding.viewPager.adapter = adapter

        binding.tabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_division -> binding.viewPager.currentItem = 0
                    R.id.btn_wildcard -> binding.viewPager.currentItem = 1
                }
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 0) {
                    binding.tabGroup.check(R.id.btn_division)
                } else {
                    binding.tabGroup.check(R.id.btn_wildcard)
                }
            }
        })

        binding.tabGroup.check(R.id.btn_division)

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is StandingsUiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.viewPager.visibility = View.GONE
                    binding.tvError.visibility = View.GONE
                }
                is StandingsUiState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.visibility = View.GONE
                    binding.viewPager.visibility = View.VISIBLE
                }
                is StandingsUiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.viewPager.visibility = View.GONE
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = "Couldn't load standings.\n${state.message}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class StandingsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return if (position == 0) {
                DivisionStandingsFragment.newInstance()
            } else {
                WildCardStandingsFragment.newInstance()
            }
        }
    }
}

/** Base for the sub-pages to share the table building logic */
abstract class StandingsPageFragment : Fragment() {
    protected val viewModel: StandingsViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(LinearLayout(requireContext()).apply {
                id = View.generateViewId()
                tag = "content_container"
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 32)
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }
    }

    protected fun makeRowView(ctx: Context, isHeader: Boolean, striped: Boolean = false, isHighlighted: Boolean = false): TableRow {
        return TableRow(ctx).apply {
            val isDark = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            when {
                !isHeader && isHighlighted -> setBackgroundColor(if (isDark) 0x22FFFFFF else 0x12000000)
                !isHeader && striped -> setBackgroundColor(ContextCompat.getColor(ctx, R.color.surface_elevated))
            }
        }
    }

    protected fun addCell(ctx: Context, row: TableRow, text: String, weight: Float = 0f, isHeader: Boolean = false, columnType: String = "") {
        val tv = TextView(ctx).apply {
            this.text = text
            textSize = if (isHeader) 10f else 13f
            setTextColor(ContextCompat.getColor(ctx, if (isHeader) R.color.text_secondary else R.color.text_primary))
            setPadding(dpToPx(ctx, 12), dpToPx(ctx, 10), dpToPx(ctx, 12), dpToPx(ctx, 10))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }

        val width = when (columnType) {
            "W", "L" -> dpToPx(ctx, 45)
            "PCT" -> dpToPx(ctx, 60)
            "GB", "WCGB" -> dpToPx(ctx, 60)
            else -> if (weight > 0f) 0 else TableRow.LayoutParams.WRAP_CONTENT
        }

        val params = TableRow.LayoutParams(width, TableRow.LayoutParams.WRAP_CONTENT)
        if (weight > 0f) params.weight = weight
        tv.layoutParams = params
        row.addView(tv)
    }

    protected fun addTeamCell(ctx: Context, row: TableRow, teamName: String, teamId: Int, isHeader: Boolean = false) {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(ctx, 12), dpToPx(ctx, 10), dpToPx(ctx, 12), dpToPx(ctx, 10))
            if (!isHeader) {
                isClickable = true
                isFocusable = true
                val outValue = TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    val args = Bundle().apply {
                        putInt("teamId", teamId)
                        putString("teamName", teamName)
                    }
                    findNavController().navigate(R.id.teamScheduleFragment, args)
                }
            }
        }

        if (!isHeader) {
            val logoContainer = FrameLayout(ctx).apply {
                val size = dpToPx(ctx, 24)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dpToPx(ctx, 8)
                }
                background = ContextCompat.getDrawable(ctx, R.drawable.shape_team_circle)
                backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.white)
            }

            val logoView = ImageView(ctx).apply {
                val iconSize = dpToPx(ctx, 16)
                layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                load(TeamColors.getLogoUrl(teamId)) {
                    decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
                }
            }
            logoContainer.addView(logoView)
            container.addView(logoContainer)
        }

        val tv = TextView(ctx).apply {
            text = teamName
            textSize = if (isHeader) 10f else 13f
            setTextColor(ContextCompat.getColor(ctx, if (isHeader) R.color.text_secondary else R.color.text_primary))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        container.addView(tv)

        val params = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        container.layoutParams = params
        row.addView(container)
    }

    protected fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}

class DivisionStandingsFragment : StandingsPageFragment() {
    companion object { fun newInstance() = DivisionStandingsFragment() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = view.findViewWithTag<LinearLayout>("content_container")
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            if (state is StandingsUiState.Success) {
                container.removeAllViews()
                
                listOf("AL", "NL").forEach { league ->
                    val filteredDivs = state.divisions.filter { div ->
                        if (league == "AL") {
                            div.league.contains("American", ignoreCase = true) || div.league.equals("AL", ignoreCase = true)
                        } else {
                            div.league.contains("National", ignoreCase = true) || div.league.equals("NL", ignoreCase = true)
                        }
                    }
                    
                    if (filteredDivs.isEmpty()) return@forEach

                    // League Header
                    val leagueHeader = TextView(requireContext()).apply {
                        text = if (league == "AL") "AMERICAN LEAGUE" else "NATIONAL LEAGUE"
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.mlb_red))
                        textSize = 14f
                        setPadding(dpToPx(requireContext(), 16), dpToPx(requireContext(), 24), dpToPx(requireContext(), 16), dpToPx(requireContext(), 8))
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    container.addView(leagueHeader)

                    filteredDivs.forEach { div ->
                        val header = TextView(requireContext()).apply {
                            text = div.divisionName
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                            textSize = 13f
                            setPadding(dpToPx(requireContext(), 16), dpToPx(requireContext(), 20), dpToPx(requireContext(), 16), dpToPx(requireContext(), 8))
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }
                        container.addView(header)

                        val colRow = makeRowView(requireContext(), isHeader = true)
                        addTeamCell(requireContext(), colRow, "TEAM", 0, isHeader = true)
                        addCell(requireContext(), colRow, "W", isHeader = true, columnType = "W")
                        addCell(requireContext(), colRow, "L", isHeader = true, columnType = "L")
                        addCell(requireContext(), colRow, "PCT", isHeader = true, columnType = "PCT")
                        addCell(requireContext(), colRow, "GB", isHeader = true, columnType = "GB")
                        container.addView(colRow)

                        div.teams.forEachIndexed { i, team ->
                            val row = makeRowView(requireContext(), isHeader = false, striped = i % 2 == 1)
                            addTeamCell(requireContext(), row, team.teamName, team.teamId)
                            addCell(requireContext(), row, team.wins.toString(), columnType = "W")
                            addCell(requireContext(), row, team.losses.toString(), columnType = "L")
                            addCell(requireContext(), row, team.pct, columnType = "PCT")
                            addCell(requireContext(), row, team.gb, columnType = "GB")
                            container.addView(row)
                        }
                    }
                }
            }
        }
    }
}

class WildCardStandingsFragment : StandingsPageFragment() {
    companion object { fun newInstance() = WildCardStandingsFragment() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = view.findViewWithTag<LinearLayout>("content_container")
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            if (state is StandingsUiState.Success) {
                container.removeAllViews()
                
                // Group wild card entries by league
                val grouped = state.wildCard.groupBy { it.league }
                
                listOf("AL", "NL").forEach { league ->
                    val entries = grouped[league] ?: emptyList()
                    val leaders = state.divisions.filter { div ->
                        if (league == "AL") {
                            div.league.contains("American", ignoreCase = true) || div.league.equals("AL", ignoreCase = true)
                        } else {
                            div.league.contains("National", ignoreCase = true) || div.league.equals("NL", ignoreCase = true)
                        }
                    }.mapNotNull { div ->
                        div.teams.firstOrNull { it.divisionRank == 1 } ?: div.teams.minByOrNull { it.divisionRank }
                    }.sortedByDescending { it.pct }

                    if (entries.isEmpty() && leaders.isEmpty()) return@forEach
                    
                    // League Header
                    val leagueHeader = TextView(requireContext()).apply {
                        text = if (league == "AL") "AMERICAN LEAGUE" else "NATIONAL LEAGUE"
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.mlb_red))
                        textSize = 14f
                        setPadding(dpToPx(requireContext(), 16), dpToPx(requireContext(), 24), dpToPx(requireContext(), 16), dpToPx(requireContext(), 8))
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    container.addView(leagueHeader)

                    // 1. Division Leaders Section
                    if (leaders.isNotEmpty()) {
                        val divLeaderHeader = TextView(requireContext()).apply {
                            text = "Division Leaders"
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                            textSize = 12f
                            setPadding(dpToPx(requireContext(), 16), dpToPx(requireContext(), 8), dpToPx(requireContext(), 16), dpToPx(requireContext(), 4))
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }
                        container.addView(divLeaderHeader)

                        val leaderColRow = makeRowView(requireContext(), isHeader = true)
                        addTeamCell(requireContext(), leaderColRow, "TEAM", 0, isHeader = true)
                        addCell(requireContext(), leaderColRow, "W", isHeader = true, columnType = "W")
                        addCell(requireContext(), leaderColRow, "L", isHeader = true, columnType = "L")
                        addCell(requireContext(), leaderColRow, "PCT", isHeader = true, columnType = "PCT")
                        addCell(requireContext(), leaderColRow, "GB", isHeader = true, columnType = "GB")
                        container.addView(leaderColRow)

                        leaders.forEachIndexed { i, team ->
                            val row = makeRowView(requireContext(), isHeader = false, striped = i % 2 == 1)
                            addTeamCell(requireContext(), row, team.teamName, team.teamId)
                            addCell(requireContext(), row, team.wins.toString(), columnType = "W")
                            addCell(requireContext(), row, team.losses.toString(), columnType = "L")
                            addCell(requireContext(), row, team.pct, columnType = "PCT")
                            addCell(requireContext(), row, team.gb, columnType = "GB")
                            container.addView(row)
                        }
                    }

                    // 2. Wild Card Standings Section
                    if (entries.isNotEmpty()) {
                        val wcHeader = TextView(requireContext()).apply {
                            text = "Wild Card Standings"
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                            textSize = 12f
                            setPadding(dpToPx(requireContext(), 16), dpToPx(requireContext(), 16), dpToPx(requireContext(), 16), dpToPx(requireContext(), 4))
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }
                        container.addView(wcHeader)
                        
                        val colRow = makeRowView(requireContext(), isHeader = true)
                        addTeamCell(requireContext(), colRow, "TEAM", 0, isHeader = true)
                        addCell(requireContext(), colRow, "W", isHeader = true, columnType = "W")
                        addCell(requireContext(), colRow, "L", isHeader = true, columnType = "L")
                        addCell(requireContext(), colRow, "PCT", isHeader = true, columnType = "PCT")
                        addCell(requireContext(), colRow, "WC GB", isHeader = true, columnType = "WCGB")
                        container.addView(colRow)

                        entries.forEachIndexed { i, entry ->
                            // Highlight the top 3 teams in each league's wild card race
                            val isHighlighted = i < 3
                            val row = makeRowView(
                                requireContext(), 
                                isHeader = false, 
                                striped = i % 2 == 1, 
                                isHighlighted = isHighlighted
                            )
                            addTeamCell(requireContext(), row, entry.teamName, entry.teamId)
                            addCell(requireContext(), row, entry.wins.toString(), columnType = "W")
                            addCell(requireContext(), row, entry.losses.toString(), columnType = "L")
                            addCell(requireContext(), row, entry.pct, columnType = "PCT")
                            addCell(requireContext(), row, entry.wcGb, columnType = "WCGB")
                            container.addView(row)

                            // Add a dashed line between the top three teams and the rest
                            if (i == 2 && entries.size > 3) {
                                val divider = View(requireContext()).apply {
                                    val h = dpToPx(requireContext(), 2)
                                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h).apply {
                                        setMargins(dpToPx(requireContext(), 16), dpToPx(requireContext(), 4), dpToPx(requireContext(), 16), dpToPx(requireContext(), 4))
                                    }
                                    background = ContextCompat.getDrawable(requireContext(), R.drawable.shape_dashed_line)
                                }
                                container.addView(divider)
                            }
                        }
                    }
                }
            }
        }
    }
}
