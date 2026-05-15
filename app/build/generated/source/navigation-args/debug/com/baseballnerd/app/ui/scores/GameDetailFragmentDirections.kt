package com.baseballnerd.app.ui.scores

import android.os.Bundle
import androidx.navigation.NavDirections
import com.baseballnerd.app.R
import kotlin.Int
import kotlin.String

public class GameDetailFragmentDirections private constructor() {
  private data class ActionGameDetailFragmentToPlayerStatsFragment(
    public val playerId: Int,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_gameDetailFragment_to_playerStatsFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putInt("playerId", this.playerId)
        return result
      }
  }

  private data class ActionGameDetailFragmentToTeamScheduleFragment(
    public val teamId: Int,
    public val teamName: String,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_gameDetailFragment_to_teamScheduleFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putInt("teamId", this.teamId)
        result.putString("teamName", this.teamName)
        return result
      }
  }

  public companion object {
    public fun actionGameDetailFragmentToPlayerStatsFragment(playerId: Int): NavDirections =
        ActionGameDetailFragmentToPlayerStatsFragment(playerId)

    public fun actionGameDetailFragmentToTeamScheduleFragment(teamId: Int, teamName: String):
        NavDirections = ActionGameDetailFragmentToTeamScheduleFragment(teamId, teamName)
  }
}
