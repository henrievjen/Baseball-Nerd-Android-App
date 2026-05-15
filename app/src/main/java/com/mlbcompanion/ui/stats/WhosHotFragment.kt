package com.baseballnerd.app.ui.stats

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import coil.decode.SvgDecoder
import coil.load
import com.baseballnerd.app.R
import com.baseballnerd.app.data.model.StatSplit
import com.baseballnerd.app.data.repository.GamesRepository
import com.baseballnerd.app.databinding.FragmentWhosHotBinding
import com.baseballnerd.app.databinding.ItemHotPlayerBinding
import com.baseballnerd.app.util.TeamColors
import java.text.SimpleDateFormat
import java.util.Locale

class WhosHotFragment : Fragment() {

    private var _binding: FragmentWhosHotBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WhosHotViewModel by viewModels {
        WhosHotViewModelFactory(GamesRepository())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWhosHotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            binding.tvError.visibility = if (err != null) View.VISIBLE else View.GONE
            binding.tvError.text       = err ?: ""
        }

        viewModel.hotHitters.observe(viewLifecycleOwner) { hitters ->
            renderHitters(hitters)
        }

        viewModel.hotPitchers.observe(viewLifecycleOwner) { pitchers ->
            renderPitchers(pitchers)
        }
    }

    // ── Hitters ─────────────────────────────────────────────────────────────

    private fun renderHitters(players: List<HotHitterUiModel>) {
        binding.containerHitters.removeAllViews()

        if (players.isEmpty()) {
            if (viewModel.loading.value == false) showEmpty(binding.containerHitters)
            return
        }

        val inflater = LayoutInflater.from(context)
        players.forEachIndexed { index, player ->
            val item = ItemHotPlayerBinding.inflate(inflater, binding.containerHitters, false)

            item.tvPlayerName.text = "${index + 1}. ${player.name}"
            item.tvPlayerTeam.text = player.teamName
            item.ivPlayerPhoto.load("https://midfield.mlbstatic.com/v1/people/${player.id}/spots/120") {
                crossfade(true)
                placeholder(R.drawable.ic_baseball_placeholder)
                error(R.drawable.ic_baseball_placeholder)
            }
            item.ivTeamLogo.load(TeamColors.getLogoUrl(player.teamId)) {
                decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
                crossfade(true)
            }

            val toggleLog = {
                val isVisible = item.containerGameLog.visibility == View.VISIBLE
                item.containerGameLog.visibility = if (isVisible) View.GONE else View.VISIBLE
                if (!isVisible) renderHitterGameLog(player.recentLogs, item.layoutLogRows)
            }

            // Full stat line chips (Summary)
            val statsRow = buildHitterStatRow(player, toggleLog)
            item.containerStats.addView(statsRow)

            // OPS badge (highlight metric)
            item.tvHighlightStat.text    = "${player.ops} OPS"
            item.tvHighlightStat.visibility = View.VISIBLE
            item.tvHighlightStat.setOnClickListener { toggleLog() }

            // Click listener for player name (Details)
            item.tvPlayerName.setOnClickListener {
                val bundle = android.os.Bundle().apply { putInt("playerId", player.id) }
                findNavController().navigate(R.id.action_whosHotFragment_to_playerStatsFragment, bundle)
            }

            // Click listener for card (Expand Log)
            item.root.setOnClickListener { toggleLog() }

            binding.containerHitters.addView(item.root)
        }
    }

    private fun buildHitterStatRow(p: HotHitterUiModel, onClick: () -> Unit): View {
        val hsv = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            
            // Add selectable ripple background
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            
            setOnClickListener { onClick() }
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        hsv.addView(row)

        statChip(row, "AVG",  p.avg,          highlight = true, onClick = onClick)
        statChip(row, "OPS",  p.ops,          highlight = true, onClick = onClick)
        statChip(row, "AB",   p.ab.toString(), onClick = onClick)
        statChip(row, "R",    p.runs.toString(), onClick = onClick)
        statChip(row, "H",    p.hits.toString(), onClick = onClick)
        statChip(row, "2B",   p.doubles.toString(), onClick = onClick)
        statChip(row, "3B",   p.triples.toString(), onClick = onClick)
        statChip(row, "HR",   p.homeRuns.toString(), highlight = p.homeRuns > 0, onClick = onClick)
        statChip(row, "RBI",  p.rbi.toString(), onClick = onClick)
        statChip(row, "BB",   p.bb.toString(), onClick = onClick)
        statChip(row, "SO",   p.so.toString(), onClick = onClick)
        statChip(row, "SB",   p.sb.toString(), onClick = onClick)
        statChip(row, "OBP",  p.obp, onClick = onClick)
        statChip(row, "SLG",  p.slg, onClick = onClick)

        return hsv
    }

    private fun renderHitterGameLog(logs: List<StatSplit>, logRows: LinearLayout) {
        logRows.removeAllViews()

        logs.forEach { split ->
            val entryWrapper = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val infoRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(12), 0, 0)
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

            val homeAwayPrefix = when (split.isHome) {
                true  -> "vs "
                false -> "@ "
                null  -> ""
            }
            val opponentText = TextView(requireContext()).apply {
                text = "$homeAwayPrefix${split.opponent?.name ?: "Opponent"}"
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoRow.addView(opponentText)

            val winLoss = split.isWin?.let { if (it) " W" else " L" } ?: ""
            val resultText = TextView(requireContext()).apply {
                text = winLoss
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(
                    context,
                    if (split.isWin == true) R.color.mlb_green_safe else R.color.mlb_red
                ))
            }
            infoRow.addView(resultText)
            entryWrapper.addView(infoRow)

            val stats = split.stat
            if (stats != null) {
                val hsv = HorizontalScrollView(requireContext()).apply {
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(0, dpToPx(8), 0, dpToPx(12))
                }
                val statsRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                addStatBox(statsRow, "AB",  (stats.atBats ?: 0).toString())
                addStatBox(statsRow, "R",   (stats.runs   ?: 0).toString())
                addStatBox(statsRow, "H",   (stats.hits   ?: 0).toString())
                addStatBox(statsRow, "2B",  (stats.doubles ?: 0).toString())
                addStatBox(statsRow, "3B",  (stats.triples ?: 0).toString())
                addStatBox(statsRow, "HR",  (stats.homeRuns ?: 0).toString())
                addStatBox(statsRow, "RBI", (stats.rbi    ?: 0).toString())
                addStatBox(statsRow, "BB",  (stats.baseOnBalls ?: 0).toString())
                addStatBox(statsRow, "SO",  (stats.strikeOuts ?: 0).toString())
                addStatBox(statsRow, "SB",  (stats.stolenBases ?: 0).toString())
                addStatBox(statsRow, "AVG", stats.avg ?: ".---", widthDp = 44)
                addStatBox(statsRow, "OBP", stats.obp ?: ".---", widthDp = 44)
                addStatBox(statsRow, "SLG", stats.slg ?: ".---", widthDp = 44)
                addStatBox(statsRow, "OPS", stats.ops ?: ".---", widthDp = 50)
                
                hsv.addView(statsRow)
                entryWrapper.addView(hsv)
            }

            logRows.addView(entryWrapper)
            logRows.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1))
                setBackgroundColor(0x15FFFFFF)
            })
        }
    }

    // ── Pitchers ─────────────────────────────────────────────────────────────

    private fun renderPitchers(players: List<HotPitcherUiModel>) {
        binding.containerPitchers.removeAllViews()

        if (players.isEmpty()) {
            if (viewModel.loading.value == false) showEmpty(binding.containerPitchers)
            return
        }

        val inflater = LayoutInflater.from(context)
        players.forEachIndexed { index, player ->
            val item = ItemHotPlayerBinding.inflate(inflater, binding.containerPitchers, false)

            item.tvPlayerName.text = "${index + 1}. ${player.name}"
            item.tvPlayerTeam.text = player.teamName
            item.ivPlayerPhoto.load("https://midfield.mlbstatic.com/v1/people/${player.id}/spots/120") {
                crossfade(true)
                placeholder(R.drawable.ic_baseball_placeholder)
                error(R.drawable.ic_baseball_placeholder)
            }
            item.ivTeamLogo.load(TeamColors.getLogoUrl(player.teamId)) {
                decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
                crossfade(true)
            }

            val toggleLog = {
                val isVisible = item.containerGameLog.visibility == View.VISIBLE
                item.containerGameLog.visibility = if (isVisible) View.GONE else View.VISIBLE
                if (!isVisible) renderPitcherGameLog(player.recentLogs, item.layoutLogRows)
            }

            val statsRow = buildPitcherStatRow(player, toggleLog)
            item.containerStats.addView(statsRow)

            // WHIP badge (highlight metric)
            item.tvHighlightStat.text       = "${player.whip} WHIP"
            item.tvHighlightStat.visibility = View.VISIBLE
            item.tvHighlightStat.setOnClickListener { toggleLog() }

            // Click listener for player name (Details)
            item.tvPlayerName.setOnClickListener {
                val bundle = android.os.Bundle().apply { putInt("playerId", player.id) }
                findNavController().navigate(R.id.action_whosHotFragment_to_playerStatsFragment, bundle)
            }

            // Click listener for card (Expand Log)
            item.root.setOnClickListener { toggleLog() }

            binding.containerPitchers.addView(item.root)
        }
    }

    private fun buildPitcherStatRow(p: HotPitcherUiModel, onClick: () -> Unit): View {
        val hsv = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            
            // Add selectable ripple background
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true

            setOnClickListener { onClick() }
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        hsv.addView(row)

        statChip(row, "IP",   p.ip,                    highlight = true, onClick = onClick)
        statChip(row, "WHIP", p.whip,                  highlight = true, onClick = onClick)
        statChip(row, "H",    p.hits.toString(), onClick = onClick)
        statChip(row, "R",    p.runs.toString(), onClick = onClick)
        statChip(row, "ER",   p.earnedRuns.toString(), onClick = onClick)
        statChip(row, "HR",   p.homeRuns.toString(),    highlight = p.homeRuns > 0, onClick = onClick)
        statChip(row, "HB",   p.hitBatters.toString(), onClick = onClick)
        statChip(row, "BB",   p.walks.toString(), onClick = onClick)
        statChip(row, "IBB",  p.intentionalWalks.toString(), onClick = onClick)
        statChip(row, "SO",   p.strikeouts.toString(), onClick = onClick)
        statChip(row, "AVG",  p.avg, onClick = onClick)
        statChip(row, "ERA",  p.era, onClick = onClick)

        return hsv
    }

    private fun renderPitcherGameLog(logs: List<StatSplit>, logRows: LinearLayout) {
        logRows.removeAllViews()

        logs.forEach { split ->
            val entryWrapper = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val infoRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(12), 0, 0)
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
                    if (split.isWin == true) R.color.mlb_green_safe else R.color.mlb_red
                ))
            }
            infoRow.addView(resultText)
            entryWrapper.addView(infoRow)

            val stats = split.stat
            if (stats != null) {
                val hsv = HorizontalScrollView(requireContext()).apply {
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(0, dpToPx(8), 0, dpToPx(12))
                }
                val statsRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                addStatBox(statsRow, "IP",  stats.inningsPitched ?: "0.0")
                addStatBox(statsRow, "H",   (stats.pitchingHits ?: stats.hits ?: 0).toString())
                addStatBox(statsRow, "R",   (stats.pitchingRuns ?: stats.runs ?: 0).toString())
                addStatBox(statsRow, "ER",  (stats.earnedRuns ?: 0).toString())
                addStatBox(statsRow, "HR",  (stats.pitchingHomeRuns ?: stats.homeRuns ?: 0).toString())
                addStatBox(statsRow, "HB",  (stats.hitBatters ?: 0).toString())
                addStatBox(statsRow, "BB",  (stats.baseOnBalls ?: 0).toString())
                addStatBox(statsRow, "IBB", (stats.intentionalWalks ?: 0).toString(), widthDp = 40)
                addStatBox(statsRow, "SO",  (stats.strikeOuts ?: 0).toString())
                addStatBox(statsRow, "AVG", stats.avg ?: ".---", widthDp = 48)
                addStatBox(statsRow, "WHIP", stats.whip ?: "-.--", widthDp = 48)
                
                val decision = when {
                    stats.wins == 1 -> "W"
                    stats.losses == 1 -> "L"
                    stats.saves == 1 -> "S"
                    stats.holds == 1 -> "H"
                    else -> null
                }
                if (decision != null) addStatBox(statsRow, "DEC", decision)

                hsv.addView(statsRow)
                entryWrapper.addView(hsv)
            }

            logRows.addView(entryWrapper)
            logRows.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1))
                setBackgroundColor(0x15FFFFFF)
            })
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun statChip(parent: LinearLayout, label: String, value: String, highlight: Boolean = false, onClick: (() -> Unit)? = null) {
        val dp = resources.displayMetrics.density

        val chip = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = (10 * dp).toInt() }

            if (onClick != null) {
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
                // Add padding to make the clickable area larger and the ripple visible
                setPadding((4 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
            }
        }

        val valueView = TextView(requireContext()).apply {
            text      = value
            textSize  = 14f
            gravity   = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(
                requireContext(),
                if (highlight) R.color.live_green else R.color.text_primary
            ))
        }

        val labelView = TextView(requireContext()).apply {
            text      = label
            textSize  = 10f
            gravity   = Gravity.CENTER
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }

        chip.addView(valueView)
        chip.addView(labelView)
        parent.addView(chip)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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
            setTypeface(null, Typeface.BOLD)
        })
        
        box.addView(TextView(requireContext()).apply {
            text = value
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        })
        
        container.addView(box)
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

    private fun showEmpty(container: ViewGroup) {
        val tv = TextView(context).apply {
            text      = "No hot players found for the last 3 games."
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            gravity   = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }
        container.addView(tv)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
