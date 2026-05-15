package com.baseballnerd.app.ui.schedule

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavArgs
import java.lang.IllegalArgumentException
import kotlin.Int
import kotlin.String
import kotlin.jvm.JvmStatic

public data class TeamScheduleFragmentArgs(
  public val teamId: Int,
  public val teamName: String,
) : NavArgs {
  public fun toBundle(): Bundle {
    val result = Bundle()
    result.putInt("teamId", this.teamId)
    result.putString("teamName", this.teamName)
    return result
  }

  public fun toSavedStateHandle(): SavedStateHandle {
    val result = SavedStateHandle()
    result.set("teamId", this.teamId)
    result.set("teamName", this.teamName)
    return result
  }

  public companion object {
    @JvmStatic
    public fun fromBundle(bundle: Bundle): TeamScheduleFragmentArgs {
      bundle.setClassLoader(TeamScheduleFragmentArgs::class.java.classLoader)
      val __teamId : Int
      if (bundle.containsKey("teamId")) {
        __teamId = bundle.getInt("teamId")
      } else {
        throw IllegalArgumentException("Required argument \"teamId\" is missing and does not have an android:defaultValue")
      }
      val __teamName : String?
      if (bundle.containsKey("teamName")) {
        __teamName = bundle.getString("teamName")
        if (__teamName == null) {
          throw IllegalArgumentException("Argument \"teamName\" is marked as non-null but was passed a null value.")
        }
      } else {
        throw IllegalArgumentException("Required argument \"teamName\" is missing and does not have an android:defaultValue")
      }
      return TeamScheduleFragmentArgs(__teamId, __teamName)
    }

    @JvmStatic
    public fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): TeamScheduleFragmentArgs {
      val __teamId : Int?
      if (savedStateHandle.contains("teamId")) {
        __teamId = savedStateHandle["teamId"]
        if (__teamId == null) {
          throw IllegalArgumentException("Argument \"teamId\" of type integer does not support null values")
        }
      } else {
        throw IllegalArgumentException("Required argument \"teamId\" is missing and does not have an android:defaultValue")
      }
      val __teamName : String?
      if (savedStateHandle.contains("teamName")) {
        __teamName = savedStateHandle["teamName"]
        if (__teamName == null) {
          throw IllegalArgumentException("Argument \"teamName\" is marked as non-null but was passed a null value")
        }
      } else {
        throw IllegalArgumentException("Required argument \"teamName\" is missing and does not have an android:defaultValue")
      }
      return TeamScheduleFragmentArgs(__teamId, __teamName)
    }
  }
}
