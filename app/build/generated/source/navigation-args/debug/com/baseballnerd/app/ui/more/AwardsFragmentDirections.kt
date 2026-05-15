package com.baseballnerd.app.ui.more

import android.os.Bundle
import androidx.navigation.NavDirections
import com.baseballnerd.app.R
import kotlin.Int

public class AwardsFragmentDirections private constructor() {
  private data class ActionAwardsFragmentToPlayerStatsFragment(
    public val playerId: Int,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_awardsFragment_to_playerStatsFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putInt("playerId", this.playerId)
        return result
      }
  }

  public companion object {
    public fun actionAwardsFragmentToPlayerStatsFragment(playerId: Int): NavDirections =
        ActionAwardsFragmentToPlayerStatsFragment(playerId)
  }
}
