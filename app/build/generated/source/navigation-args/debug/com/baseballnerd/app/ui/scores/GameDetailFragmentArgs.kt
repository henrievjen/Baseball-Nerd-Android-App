package com.baseballnerd.app.ui.scores

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavArgs
import java.lang.IllegalArgumentException
import kotlin.Long
import kotlin.String
import kotlin.jvm.JvmStatic

public data class GameDetailFragmentArgs(
  public val gamePk: Long,
  public val date: String = "",
) : NavArgs {
  public fun toBundle(): Bundle {
    val result = Bundle()
    result.putLong("gamePk", this.gamePk)
    result.putString("date", this.date)
    return result
  }

  public fun toSavedStateHandle(): SavedStateHandle {
    val result = SavedStateHandle()
    result.set("gamePk", this.gamePk)
    result.set("date", this.date)
    return result
  }

  public companion object {
    @JvmStatic
    public fun fromBundle(bundle: Bundle): GameDetailFragmentArgs {
      bundle.setClassLoader(GameDetailFragmentArgs::class.java.classLoader)
      val __gamePk : Long
      if (bundle.containsKey("gamePk")) {
        __gamePk = bundle.getLong("gamePk")
      } else {
        throw IllegalArgumentException("Required argument \"gamePk\" is missing and does not have an android:defaultValue")
      }
      val __date : String?
      if (bundle.containsKey("date")) {
        __date = bundle.getString("date")
        if (__date == null) {
          throw IllegalArgumentException("Argument \"date\" is marked as non-null but was passed a null value.")
        }
      } else {
        __date = ""
      }
      return GameDetailFragmentArgs(__gamePk, __date)
    }

    @JvmStatic
    public fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): GameDetailFragmentArgs {
      val __gamePk : Long?
      if (savedStateHandle.contains("gamePk")) {
        __gamePk = savedStateHandle["gamePk"]
        if (__gamePk == null) {
          throw IllegalArgumentException("Argument \"gamePk\" of type long does not support null values")
        }
      } else {
        throw IllegalArgumentException("Required argument \"gamePk\" is missing and does not have an android:defaultValue")
      }
      val __date : String?
      if (savedStateHandle.contains("date")) {
        __date = savedStateHandle["date"]
        if (__date == null) {
          throw IllegalArgumentException("Argument \"date\" is marked as non-null but was passed a null value")
        }
      } else {
        __date = ""
      }
      return GameDetailFragmentArgs(__gamePk, __date)
    }
  }
}
