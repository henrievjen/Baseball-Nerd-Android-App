package com.baseballnerd.app.ui.stats

import android.os.Bundle
import androidx.navigation.NavDirections
import com.baseballnerd.app.R
import kotlin.Int

public class StatsFragmentDirections private constructor() {
  private data class ActionStatsFragmentToPlayerStatsFragment(
    public val playerId: Int,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_statsFragment_to_playerStatsFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putInt("playerId", this.playerId)
        return result
      }
  }

  public companion object {
    public fun actionStatsFragmentToPlayerStatsFragment(playerId: Int): NavDirections =
        ActionStatsFragmentToPlayerStatsFragment(playerId)
  }
}
