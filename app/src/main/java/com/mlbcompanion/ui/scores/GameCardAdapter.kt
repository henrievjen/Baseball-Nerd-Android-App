package com.baseballnerd.app.ui.scores

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.decode.SvgDecoder
import coil.load
import com.baseballnerd.app.R
import com.baseballnerd.app.data.model.GameCardModel
import com.baseballnerd.app.databinding.ItemGameCardBinding
import com.baseballnerd.app.databinding.ItemGameGridCardBinding
import com.baseballnerd.app.util.TeamColors
import java.text.SimpleDateFormat
import java.util.*

class GameCardAdapter(
    private val onCardClick: (GameCardModel) -> Unit,
    private val onTeamClick: (Int, String) -> Unit = { _, _ -> },
    private val onPlayerClick: (Int) -> Unit = { _ -> }
) : ListAdapter<GameCardModel, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        const val VIEW_TYPE_LIST = 0
        const val VIEW_TYPE_GRID = 1
    }

    private var viewType = VIEW_TYPE_LIST

    fun setViewType(type: Int) {
        if (this.viewType != type) {
            this.viewType = type
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_GRID) {
            val binding = ItemGameGridCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            GridViewHolder(binding)
        } else {
            val binding = ItemGameCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            ListViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val game = getItem(position)
        if (holder is ListViewHolder) holder.bind(game)
        else if (holder is GridViewHolder) holder.bind(game)
    }

    private fun extractTeamName(fullName: String): String {
        val parts = fullName.split(" ")
        if (parts.size < 2) return fullName
        val last = parts.last()
        return if (last.lowercase() == "sox" || last.lowercase() == "jays") {
            val secondLast = parts[parts.size - 2]
            "$secondLast $last"
        } else {
            last
        }
    }

    inner class ListViewHolder(private val binding: ItemGameCardBinding) : RecyclerView.ViewHolder(binding.root) {
        private val displayDateFormat = SimpleDateFormat("EEE, MMM d", Locale.US)
        private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun bind(game: GameCardModel) {
            binding.root.setOnClickListener { onCardClick(game) }
            binding.layoutAwayLogo.setOnClickListener { onTeamClick(game.awayTeamId, game.awayTeamName) }
            binding.layoutHomeLogo.setOnClickListener { onTeamClick(game.homeTeamId, game.homeTeamName) }

            binding.tvAwayTeam.text = extractTeamName(game.awayTeamName)
            binding.tvHomeTeam.text = extractTeamName(game.homeTeamName)
            binding.tvAwayScore.text = game.awayScore.toString()
            binding.tvHomeScore.text = game.homeScore.toString()

            try {
                val date = apiDateFormat.parse(game.gameDate)
                binding.tvGameDate.text = date?.let { displayDateFormat.format(it).uppercase() }
                binding.tvGameDate.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.tvGameDate.visibility = View.GONE
            }

            val homeColor = TeamColors.getPrimaryColor(game.homeTeamId)
            binding.viewAwayLogoBg.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            binding.viewHomeLogoBg.backgroundTintList = ColorStateList.valueOf(Color.WHITE)

            binding.ivAwayLogo.load(TeamColors.getLogoUrl(game.awayTeamId)) {
                decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
                crossfade(true)
                error(R.drawable.ic_baseball_placeholder)
            }
            binding.ivHomeLogo.load(TeamColors.getLogoUrl(game.homeTeamId)) {
                decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
                crossfade(true)
                error(R.drawable.ic_baseball_placeholder)
            }

            val hasStarted = game.gameState == "Live" || game.gameState == "Final"
            val isWarmup = game.detailedState.lowercase() in setOf("warmup", "pre-game", "pregame", "delayed start")
            val showLiveData = hasStarted && !isWarmup

            if (game.gameState == "Live") {
                binding.tvStatusCentered.visibility = View.GONE
                binding.layoutStatusContainer.visibility = View.VISIBLE
                binding.tvInning.text = game.inningOrdinal
                binding.viewLiveIndicator.visibility = View.VISIBLE
                binding.layoutLiveData.visibility = if (showLiveData) View.VISIBLE else View.GONE

                if (showLiveData) {
                    binding.baseView.setRunners(game.runnerOnFirst, game.runnerOnSecond, game.runnerOnThird)
                    binding.tvCount.text = "${game.balls} - ${game.strikes}"
                    updateOuts(game.outs)
                }

                if (showLiveData && game.currentBatter != null) {
                    binding.tvBatterInfo.visibility = View.VISIBLE
                    val b = game.currentBatter
                    val formattedName = formatName(b.name)
                    val batterText = "AB: $formattedName ${b.hits}-${b.atBats}"
                    val spannable = SpannableStringBuilder(batterText)
                    spannable.setSpan(StyleSpan(Typeface.BOLD), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    binding.tvBatterInfo.text = spannable
                    binding.tvBatterInfo.setOnClickListener { b.id?.let { onPlayerClick(it) } }
                } else {
                    binding.tvBatterInfo.visibility = View.GONE
                }

                if (showLiveData && game.currentPitcher != null) {
                    binding.tvPitcherInfo.visibility = View.VISIBLE
                    val p = game.currentPitcher
                    val pitcherText = "P: ${p.name} ${p.ip} IP, ${p.er} ER, ${p.k} K, ${p.bb} BB"
                    val spannable = SpannableStringBuilder(pitcherText)
                    spannable.setSpan(StyleSpan(Typeface.BOLD), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    binding.tvPitcherInfo.text = pitcherText
                    binding.tvPitcherInfo.setOnClickListener { p.id?.let { onPlayerClick(it) } }
                } else {
                    binding.tvPitcherInfo.visibility = View.GONE
                }
            } else {
                binding.layoutStatusContainer.visibility = View.GONE
                binding.tvStatusCentered.visibility = View.VISIBLE
                binding.tvStatusCentered.text = game.inningOrdinal
                binding.tvBatterInfo.visibility = View.GONE
                binding.tvPitcherInfo.visibility = View.GONE
            }

            // Update Badges
            updateBadges(game)

            binding.viewTeamAccent.setBackgroundColor(homeColor)
        }

        private fun updateBadges(game: GameCardModel) {
            var hasBadge = false

            // Main Badge for non-regular season games
            val badgeLabel = if (game.gameType != null && game.gameType != "R") {
                val description = game.seriesDescription
                val gameNum = game.seriesGameNumber

                if (!description.isNullOrBlank()) {
                    // For postseason, include the game number
                    if (gameNum != null && game.gameType in setOf("W", "D", "L", "F", "C")) {
                        "$description Game $gameNum".uppercase()
                    } else {
                        description.uppercase()
                    }
                } else {
                    // Fallback to generic labels
                    when (game.gameType) {
                        "S" -> "SPRING TRAINING"
                        "E" -> "PRESEASON"
                        "W" -> "WORLD SERIES"
                        "D" -> "DIVISION SERIES"
                        "L" -> "LEAGUE CHAMPIONSHIP SERIES"
                        "F" -> "WILD CARD"
                        "C" -> "CHAMPIONSHIP"
                        "A" -> "ALL-STAR GAME"
                        else -> null
                    }
                }
            } else null

            if (badgeLabel != null) {
                binding.tvPlayoffBadge.text = badgeLabel
                binding.tvPlayoffBadge.visibility = View.VISIBLE
                hasBadge = true
                binding.tvGameTypeBadge.visibility = View.GONE
            } else {
                binding.tvPlayoffBadge.visibility = View.GONE
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

        private fun updateOuts(outs: Int) {
            binding.out1.setImageResource(if (outs >= 1) R.drawable.shape_out_filled else R.drawable.shape_out_empty)
            binding.out2.setImageResource(if (outs >= 2) R.drawable.shape_out_filled else R.drawable.shape_out_empty)
            binding.out3.setImageResource(if (outs >= 3) R.drawable.shape_out_filled else R.drawable.shape_out_empty)
        }
    }

    inner class GridViewHolder(private val binding: ItemGameGridCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(game: GameCardModel) {
            binding.root.setOnClickListener { onCardClick(game) }
            binding.layoutAwayLogo.setOnClickListener { onTeamClick(game.awayTeamId, game.awayTeamName) }
            binding.layoutHomeLogo.setOnClickListener { onTeamClick(game.homeTeamId, game.homeTeamName) }

            binding.tvGameStatus.text = game.inningOrdinal
            binding.tvAwayTeam.text = extractTeamName(game.awayTeamName)
            binding.tvHomeTeam.text = extractTeamName(game.homeTeamName)
            binding.tvAwayScore.text = game.awayScore.toString()
            binding.tvHomeScore.text = game.homeScore.toString()

            binding.ivAwayLogo.load(TeamColors.getLogoUrl(game.awayTeamId)) {
                decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
                crossfade(true)
                error(R.drawable.ic_baseball_placeholder)
            }
            binding.ivHomeLogo.load(TeamColors.getLogoUrl(game.homeTeamId)) {
                decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
                crossfade(true)
                error(R.drawable.ic_baseball_placeholder)
            }

            // Update Badges
            updateBadges(game)
        }

        private fun updateBadges(game: GameCardModel) {
            var hasBadge = false

            // Main Badge for non-regular season games
            val badgeLabel = if (game.gameType != null && game.gameType != "R") {
                val description = game.seriesDescription
                val gameNum = game.seriesGameNumber

                if (!description.isNullOrBlank()) {
                    // For postseason, include the game number
                    if (gameNum != null && game.gameType in setOf("W", "D", "L", "F", "C")) {
                        "$description Game $gameNum".uppercase()
                    } else {
                        description.uppercase()
                    }
                } else {
                    // Fallback to generic labels
                    when (game.gameType) {
                        "S" -> "SPRING TRAINING"
                        "E" -> "PRESEASON"
                        "W" -> "WORLD SERIES"
                        "D" -> "DIVISION SERIES"
                        "L" -> "LEAGUE CHAMPIONSHIP SERIES"
                        "F" -> "WILD CARD"
                        "C" -> "CHAMPIONSHIP"
                        "A" -> "ALL-STAR GAME"
                        else -> null
                    }
                }
            } else null

            if (badgeLabel != null) {
                binding.tvPlayoffBadge.text = badgeLabel
                binding.tvPlayoffBadge.visibility = View.VISIBLE
                hasBadge = true
                binding.tvGameTypeBadge.visibility = View.GONE
            } else {
                binding.tvPlayoffBadge.visibility = View.GONE
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
    }

    private fun formatName(fullName: String): String {
        val parts = fullName.split(" ").filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val firstInitial = parts[0].firstOrNull()?.toString() ?: ""
            val lastName = parts.last()
            return "$firstInitial. $lastName"
        }
        return fullName
    }

    object DiffCallback : DiffUtil.ItemCallback<GameCardModel>() {
        override fun areItemsTheSame(old: GameCardModel, new: GameCardModel) = old.gamePk == new.gamePk
        override fun areContentsTheSame(old: GameCardModel, new: GameCardModel) = old == new
    }
}
