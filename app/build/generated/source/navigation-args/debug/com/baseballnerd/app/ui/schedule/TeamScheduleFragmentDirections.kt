package com.baseballnerd.app.ui.schedule

import android.os.Bundle
import androidx.navigation.NavDirections
import com.baseballnerd.app.R
import kotlin.Int
import kotlin.Long
import kotlin.String

public class TeamScheduleFragmentDirections private constructor() {
  private data class ActionTeamScheduleFragmentToGameDetailFragment(
    public val gamePk: Long,
    public val date: String = "",
  ) : NavDirections {
    public override val actionId: Int = R.id.action_teamScheduleFragment_to_gameDetailFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putLong("gamePk", this.gamePk)
        result.putString("date", this.date)
        return result
      }
  }

  private data class ActionTeamScheduleFragmentToPlayerStatsFragment(
    public val playerId: Int,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_teamScheduleFragment_to_playerStatsFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putInt("playerId", this.playerId)
        return result
      }
  }

  public companion object {
    public fun actionTeamScheduleFragmentToGameDetailFragment(gamePk: Long, date: String = ""):
        NavDirections = ActionTeamScheduleFragmentToGameDetailFragment(gamePk, date)

    public fun actionTeamScheduleFragmentToPlayerStatsFragment(playerId: Int): NavDirections =
        ActionTeamScheduleFragmentToPlayerStatsFragment(playerId)
  }
}
