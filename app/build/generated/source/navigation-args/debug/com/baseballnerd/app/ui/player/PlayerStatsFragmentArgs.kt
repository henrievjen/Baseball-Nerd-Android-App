package com.baseballnerd.app.ui.player

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavArgs
import java.lang.IllegalArgumentException
import kotlin.Int
import kotlin.jvm.JvmStatic

public data class PlayerStatsFragmentArgs(
  public val playerId: Int,
) : NavArgs {
  public fun toBundle(): Bundle {
    val result = Bundle()
    result.putInt("playerId", this.playerId)
    return result
  }

  public fun toSavedStateHandle(): SavedStateHandle {
    val result = SavedStateHandle()
    result.set("playerId", this.playerId)
    return result
  }

  public companion object {
    @JvmStatic
    public fun fromBundle(bundle: Bundle): PlayerStatsFragmentArgs {
      bundle.setClassLoader(PlayerStatsFragmentArgs::class.java.classLoader)
      val __playerId : Int
      if (bundle.containsKey("playerId")) {
        __playerId = bundle.getInt("playerId")
      } else {
        throw IllegalArgumentException("Required argument \"playerId\" is missing and does not have an android:defaultValue")
      }
      return PlayerStatsFragmentArgs(__playerId)
    }

    @JvmStatic
    public fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): PlayerStatsFragmentArgs {
      val __playerId : Int?
      if (savedStateHandle.contains("playerId")) {
        __playerId = savedStateHandle["playerId"]
        if (__playerId == null) {
          throw IllegalArgumentException("Argument \"playerId\" of type integer does not support null values")
        }
      } else {
        throw IllegalArgumentException("Required argument \"playerId\" is missing and does not have an android:defaultValue")
      }
      return PlayerStatsFragmentArgs(__playerId)
    }
  }
}
