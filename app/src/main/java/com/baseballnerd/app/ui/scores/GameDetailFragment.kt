package com.baseballnerd.app.ui.scores

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import coil.decode.SvgDecoder
import coil.load
import com.baseballnerd.app.R
import com.baseballnerd.app.util.TeamAbbr
import com.baseballnerd.app.data.model.*
import com.baseballnerd.app.data.repository.GamesRepository
import com.baseballnerd.app.databinding.FragmentGameDetailBinding
import com.baseballnerd.app.util.TeamColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class GameDetailFragment : Fragment() {

    private var _binding: FragmentGameDetailBinding? = null
    private val binding get() = _binding!!

    private val gamePk: Long by lazy { arguments?.getLong("gamePk") ?: 0L }

    private val gameDate: String by lazy {
        arguments?.getString("date")?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private val viewModel: GameDetailViewModel by viewModels {
        GameDetailViewModelFactory(gamePk, gameDate, GamesRepository())
    }

    private var showingLineups = true
    private var showingScoringOnly = false

    // Store expanded play indices to persist across data refreshes
    private val expandedPlays = mutableSetOf<Int>()

    // For keeping pitches on strikezone until next at bat
    private var lastAtBatIndex: Int = -1
    private var lastAtBatPitches: List<PitchDetail> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGameDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnLineups.setOnClickListener { switchTab(showLineups = true) }
        binding.btnPlays.setOnClickListener { switchTab(showLineups = false) }

        binding.btnPlaysAll.setOnClickListener { switchPlaysFilter(scoringOnly = false) }
        binding.btnPlaysScoring.setOnClickListener { switchPlaysFilter(scoringOnly = true) }

        binding.layoutBullpenHeader.setOnClickListener {
            val isVisible = binding.layoutBullpenContent.visibility == View.VISIBLE
            binding.layoutBullpenContent.visibility = if (isVisible) View.GONE else View.VISIBLE
            binding.ivBullpenExpand.rotation = if (isVisible) 0f else 180f
        }

        binding.layoutPitchTypesHeader.setOnClickListener {
            val isVisible = binding.layoutPitchTypesContent.visibility == View.VISIBLE
            binding.layoutPitchTypesContent.visibility = if (isVisible) View.GONE else View.VISIBLE
            binding.ivPitchTypesExpand.rotation = if (isVisible) 0f else 180f
        }

        viewModel.gameData.observe(viewLifecycleOwner) { game ->
            if (game != null) bindGameData(game)
        }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading && viewModel.gameData.value == null) {
                binding.cardBoxScore.visibility = View.GONE
                binding.cardGameStatus.visibility = View.GONE
                binding.cardPlayerInfo.visibility = View.GONE
                binding.layoutStrikeZone.visibility = View.GONE
                binding.cardLineups.visibility = View.GONE
                binding.layoutProbables.visibility = View.GONE
                binding.layoutPreviewLineups.visibility = View.GONE
                binding.cardBullpenUsage.visibility = View.GONE
                binding.cardPitchTypes.visibility = View.GONE
                binding.cardGameInfo.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startPolling()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPolling()
    }

    private fun navigateToTeamSchedule(teamId: Int, teamName: String) {
        val action = GameDetailFragmentDirections.actionGameDetailFragmentToTeamScheduleFragment(teamId, teamName)
        findNavController().navigate(action)
    }

    private fun navigateToPlayerStats(playerId: Int) {
        val action = GameDetailFragmentDirections.actionGameDetailFragmentToPlayerStatsFragment(playerId)
        findNavController().navigate(action)
    }

    private fun switchTab(showLineups: Boolean) {
        showingLineups = showLineups
        binding.scrollLineups.visibility = if (showLineups) View.VISIBLE else View.GONE
        binding.layoutPlaysContent.visibility = if (showLineups) View.GONE else View.VISIBLE

        val activeText = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val inactiveText = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        binding.btnLineups.setBackgroundResource(if (showLineups) R.drawable.bg_tab_selected else 0)
        binding.btnLineups.setTextColor(if (showLineups) activeText else inactiveText)

        binding.btnPlays.setBackgroundResource(if (!showLineups) R.drawable.bg_tab_selected else 0)
        binding.btnPlays.setTextColor(if (!showLineups) activeText else inactiveText)
    }

    private fun switchPlaysFilter(scoringOnly: Boolean) {
        showingScoringOnly = scoringOnly
        val activeText   = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val inactiveText = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        binding.btnPlaysAll.setBackgroundResource(if (scoringOnly) 0 else R.drawable.bg_tab_selected)
        binding.btnPlaysAll.setTextColor(if (!scoringOnly) activeText else inactiveText)
        binding.btnPlaysScoring.setBackgroundResource(if (scoringOnly) R.drawable.bg_tab_selected else 0)
        binding.btnPlaysScoring.setTextColor(if (scoringOnly) activeText else inactiveText)
        // Re-render plays with the new filter using the last known game
        viewModel.gameData.value?.let { renderPlays(it) }
    }

    private fun bindGameData(game: GameCardModel) {
        val isPostponed = game.detailedState.equals("Postponed", ignoreCase = true)

        binding.tvAwayScore.text = if (isPostponed) "0" else game.awayScore.toString()
        binding.tvHomeScore.text = if (isPostponed) "0" else game.homeScore.toString()

        binding.tvAwayRecord.text = game.awayRecord ?: ""
        binding.tvHomeRecord.text = game.homeRecord ?: ""

        // Team logo clicks
        binding.layoutAwayLogo.setOnClickListener { navigateToTeamSchedule(game.awayTeamId, game.awayTeamName) }
        binding.layoutHomeLogo.setOnClickListener { navigateToTeamSchedule(game.homeTeamId, game.homeTeamName) }

        binding.ivAwayLogo.load(TeamColors.getLogoUrl(game.awayTeamId)) {
            decoderFactory(SvgDecoder.Factory())
        }
        binding.ivHomeLogo.load(TeamColors.getLogoUrl(game.homeTeamId)) {
            decoderFactory(SvgDecoder.Factory())
        }

        if (!isPostponed && game.linescore != null && (game.gameState == "Live" || game.gameState == "Final")) {
            binding.cardBoxScore.visibility = View.VISIBLE
            renderLinescore(game, game.linescore!!)
        } else {
            binding.cardBoxScore.visibility = View.GONE
        }

        binding.tvInning.text = if (isPostponed) "Postponed" else game.inningOrdinal

        if (game.gameState == "Live") {
            binding.cardGameStatus.visibility = View.VISIBLE
            binding.cardPlayerInfo.visibility = View.VISIBLE
            binding.baseView.visibility = View.VISIBLE
            binding.layoutCountOuts.visibility = View.VISIBLE
            binding.layoutStrikeZone.visibility = View.VISIBLE
            binding.layoutProbables.visibility = View.GONE
            binding.layoutPreviewLineups.visibility = View.GONE
            binding.cardGameInfo.visibility = View.VISIBLE
            renderGameInfo(game)

            binding.baseView.setRunners(game.runnerOnFirst, game.runnerOnSecond, game.runnerOnThird)
            binding.tvCount.text = "${game.balls} - ${game.strikes}"
            updateOuts(game.outs)

            game.currentPitcher?.let { p ->
                val text = "P: ${p.name} (${p.ip} IP, ${p.er} ER, ${p.k} K)"
                val ss = SpannableStringBuilder(text)
                ss.setSpan(StyleSpan(Typeface.BOLD), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                binding.tvPitcherInfo.text = ss
                binding.tvPitcherInfo.setOnClickListener { p.id?.let { navigateToPlayerStats(it) } }
            }
            game.currentBatter?.let { b ->
                val text = "AB: ${b.name} (${b.hits}-${b.atBats})"
                val ss = SpannableStringBuilder(text)
                ss.setSpan(StyleSpan(Typeface.BOLD), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                binding.tvBatterInfo.text = ss
                binding.tvBatterInfo.setOnClickListener { b.id?.let { navigateToPlayerStats(it) } }
            }

            // Update Strike Zone: Keep pitches until next at bat
            val currentAtBat = game.plays.lastOrNull { it.halfInning == (if (game.inningOrdinal.contains("Top")) "top" else "bottom") }
            val currentIdx = currentAtBat?.atBatIndex ?: -1

            if (game.currentAtBatPitches.isNotEmpty()) {
                lastAtBatIndex = currentIdx
                lastAtBatPitches = game.currentAtBatPitches
            }

            val pitchesToShow = if (game.currentAtBatPitches.isNotEmpty()) {
                game.currentAtBatPitches
            } else {
                lastAtBatPitches
            }

            binding.strikeZoneView.setPitches(pitchesToShow)
            renderCurrentPitches(pitchesToShow)

        } else if (isPostponed) {
            // Postponed — hide all live/preview data and game info
            binding.cardPlayerInfo.visibility = View.GONE
            binding.baseView.visibility = View.GONE
            binding.layoutCountOuts.visibility = View.GONE
            binding.cardGameStatus.visibility = View.GONE
            binding.layoutStrikeZone.visibility = View.GONE
            binding.layoutProbables.visibility = View.GONE
            binding.layoutPreviewLineups.visibility = View.GONE
            binding.cardGameInfo.visibility = View.GONE

        } else if (game.gameState != "Final") {
            // Preview, Scheduled, Pre-Game, etc.
            binding.cardPlayerInfo.visibility = View.GONE
            binding.baseView.visibility = View.GONE
            binding.layoutCountOuts.visibility = View.GONE
            binding.cardGameStatus.visibility = View.GONE
            binding.layoutStrikeZone.visibility = View.GONE
            binding.cardGameInfo.visibility = View.VISIBLE
            renderGameInfo(game)

            // Show probables
            binding.layoutProbables.visibility = View.VISIBLE

            game.awayStarter?.let {
                binding.tvAwayStarterName.text = it.name
                val header = "(#${it.number ?: ""} ${it.hand ?: ""}HP)"
                val stats = "$header\n${it.record ?: "0-0"}, ${it.era ?: "-.--"} ERA, ${it.strikeouts ?: 0} K"
                binding.tvAwayStarterStats.text = stats
                binding.ivAwayStarter.load("https://midfield.mlbstatic.com/v1/people/${it.id}/spots/120") {
                    crossfade(true)
                    placeholder(R.drawable.ic_baseball_placeholder)
                    error(R.drawable.ic_baseball_placeholder)
                }
                binding.layoutAwayStarter.setOnClickListener { _ -> navigateToPlayerStats(it.id) }
            } ?: run {
                binding.tvAwayStarterName.text = "TBD"
                binding.tvAwayStarterStats.text = ""
                binding.ivAwayStarter.setImageResource(R.drawable.ic_baseball_placeholder)
                binding.layoutAwayStarter.setOnClickListener(null)
            }

            game.homeStarter?.let {
                binding.tvHomeStarterName.text = it.name
                val header = "(#${it.number ?: ""} ${it.hand ?: ""}HP)"
                val stats = "$header\n${it.record ?: "0-0"}, ${it.era ?: "-.--"} ERA, ${it.strikeouts ?: 0} K"
                binding.tvHomeStarterStats.text = stats
                binding.ivHomeStarter.load("https://midfield.mlbstatic.com/v1/people/${it.id}/spots/120") {
                    crossfade(true)
                    placeholder(R.drawable.ic_baseball_placeholder)
                    error(R.drawable.ic_baseball_placeholder)
                }
                binding.layoutHomeStarter.setOnClickListener { _ -> navigateToPlayerStats(it.id) }
            } ?: run {
                binding.tvHomeStarterName.text = "TBD"
                binding.tvHomeStarterStats.text = ""
                binding.ivHomeStarter.setImageResource(R.drawable.ic_baseball_placeholder)
                binding.layoutHomeStarter.setOnClickListener(null)
            }

            // Preview lineups
            val hasAwayLineup = game.awayLineup != null && game.awayLineup.batters.isNotEmpty()
            val hasHomeLineup = game.homeLineup != null && game.homeLineup.batters.isNotEmpty()
            if (hasAwayLineup || hasHomeLineup) {
                binding.layoutPreviewLineups.visibility = View.VISIBLE
                game.awayLineup?.let { renderPreviewLineup(it, binding.tableAwayLineupPreview) }
                game.homeLineup?.let { renderPreviewLineup(it, binding.tableHomeLineupPreview) }
            } else {
                binding.layoutPreviewLineups.visibility = View.GONE
            }
        } else {
            // Final
            binding.cardPlayerInfo.visibility = View.GONE
            binding.baseView.visibility = View.GONE
            binding.layoutCountOuts.visibility = View.GONE
            binding.cardGameStatus.visibility = View.GONE
            binding.layoutStrikeZone.visibility = View.GONE
            binding.layoutProbables.visibility = View.GONE
            binding.layoutPreviewLineups.visibility = View.GONE

            // Show Game Info for Final games
            binding.cardGameInfo.visibility = View.VISIBLE
            renderGameInfo(game)
        }

        // Update Badges (Playoff, Game Type, No-Hitter, Perfect Game)
        updateBadges(game)

        val hasAwayReported = !isPostponed && game.awayLineup != null && (game.awayLineup.batters.isNotEmpty() || game.awayLineup.pitchers.isNotEmpty())
        val hasHomeReported = !isPostponed && game.homeLineup != null && (game.homeLineup.batters.isNotEmpty() || game.homeLineup.pitchers.isNotEmpty())
        val hasLineups = hasAwayReported || hasHomeReported
        val hasPlays = !isPostponed && game.plays.isNotEmpty()

        if (hasLineups || hasPlays) {
            binding.cardLineups.visibility = View.VISIBLE

            if (hasLineups) {
                // Away Section Visibility
                binding.tvAwayLineupTeam.visibility = if (hasAwayReported) View.VISIBLE else View.GONE
                binding.scrollAwayLineup.visibility = if (hasAwayReported && game.awayLineup?.batters?.isNotEmpty() == true) View.VISIBLE else View.GONE
                binding.scrollAwayPitchers.visibility = if (hasAwayReported && game.awayLineup?.pitchers?.isNotEmpty() == true) View.VISIBLE else View.GONE
                if (hasAwayReported) {
                    game.awayLineup?.let {
                        renderLineup(it, binding.tableAwayLineup)
                        renderPitchers(it, binding.tableAwayPitchers)
                    }
                    binding.tvAwayLineupTeam.text = game.awayTeamName.uppercase()
                }

                // Home Section Visibility
                binding.tvHomeLineupTeam.visibility = if (hasHomeReported) View.VISIBLE else View.GONE
                binding.scrollHomeLineup.visibility = if (hasHomeReported && game.homeLineup?.batters?.isNotEmpty() == true) View.VISIBLE else View.GONE
                binding.scrollHomePitchers.visibility = if (hasHomeReported && game.homeLineup?.pitchers?.isNotEmpty() == true) View.VISIBLE else View.GONE
                if (hasHomeReported) {
                    game.homeLineup?.let {
                        renderLineup(it, binding.tableHomeLineup)
                        renderPitchers(it, binding.tableHomePitchers)
                    }
                    binding.tvHomeLineupTeam.text = game.homeTeamName.uppercase()
                }

                binding.dividerLineups.visibility = if (hasAwayReported && hasHomeReported) View.VISIBLE else View.GONE
            }

            if (hasPlays) renderPlays(game)

            binding.btnPlays.visibility = if (hasPlays) View.VISIBLE else View.GONE
            binding.btnLineups.visibility = if (hasLineups) View.VISIBLE else View.GONE

            switchTab(showingLineups && hasLineups)
        } else {
            binding.cardLineups.visibility = View.GONE
        }

        // Bullpen Usage Section — only show for the current season
        val gameYear = gameDate.take(4).toIntOrNull() ?: 0
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        if (!isPostponed && gameYear >= currentYear && (game.awayBullpenUsage.isNotEmpty() || game.homeBullpenUsage.isNotEmpty())) {
            binding.cardBullpenUsage.visibility = View.VISIBLE
            renderBullpenUsageSection(game)
        } else {
            binding.cardBullpenUsage.visibility = View.GONE
        }

        // Pitch Types Section
        if (!isPostponed && game.plays.isNotEmpty() && (game.gameState == "Live" || game.gameState == "Final")) {
            binding.cardPitchTypes.visibility = View.VISIBLE
            renderPitchTypeSection(game)
        } else {
            binding.cardPitchTypes.visibility = View.GONE
        }
    }

    private fun updateBadges(game: GameCardModel) {
        var hasBadge = false

        // Series Badge (Postseason)
        val isPostseason = game.gameType in setOf("W", "D", "L", "F", "C")
        val seriesLabel = if (isPostseason && !game.seriesDescription.isNullOrBlank()) {
            val gameNum = game.seriesGameNumber
            if (gameNum != null) "${game.seriesDescription} Game $gameNum".uppercase()
            else game.seriesDescription.uppercase()
        } else null

        if (seriesLabel != null) {
            binding.tvPlayoffBadge.text = seriesLabel
            binding.tvPlayoffBadge.visibility = View.VISIBLE
            hasBadge = true
        } else {
            binding.tvPlayoffBadge.visibility = View.GONE
        }

        // Game Type Badge
        val gameTypeLabel = when (game.gameType) {
            "S" -> "SPRING TRAINING"
            "E" -> "PRESEASON"
            else -> null
        }
        if (gameTypeLabel != null) {
            binding.tvGameTypeBadge.text = gameTypeLabel
            binding.tvGameTypeBadge.visibility = View.VISIBLE
            hasBadge = true
        } else {
            binding.tvGameTypeBadge.visibility = View.GONE
        }

        // No Hitter / Perfect Game Badges
        val isPostponed = game.detailedState.equals("Postponed", ignoreCase = true)
        val isCancelled = game.detailedState.equals("Cancelled", ignoreCase = true)
        val isDelayed = game.detailedState.contains("Delayed", ignoreCase = true)
        val isHiddenState = isPostponed || isCancelled || isDelayed

        val isNoHitter = (game.awayNoHitter || game.homeNoHitter) && !isHiddenState
        val isPerfectGame = (game.awayPerfectGame || game.homePerfectGame) && !isHiddenState

        binding.tvNoHitterBadge.visibility = if (isNoHitter && !isPerfectGame) View.VISIBLE else View.GONE
        binding.tvPerfectGameBadge.visibility = if (isPerfectGame) View.VISIBLE else View.GONE

        if (isNoHitter || isPerfectGame) hasBadge = true

        binding.layoutBadges.visibility = if (hasBadge) View.VISIBLE else View.GONE
    }

    private fun renderBullpenUsageSection(game: GameCardModel) {
        val container = binding.containerBullpenStats
        container.removeAllViews()

        fun renderTeamBullpen(teamName: String, bullpen: List<BullpenUsageModel>) {
            if (bullpen.isEmpty()) return

            // ── Team header ───────────────────────────────────────────────────
            container.addView(TextView(requireContext()).apply {
                text = teamName.uppercase()
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 16, 0, 4)
                setSingleLine(true)
            })

            // ── Date-column header row (shown once per team) ──────────────────
            val dates = bullpen.firstOrNull()?.usage?.map { it.date } ?: emptyList()
            if (dates.isNotEmpty()) {
                val headerRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, 2)
                }
                // Spacer matching the name column weight
                headerRow.addView(android.view.View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 2f)
                })
                dates.forEach { date ->
                    headerRow.addView(TextView(requireContext()).apply {
                        text = date
                        textSize = 9f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        layoutParams = LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setSingleLine(true)
                    })
                }
                container.addView(headerRow)
            }

            // ── One row per bullpen pitcher ───────────────────────────────────
            bullpen.forEach { usage ->
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 6, 0, 6)
                    isClickable = true
                    isFocusable = true
                    background = with(android.util.TypedValue()) {
                        context.theme.resolveAttribute(
                            android.R.attr.selectableItemBackground, this, true)
                        ContextCompat.getDrawable(context, resourceId)
                    }
                    setOnClickListener { navigateToPlayerStats(usage.pitcherId) }
                }

                // Pitcher name — weight 2 so it gets most of the row
                row.addView(TextView(requireContext()).apply {
                    text = usage.pitcherName
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    layoutParams = LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                })

                // Bold pitch count for each of the last 3 days — weight 1 each
                usage.usage.forEach { day ->
                    row.addView(TextView(requireContext()).apply {
                        text = if (day.count > 0) day.count.toString() else "—"
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        gravity = android.view.Gravity.CENTER
                        setTextColor(
                            if (day.count > 0)
                                ContextCompat.getColor(context, R.color.text_primary)
                            else
                                ContextCompat.getColor(context, R.color.text_secondary)
                        )
                        layoutParams = LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setSingleLine(true)
                    })
                }

                container.addView(row)
            }
        }

        renderTeamBullpen(game.awayTeamName, game.awayBullpenUsage)
        renderTeamBullpen(game.homeTeamName, game.homeBullpenUsage)
    }

    private fun renderLinescore(game: GameCardModel, model: LinescoreModel) {
        val table = binding.tableLinescore
        table.removeAllViews()

        val ctx = requireContext()
        val headerRow = TableRow(ctx)
        val awayRow = TableRow(ctx)
        val homeRow = TableRow(ctx)

        val numInnings = model.innings.size
        // Use weight-based layout if 9 innings or fewer, otherwise fixed widths
        val useFixed = numInnings > 9

        fun addLinescoreCell(row: TableRow, text: String, isHeader: Boolean = false, isBold: Boolean = false, widthDp: Int = 0, weight: Float = 0f) {
            val tv = TextView(ctx).apply {
                this.text = text
                textSize = if (isHeader) 11f else 13f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                if (isBold || isHeader) setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(10), 0, dpToPx(10))
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val params = TableRow.LayoutParams(if (widthDp > 0) dpToPx(widthDp) else 0, TableRow.LayoutParams.WRAP_CONTENT)
            if (weight > 0f) params.weight = weight
            tv.layoutParams = params
            row.addView(tv)
        }

        // Labels Column
        addLinescoreCell(headerRow, "", isHeader = true, widthDp = if (useFixed) 44 else 0, weight = if (useFixed) 0f else 1.2f)
        addLinescoreCell(awayRow, TeamAbbr.fromId(game.awayTeamId), isBold = true, widthDp = if (useFixed) 44 else 0, weight = if (useFixed) 0f else 1.2f)
        addLinescoreCell(homeRow, TeamAbbr.fromId(game.homeTeamId), isBold = true, widthDp = if (useFixed) 44 else 0, weight = if (useFixed) 0f else 1.2f)

        // Innings
        model.innings.forEach { inning ->
            addLinescoreCell(headerRow, inning.num.toString(), isHeader = true, widthDp = if (useFixed) 30 else 0, weight = if (useFixed) 0f else 1f)
            addLinescoreCell(awayRow, inning.awayRuns, widthDp = if (useFixed) 30 else 0, weight = if (useFixed) 0f else 1f)
            addLinescoreCell(homeRow, inning.homeRuns, widthDp = if (useFixed) 30 else 0, weight = if (useFixed) 0f else 1f)
        }

        // Totals: R, H, E
        val totalWeight = if (useFixed) 0f else 1.1f
        addLinescoreCell(headerRow, "R", isHeader = true, widthDp = if (useFixed) 34 else 0, weight = totalWeight)
        addLinescoreCell(awayRow, model.awayTotal.runs.toString(), isBold = true, widthDp = if (useFixed) 34 else 0, weight = totalWeight)
        addLinescoreCell(homeRow, model.homeTotal.runs.toString(), isBold = true, widthDp = if (useFixed) 34 else 0, weight = totalWeight)

        addLinescoreCell(headerRow, "H", isHeader = true, widthDp = if (useFixed) 34 else 0, weight = totalWeight)
        addLinescoreCell(awayRow, model.awayTotal.hits.toString(), widthDp = if (useFixed) 34 else 0, weight = totalWeight)
        addLinescoreCell(homeRow, model.homeTotal.hits.toString(), widthDp = if (useFixed) 34 else 0, weight = totalWeight)

        addLinescoreCell(headerRow, "E", isHeader = true, widthDp = if (useFixed) 34 else 0, weight = totalWeight)
        addLinescoreCell(awayRow, model.awayTotal.errors.toString(), widthDp = if (useFixed) 34 else 0, weight = totalWeight)
        addLinescoreCell(homeRow, model.homeTotal.errors.toString(), widthDp = if (useFixed) 34 else 0, weight = totalWeight)

        table.addView(headerRow)
        table.addView(awayRow)
        table.addView(homeRow)

        // Ensure table stretches to fill parent width when not in fixed-width mode
        table.isStretchAllColumns = !useFixed
    }

    private fun renderGameInfo(game: GameCardModel) {
        val grid = binding.gridGameInfo
        grid.removeAllViews()

        val infoItems = mutableListOf<Pair<String, String?>>()
        infoItems.add("Venue" to game.venueName?.removeSuffix("."))

        // Format scheduled start time from ISO gameDate (e.g. "2024-04-01T18:10:00Z")
        val scheduledTime: String? = try {
            val isoFmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val localFmt = java.text.SimpleDateFormat("h:mm a z", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getDefault()
            }
            if (game.isStartTimeTBD) "TBD"
            else localFmt.format(isoFmt.parse(game.gameDate)!!)
        } catch (e: Exception) { null }

        if (scheduledTime != null) infoItems.add("Start Time" to scheduledTime)
        infoItems.add("Weather" to game.weather?.removeSuffix("."))
        infoItems.add("Wind" to game.wind?.removeSuffix("."))

        // TV / Radio Broadcasts
        val tvHome = game.broadcasts?.filter { it.type == "TV" && it.homeAway == "home" }?.mapNotNull { it.name }?.distinct()
        val tvAway = game.broadcasts?.filter { it.type == "TV" && it.homeAway == "away" }?.mapNotNull { it.name }?.distinct()

        val tvHomeStr = tvHome?.joinToString(", ") ?: ""
        val tvAwayStr = tvAway?.joinToString(", ") ?: ""

        if (tvHomeStr.isNotEmpty() && tvAwayStr.isNotEmpty() && tvHomeStr == tvAwayStr) {
            infoItems.add("TV" to tvHomeStr)
        } else {
            if (tvHomeStr.isNotEmpty()) {
                infoItems.add("TV Home" to tvHomeStr)
            }
            if (tvAwayStr.isNotEmpty()) {
                infoItems.add("TV Away" to tvAwayStr)
            }
        }

        val tvNational = game.broadcasts?.filter { it.type == "TV" && it.homeAway != "home" && it.homeAway != "away" }?.mapNotNull { it.name }?.distinct()
        if (!tvNational.isNullOrEmpty()) {
            infoItems.add("TV" to tvNational.joinToString(", "))
        }

        val radioBroadcasts = game.broadcasts?.filter { it.type == "Radio" }?.mapNotNull { it.name }?.distinct()
        if (!radioBroadcasts.isNullOrEmpty()) {
            infoItems.add("Radio" to radioBroadcasts.joinToString(", "))
        }

        val firstPitchTime = game.firstPitch?.removeSuffix(".")
        val venueTz = when {
            !game.venueTimeZoneAbbrev.isNullOrBlank() -> game.venueTimeZoneAbbrev // e.g. CDT
            !game.venueTimeZone.isNullOrBlank() -> game.venueTimeZone // e.g. America/Chicago
            else -> null
        }

        val firstPitchWithTz = when {
            firstPitchTime != null && venueTz != null -> "$firstPitchTime ($venueTz)"
            firstPitchTime != null -> firstPitchTime
            else -> null
        }

        infoItems.add("First Pitch (Local Time)" to firstPitchWithTz)

        infoItems.add("Game Time" to game.gameDuration?.removeSuffix("."))
        infoItems.add("Attendance" to game.attendance?.removeSuffix("."))

        infoItems.filter { it.second != null }.forEach { (label, value) ->
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 16, 8)
                val params = android.widget.GridLayout.LayoutParams()
                params.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                layoutParams = params
            }

            layout.addView(TextView(requireContext()).apply {
                text = label.uppercase()
                textSize = 10f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            layout.addView(TextView(requireContext()).apply {
                text = value
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setPadding(0, 2, 0, 0)
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            grid.addView(layout)
        }
    }

    private fun renderCurrentPitches(pitches: List<PitchDetail>) {
        val container = binding.containerCurrentPitches
        container.removeAllViews()

        pitches.forEach { pitch ->
            container.addView(TextView(requireContext()).apply {
                val outcome = pitch.callDescription
                val info = "${pitch.pitchNumber}. ${pitch.pitchType} - ${pitch.speed} (${outcome})"
                text = info
                textSize = 10f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, 2, 0, 2)
                isSingleLine = false
                ellipsize = null
            })
        }
    }

    private fun renderPitchTypeSection(game: GameCardModel) {
        val container = binding.containerPitcherTypes
        container.removeAllViews()

        // 1. Collect all pitches across all plays, attributed to a pitcher
        // Pitcher is on the defensive team
        val allPitches = game.plays.flatMap { play ->
            val teamId = if (play.halfInning.equals("top", ignoreCase = true)) game.homeTeamId else game.awayTeamId
            play.pitches.map { pitch ->
                teamId to (play.pitcherName to pitch)
            }
        }

        // 2. Group by Team first
        val groupedByTeam = allPitches.groupBy { it.first }

        var totalPitchersRendered = 0

        // Helper to render a team's section
        fun renderTeamPitchers(teamId: Int, teamName: String) {
            val teamPitches = groupedByTeam[teamId] ?: return
            val groupedByPitcher = teamPitches.map { it.second }.groupBy { it.first }

            // Filter out pitchers whose pitch types are ALL unknown
            val validPitchers = groupedByPitcher.filter { (_, pairs) ->
                pairs.any { it.second.pitchType != "Unknown" }
            }

            if (validPitchers.isEmpty()) return

            // Team Header
            container.addView(TextView(requireContext()).apply {
                text = teamName.uppercase()
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 16, 0, 8)
                setSingleLine(true)
            })

            validPitchers.forEach { (pitcherName, pairs) ->
                totalPitchersRendered++
                val pitches = pairs.map { it.second }
                val pitcherLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 8, 0, 16)
                }

                pitcherLayout.addView(TextView(requireContext()).apply {
                    text = pitcherName?.uppercase() ?: "UNKNOWN"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.mlb_red))
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, 4)
                })

                val pitchTypeGroups = pitches.groupBy { it.pitchType }
                val totalThrown = pitches.size.toDouble()

                // Sort pitch types by count descending
                val sortedPitchTypes = pitchTypeGroups.toList().sortedByDescending { it.second.size }

                sortedPitchTypes.forEach { (type, typePitches) ->
                    val count = typePitches.size
                    val percent = (count / totalThrown * 100).toInt()

                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 4, 0, 4)
                    }

                    row.addView(TextView(requireContext()).apply {
                        text = type
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                        textSize = 13f
                        setSingleLine(true)
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })

                    row.addView(TextView(requireContext()).apply {
                        text = "$count ($percent%)"
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setSingleLine(true)
                    })

                    pitcherLayout.addView(row)
                }
                container.addView(pitcherLayout)
            }
        }

        // Render Away pitchers then Home pitchers
        renderTeamPitchers(game.awayTeamId, game.awayTeamName)
        renderTeamPitchers(game.homeTeamId, game.homeTeamName)

        // If no pitchers were actually rendered, hide the entire card
        binding.cardPitchTypes.visibility = if (totalPitchersRendered > 0) View.VISIBLE else View.GONE
    }

    private fun renderPlays(game: GameCardModel) {
        val allPlays = game.plays.reversed()
        val plays = if (showingScoringOnly) allPlays.filter { it.isScoringPlay } else allPlays
        val container = binding.containerPlays
        container.removeAllViews()

        if (plays.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = if (showingScoringOnly) "No scoring plays yet." else "No plays available yet."
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, 8, 0, 8)
            }
            container.addView(tv)
            return
        }

        var lastInning = -1
        var lastHalf = ""

        plays.forEach { play ->
            val ctx = requireContext()

            // Inning or Half-Inning Divider
            if (lastInning != -1 && (play.inning != lastInning || play.halfInning != lastHalf)) {
                container.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1.5f * resources.displayMetrics.density).toInt()
                    )
                    setBackgroundColor(ContextCompat.getColor(ctx, R.color.surface_elevated))
                })
            }

            val battingTeamId = if (play.halfInning == "top") game.awayTeamId else game.homeTeamId
            container.addView(buildPlayRow(play, battingTeamId, game))

            lastInning = play.inning
            lastHalf = play.halfInning
        }
    }

    private fun buildPlayRow(play: PlaySummary, battingTeamId: Int, game: GameCardModel): View {
        val ctx = requireContext()
        val primaryColor = ContextCompat.getColor(ctx, R.color.text_primary)
        val secondaryColor = ContextCompat.getColor(ctx, R.color.text_secondary)
        val dividerColor = ContextCompat.getColor(ctx, R.color.surface_elevated)
        val hasPitches = play.pitches.isNotEmpty()

        val wrapper = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        // ── Header row (always visible, tappable when pitches exist) ────────
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 14, 0, 14)
            gravity = Gravity.CENTER_VERTICAL
            if (hasPitches) {
                isClickable = true
                isFocusable = true
                val tv = android.util.TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                background = ContextCompat.getDrawable(ctx, tv.resourceId)
            }
        }

        val halfLabel = if (play.halfInning == "top") "Top" else "Bot"
        val pitchCountLabel = if (hasPitches) " · ${play.pitches.size}p" else ""

        // Batting Team Logo in a white circle
        val logoContainerSize = (28 * ctx.resources.displayMetrics.density).toInt()
        val logoViewSize = (20 * ctx.resources.displayMetrics.density).toInt()

        val logoContainer = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(logoContainerSize, logoContainerSize).apply {
                marginEnd = (12 * ctx.resources.displayMetrics.density).toInt()
            }
            background = ContextCompat.getDrawable(ctx, R.drawable.shape_team_circle)
            backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.white)
        }

        val logoView = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(logoViewSize, logoViewSize).apply {
                gravity = Gravity.CENTER
            }
            load(TeamColors.getLogoUrl(battingTeamId)) {
                decoderFactory(SvgDecoder.Factory())
            }
        }
        logoContainer.addView(logoView)
        headerRow.addView(logoContainer)

        // Inning Indicator
        headerRow.addView(TextView(ctx).apply {
            text = "$halfLabel ${play.inning}"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(secondaryColor)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (14 * ctx.resources.displayMetrics.density).toInt()
            }
            setSingleLine(true)
        })

        // Left: batter + outcome
        val leftBlock = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val batterNameRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val scoreSuffix = if (play.isScoringPlay) {
            val awayAbbr = TeamAbbr.fromId(game.awayTeamId)
            val homeAbbr = TeamAbbr.fromId(game.homeTeamId)
            " ($awayAbbr ${play.awayScore}, $homeAbbr ${play.homeScore})"
        } else ""

        batterNameRow.addView(TextView(ctx).apply {
            text = "${play.batterName}$scoreSuffix"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(primaryColor)
        })
        if (play.isScoringPlay) {
            batterNameRow.addView(TextView(ctx).apply {
                text = "SCORING PLAY"
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.white))
                setPadding(dpToPx(6), dpToPx(1), dpToPx(6), dpToPx(1))
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(ContextCompat.getColor(ctx, R.color.mlb_red))
                    cornerRadius = dpToPx(4).toFloat()
                }
                background = bg
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginStart = dpToPx(8)
                layoutParams = lp
                setSingleLine(true)
            })
        }
        leftBlock.addView(batterNameRow)

        leftBlock.addView(TextView(ctx).apply {
            text = play.description
            textSize = 12f
            setTextColor(secondaryColor)
            setPadding(0, 3, 0, 0)
            // Ensure full description is shown
            setSingleLine(false)
            ellipsize = null
        })

        // Right: pitch count (+ chevron if expandable)
        val rightBlock = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        if (hasPitches) {
            rightBlock.addView(TextView(ctx).apply {
                text = "$pitchCountLabel"
                textSize = 11.5f
                setTextColor(secondaryColor)
                gravity = Gravity.CENTER_VERTICAL
                setSingleLine(true)
            })
            rightBlock.addView(TextView(ctx).apply {
                text = "  ›"
                textSize = 18f
                setTextColor(secondaryColor)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
                setSingleLine(true)
            })
        }

        headerRow.addView(leftBlock)
        headerRow.addView(rightBlock)

        // ── Pitch detail panel (collapsible) ─────────────────────────────
        val pitchPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (expandedPlays.contains(play.atBatIndex)) View.VISIBLE else View.GONE
            setPadding(0, 4, 0, 12)
        }

        if (hasPitches) {
            pitchPanel.addView(buildPitchHeaderRow())
            play.pitches.forEach { pitch -> pitchPanel.addView(buildPitchDetailRow(pitch)) }

            val chevronView = rightBlock.getChildAt(rightBlock.childCount - 1) as? TextView
            if (expandedPlays.contains(play.atBatIndex)) {
                chevronView?.text = "  ˅"
            }

            headerRow.setOnClickListener {
                if (expandedPlays.contains(play.atBatIndex)) {
                    expandedPlays.remove(play.atBatIndex)
                    pitchPanel.visibility = View.GONE
                    chevronView?.text = "  ›"
                } else {
                    expandedPlays.add(play.atBatIndex)
                    pitchPanel.visibility = View.VISIBLE
                    chevronView?.text = "  ˅"
                }
            }
        }

        // Bottom divider
        val divider = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1))
            setBackgroundColor(dividerColor)
        }

        wrapper.addView(headerRow)
        wrapper.addView(pitchPanel)
        wrapper.addView(divider)
        return wrapper
    }

    private fun buildPitchHeaderRow(): View {
        val ctx = requireContext()
        val color = ContextCompat.getColor(ctx, R.color.text_secondary)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(8), 0, dpToPx(4))
            addView(makePitchLabel("#", color, widthDp = 24))
            addView(makePitchLabel("Pitch Type", color, weight = 1.1f, align = Gravity.START))
            addView(makePitchLabel("Speed", color, widthDp = 76))
            addView(makePitchLabel("Result", color, weight = 1f, align = Gravity.START))
        }
    }

    private fun buildPitchDetailRow(pitch: PitchDetail): View {
        val ctx = requireContext()
        val primaryColor = ContextCompat.getColor(ctx, R.color.text_primary)
        val accentColor = callColor(pitch.callCode)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(6), 0, dpToPx(6))
            addView(makePitchLabel(pitch.pitchNumber.toString(), primaryColor, widthDp = 24))
            addView(makePitchLabel(
                formatPitchType(pitch.pitchType, pitch.pitchTypeCode),
                primaryColor, weight = 1.1f, align = Gravity.START
            ))
            addView(makePitchLabel(pitch.speed, primaryColor, widthDp = 76))
            addView(makePitchLabel(pitch.callDescription, accentColor, weight = 1f, align = Gravity.START, multiLine = true))
        }
    }

    private fun renderPreviewLineup(lineup: TeamLineup, table: android.widget.TableLayout) {
        table.removeAllViews()
        lineup.batters.forEach { batter ->
            val row = TableRow(requireContext())
            addCell(row, batter.battingOrder.toString(), isHeader = false, widthWeight = 0.15f)
            addCell(row, batter.name, isHeader = false, stretch = true, widthWeight = 0.65f)
            addCell(row, batter.position, isHeader = false, widthWeight = 0.2f)

            row.isClickable = true
            row.setOnClickListener { navigateToPlayerStats(batter.id) }
            table.addView(row)
        }
    }

    private fun renderLineup(lineup: TeamLineup, table: android.widget.TableLayout) {
        table.removeAllViews()
        if (lineup.batters.isEmpty()) return

        table.isStretchAllColumns = false

        // Fixed dp widths — total ~596dp so the table overflows the screen.
        // The parent HorizontalScrollView lets users scroll right to reveal OBP/SLG/OPS.
        val header = TableRow(requireContext())
        addCell(header, "#",      isHeader = true, fixedWidthDp = 28)
        addCell(header, "Batter", isHeader = true, stretch = true, fixedWidthDp = 130)
        addCell(header, "POS",    isHeader = true, fixedWidthDp = 38)
        addCell(header, "AB",     isHeader = true, fixedWidthDp = 36)
        addCell(header, "R",      isHeader = true, fixedWidthDp = 36)
        addCell(header, "H",      isHeader = true, fixedWidthDp = 36)
        addCell(header, "RBI",    isHeader = true, fixedWidthDp = 40)
        addCell(header, "HR",     isHeader = true, fixedWidthDp = 36)
        addCell(header, "BB",     isHeader = true, fixedWidthDp = 36)
        addCell(header, "K",      isHeader = true, fixedWidthDp = 36)
        addCell(header, "AVG",    isHeader = true, fixedWidthDp = 48)
        // ── scroll right to see these ──────────────────────────────────────
        addCell(header, "OBP",    isHeader = true, fixedWidthDp = 48)
        addCell(header, "SLG",    isHeader = true, fixedWidthDp = 48)
        addCell(header, "OPS",    isHeader = true, fixedWidthDp = 48)
        table.addView(header)

        var totalAb = 0; var totalR = 0; var totalH = 0; var totalRbi = 0; var totalBb = 0; var totalK = 0; var totalHr = 0

        lineup.batters.forEach { batter ->
            totalAb += batter.ab
            totalR += batter.r
            totalH += batter.h
            totalRbi += batter.rbi
            totalBb += batter.bb
            totalK += batter.k
            totalHr += batter.hr

            val row = TableRow(requireContext())
            addCell(row, if (batter.isSubstitution) "" else batter.battingOrder.toString(), isHeader = false, fixedWidthDp = 28)
            addCell(row, batter.name, isHeader = false, stretch = true, indent = false, fixedWidthDp = 130, isGrey = batter.isSubstitution)
            addCell(row, batter.position, isHeader = false, fixedWidthDp = 38)
            addCell(row, batter.ab.toString(),  isHeader = false, fixedWidthDp = 36)
            addCell(row, batter.r.toString(),   isHeader = false, fixedWidthDp = 36)
            addCell(row, batter.h.toString(),   isHeader = false, fixedWidthDp = 36)
            addCell(row, batter.rbi.toString(), isHeader = false, fixedWidthDp = 40)
            addCell(row, batter.hr.toString(),  isHeader = false, fixedWidthDp = 36)
            addCell(row, batter.bb.toString(),  isHeader = false, fixedWidthDp = 36)
            addCell(row, batter.k.toString(),   isHeader = false, fixedWidthDp = 36)
            addCell(row, batter.seasonAvg ?: ".---", isHeader = false, fixedWidthDp = 48)
            addCell(row, batter.seasonObp ?: ".---", isHeader = false, fixedWidthDp = 48)
            addCell(row, batter.seasonSlg ?: ".---", isHeader = false, fixedWidthDp = 48)
            addCell(row, batter.seasonOps ?: ".---", isHeader = false, fixedWidthDp = 48)

            row.isClickable = true
            row.setOnClickListener { navigateToPlayerStats(batter.id) }
            table.addView(row)
        }

        // Divider
        val divider = View(requireContext()).apply {
            val h = dpToPx(1)
            layoutParams = android.widget.TableLayout.LayoutParams(
                android.widget.TableLayout.LayoutParams.MATCH_PARENT, h
            ).apply { setMargins(0, dpToPx(8), 0, dpToPx(8)) }
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
        }
        table.addView(divider)

        // Totals row
        val totalsRow = TableRow(requireContext())
        addCell(totalsRow, "",               isHeader = false, fixedWidthDp = 28)
        addCell(totalsRow, "TOTALS",         isHeader = false, isBold = true, stretch = true, fixedWidthDp = 130)
        addCell(totalsRow, "",               isHeader = false, fixedWidthDp = 38)
        addCell(totalsRow, totalAb.toString(),  isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalR.toString(),   isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalH.toString(),   isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalRbi.toString(), isHeader = false, isBold = true, fixedWidthDp = 40)
        addCell(totalsRow, totalHr.toString(),  isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalBb.toString(),  isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalK.toString(),   isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, "", isHeader = false, fixedWidthDp = 48)
        addCell(totalsRow, "", isHeader = false, fixedWidthDp = 48)
        addCell(totalsRow, "", isHeader = false, fixedWidthDp = 48)
        addCell(totalsRow, "", isHeader = false, fixedWidthDp = 48)
        table.addView(totalsRow)
    }

    private fun renderPitchers(lineup: TeamLineup, table: android.widget.TableLayout) {
        table.removeAllViews()
        if (lineup.pitchers.isEmpty()) return

        // Use fixed widths to match the batter table's behavior (overflow + horizontal scroll).
        table.isStretchAllColumns = false
        table.isShrinkAllColumns = false

        val header = TableRow(requireContext())
        addCell(header, "Pitcher", isHeader = true, stretch = true, fixedWidthDp = 130)
        addCell(header, "IP",   isHeader = true, fixedWidthDp = 40, noEllipsize = true)
        addCell(header, "H",    isHeader = true, fixedWidthDp = 36)
        addCell(header, "R",    isHeader = true, fixedWidthDp = 36)
        addCell(header, "ER",   isHeader = true, fixedWidthDp = 36)
        addCell(header, "BB",   isHeader = true, fixedWidthDp = 36)
        addCell(header, "K",    isHeader = true, fixedWidthDp = 36)
        addCell(header, "HR",   isHeader = true, fixedWidthDp = 36)
        addCell(header, "P",    isHeader = true, fixedWidthDp = 36)
        addCell(header, "S",    isHeader = true, fixedWidthDp = 36)
        addCell(header, "B",    isHeader = true, fixedWidthDp = 36)
        // ── scroll right to see these ──────────────────────────────────────
        addCell(header, "ERA",  isHeader = true, fixedWidthDp = 48)
        addCell(header, "WHIP", isHeader = true, fixedWidthDp = 48)
        table.addView(header)

        var totalOuts = 0; var totalH = 0; var totalR = 0; var totalEr = 0; var totalBb = 0; var totalK = 0; var totalHr = 0
        var totalP = 0; var totalS = 0; var totalB = 0

        lineup.pitchers.forEach { p ->
            val outsParts = p.ip.split(".")
            val major = outsParts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = outsParts.getOrNull(1)?.toIntOrNull() ?: 0
            totalOuts += (major * 3) + minor

            totalH += p.h
            totalR += p.r
            totalEr += p.er
            totalBb += p.bb
            totalK += p.k
            totalHr += p.hr
            totalP += p.pitches
            totalS += p.strikes
            totalB += p.balls

            val row = TableRow(requireContext())
            addCell(row, p.name, isHeader = false, stretch = true, fixedWidthDp = 130)
            addCell(row, p.ip,            isHeader = false, fixedWidthDp = 40, noEllipsize = true)
            addCell(row, p.h.toString(),  isHeader = false, fixedWidthDp = 36)
            addCell(row, p.r.toString(),  isHeader = false, fixedWidthDp = 36)
            addCell(row, p.er.toString(), isHeader = false, fixedWidthDp = 36)
            addCell(row, p.bb.toString(), isHeader = false, fixedWidthDp = 36)
            addCell(row, p.k.toString(),  isHeader = false, fixedWidthDp = 36)
            addCell(row, p.hr.toString(), isHeader = false, fixedWidthDp = 36)
            addCell(row, p.pitches.toString(), isHeader = false, fixedWidthDp = 36)
            addCell(row, p.strikes.toString(), isHeader = false, fixedWidthDp = 36)
            addCell(row, p.balls.toString(),   isHeader = false, fixedWidthDp = 36)
            addCell(row, p.seasonEra,             isHeader = false, fixedWidthDp = 48)
            addCell(row, p.seasonWhip ?: "-.--",  isHeader = false, fixedWidthDp = 48)

            row.isClickable = true
            row.setOnClickListener { navigateToPlayerStats(p.id) }
            table.addView(row)
        }

        val totalIp = "${totalOuts / 3}.${totalOuts % 3}"

        // Divider
        val divider = View(requireContext()).apply {
            val h = dpToPx(1)
            layoutParams = android.widget.TableLayout.LayoutParams(
                android.widget.TableLayout.LayoutParams.MATCH_PARENT, h
            ).apply { setMargins(0, dpToPx(8), 0, dpToPx(8)) }
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
        }
        table.addView(divider)

        // Totals row
        val totalsRow = TableRow(requireContext())
        addCell(totalsRow, "TOTALS",              isHeader = false, isBold = true, stretch = true, fixedWidthDp = 130)
        addCell(totalsRow, totalIp,               isHeader = false, isBold = true, fixedWidthDp = 40, noEllipsize = true)
        addCell(totalsRow, totalH.toString(),     isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalR.toString(),     isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalEr.toString(),    isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalBb.toString(),    isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalK.toString(),     isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalHr.toString(),    isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalP.toString(),     isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalS.toString(),     isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, totalB.toString(),     isHeader = false, isBold = true, fixedWidthDp = 36)
        addCell(totalsRow, "",                    isHeader = false, fixedWidthDp = 48)
        addCell(totalsRow, "",                    isHeader = false, fixedWidthDp = 48)
        table.addView(totalsRow)
    }

    private fun callColor(code: String): Int {
        val ctx = requireContext()
        return when (code.uppercase()) {
            "B"            -> ContextCompat.getColor(ctx, R.color.mlb_green_safe)
            "C", "S", "W"  -> ContextCompat.getColor(ctx, R.color.mlb_red)
            "F", "T", "R", "L", "M" -> ContextCompat.getColor(ctx, R.color.text_secondary)
            "X", "D", "E", "H" -> ContextCompat.getColor(ctx, R.color.mlb_gold)
            else           -> ContextCompat.getColor(ctx, R.color.text_primary)
        }
    }

    private fun formatPitchType(fullName: String, code: String): String {
        return when (code.uppercase()) {
            "FF" -> "4-Seam FB"
            "SI", "FT" -> "Sinker"
            "FC" -> "Cutter"
            "SL" -> "Slider"
            "ST" -> "Sweeper"
            "CU", "KC" -> "Curveball"
            "CH" -> "Changeup"
            "FS", "FO" -> "Splitter"
            "KN" -> "Knuckleball"
            "EP" -> "Eephus"
            "IN" -> "Int. Ball"
            "PO" -> "Pitchout"
            else -> if (fullName.length > 14) code else fullName
        }
    }

    private fun makePitchLabel(
        text: String,
        color: Int,
        widthDp: Int = -1,
        weight: Float = 0f,
        bold: Boolean = false,
        align: Int = Gravity.CENTER,
        multiLine: Boolean = false
    ): TextView {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            this.text = text
            textSize = 12f
            setTextColor(color)
            gravity = align or Gravity.CENTER_VERTICAL
            if (bold) setTypeface(null, Typeface.BOLD)
            if (multiLine) {
                maxLines = Int.MAX_VALUE
                ellipsize = null
                isSingleLine = false
            } else {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setSingleLine(true)
            }
            layoutParams = if (weight > 0f) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            } else {
                LinearLayout.LayoutParams(
                    if (widthDp > 0) (widthDp * dp).toInt() else LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        }
    }

    private fun addCell(
        row: TableRow,
        text: String,
        isHeader: Boolean = false,
        isBold: Boolean = false,
        stretch: Boolean = false,
        indent: Boolean = false,
        widthWeight: Float = -1f,
        noEllipsize: Boolean = false,
        fixedWidthDp: Int = -1,
        isGrey: Boolean = false
    ) {
        val dp = requireContext().resources.displayMetrics.density
        val tv = TextView(requireContext()).apply {
            this.text = text
            val startPadding = if (indent) dpToPx(24) else (if (stretch) dpToPx(10) else dpToPx(6))
            setPadding(startPadding, dpToPx(12), dpToPx(6), dpToPx(12))
            gravity = if (stretch) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            textSize = if (isHeader) 11.5f else 13.5f
            setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isHeader || isGrey) R.color.text_secondary else R.color.text_primary
                )
            )
            if (isBold || isHeader) setTypeface(null, Typeface.BOLD)
            
            if (stretch && !noEllipsize) {
                ellipsize = null
                isSingleLine = false
            } else {
                setSingleLine(true)
                maxLines = 1
                if (noEllipsize) {
                    ellipsize = null
                } else {
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
            }

            if (fixedWidthDp > 0) {
                minWidth = (fixedWidthDp * dp).toInt()
            }
        }
        val width = if (fixedWidthDp > 0) (fixedWidthDp * dp).toInt() else TableRow.LayoutParams.WRAP_CONTENT
        val params = TableRow.LayoutParams(width, TableRow.LayoutParams.WRAP_CONTENT)
        if (fixedWidthDp <= 0) {
            if (widthWeight > 0f) params.weight = widthWeight else if (stretch) params.weight = 1f
        }
        tv.layoutParams = params
        row.addView(tv)
    }

    private fun updateOuts(outs: Int) {
        binding.out1.setImageResource(if (outs >= 1) R.drawable.shape_out_filled else R.drawable.shape_out_empty)
        binding.out2.setImageResource(if (outs >= 2) R.drawable.shape_out_filled else R.drawable.shape_out_empty)
        binding.out3.setImageResource(if (outs >= 3) R.drawable.shape_out_filled else R.drawable.shape_out_empty)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

class GameDetailViewModel(
    private val gamePk: Long,
    private val date: String,
    private val repository: GamesRepository
) : ViewModel() {

    private val _gameData = androidx.lifecycle.MutableLiveData<GameCardModel?>()
    val gameData: androidx.lifecycle.LiveData<GameCardModel?> = _gameData

    private val _loading = androidx.lifecycle.MutableLiveData<Boolean>()
    val loading: androidx.lifecycle.LiveData<Boolean> = _loading

    private var pollingJob: Job? = null

    init {
        initialLoad()
    }

    private fun initialLoad() {
        viewModelScope.launch {
            _loading.value = true
            fetchAndPost()
            _loading.value = false

            // Start polling if the game is live after first load
            if (_gameData.value?.gameState == "Live") {
                startPolling()
            }
        }
    }

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                // Fetch fresh data immediately when polling starts (e.g. onResume)
                fetchAndPost()

                // Poll quickly (3s) for live games to catch every pitch
                // and moderately (30s) for non-live to catch game starts/ends
                val current = _gameData.value
                val pollDelay = if (current?.gameState == "Live") 3000L else 30000L

                delay(pollDelay)
            }
        }
    }

    fun startPollingFast() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchAndPost()
                delay(3000L)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    private suspend fun fetchAndPost() {
        runCatching { repository.getGameDetail(gamePk, date) }.onSuccess { game ->
            _gameData.value = game
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

class GameDetailViewModelFactory(
    private val gamePk: Long,
    private val date: String,
    private val repository: GamesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        GameDetailViewModel(gamePk, date, repository) as T
}
