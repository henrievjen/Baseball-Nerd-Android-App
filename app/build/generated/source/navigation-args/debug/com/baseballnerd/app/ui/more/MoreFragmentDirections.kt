package com.baseballnerd.app.ui.more

import androidx.navigation.ActionOnlyNavDirections
import androidx.navigation.NavDirections
import com.baseballnerd.app.R

public class MoreFragmentDirections private constructor() {
  public companion object {
    public fun actionMoreFragmentToAwardsFragment(): NavDirections =
        ActionOnlyNavDirections(R.id.action_moreFragment_to_awardsFragment)

    public fun actionMoreFragmentToAboutFragment(): NavDirections =
        ActionOnlyNavDirections(R.id.action_moreFragment_to_aboutFragment)

    public fun actionMoreFragmentToWhosHotFragment(): NavDirections =
        ActionOnlyNavDirections(R.id.action_moreFragment_to_whosHotFragment)

    public fun actionMoreFragmentToSettingsFragment(): NavDirections =
        ActionOnlyNavDirections(R.id.action_moreFragment_to_settingsFragment)

    public fun actionMoreFragmentToPositionsFragment(): NavDirections =
        ActionOnlyNavDirections(R.id.action_moreFragment_to_positionsFragment)
  }
}
