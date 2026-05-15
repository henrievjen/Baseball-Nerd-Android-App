package com.baseballnerd.app.ui.scores

import android.os.Bundle
import androidx.navigation.NavDirections
import com.baseballnerd.app.R
import kotlin.Int
import kotlin.Long
import kotlin.String

public class ScoresFragmentDirections private constructor() {
  private data class ActionScoresFragmentToGameDetailFragment(
    public val gamePk: Long,
    public val date: String = "",
  ) : NavDirections {
    public override val actionId: Int = R.id.action_scoresFragment_to_gameDetailFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putLong("gamePk", this.gamePk)
        result.putString("date", this.date)
        return result
      }
  }

  private data class ActionScoresFragmentToPlayerStatsFragment(
    public val playerId: Int,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_scoresFragment_to_playerStatsFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putInt("playerId", this.playerId)
        return result
      }
  }

  private data class ActionScoresFragmentToTeamScheduleFragment(
    public val teamId: Int,
    public val teamName: String,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_scoresFragment_to_teamScheduleFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putInt("teamId", this.teamId)
        result.putString("teamName", this.teamName)
        return result
      }
  }

  public companion object {
    public fun actionScoresFragmentToGameDetailFragment(gamePk: Long, date: String = ""):
        NavDirections = ActionScoresFragmentToGameDetailFragment(gamePk, date)

    public fun actionScoresFragmentToPlayerStatsFragment(playerId: Int): NavDirections =
        ActionScoresFragmentToPlayerStatsFragment(playerId)

    public fun actionScoresFragmentToTeamScheduleFragment(teamId: Int, teamName: String):
        NavDirections = ActionScoresFragmentToTeamScheduleFragment(teamId, teamName)
  }
}
