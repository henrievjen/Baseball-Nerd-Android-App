package com.baseballnerd.app.ui.stats

import android.os.Bundle
import androidx.navigation.NavDirections
import com.baseballnerd.app.R
import kotlin.Int

public class PositionsFragmentDirections private constructor() {
  private data class ActionPositionsFragmentToPlayerStatsFragment(
    public val playerId: Int,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_positionsFragment_to_playerStatsFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putInt("playerId", this.playerId)
        return result
      }
  }

  public companion object {
    public fun actionPositionsFragmentToPlayerStatsFragment(playerId: Int): NavDirections =
        ActionPositionsFragmentToPlayerStatsFragment(playerId)
  }
}
