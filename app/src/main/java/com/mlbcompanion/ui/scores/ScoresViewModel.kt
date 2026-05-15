package com.baseballnerd.app.ui.scores

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.*

data class DateTab(
    val date: String,       // "yyyy-MM-dd" for API
    val label: String,      // "Today", "Mon 3/31", etc.
    val isToday: Boolean
)

class ScoresViewModel : ViewModel() {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val labelSdf = SimpleDateFormat("EEE M/d", Locale.US)

    // Anchor today's date
    private var todayMidnight: Calendar = getMidnightCalendar()
    private var todayString = sdf.format(todayMidnight.time)

    private val _selectedDate = MutableLiveData<String>(todayString)
    val selectedDate: LiveData<String> = _selectedDate

    companion object {
        const val MAX_PAGES = 20_000
        // Position 10_000 === today. Positions below = past, above = future.
        const val INITIAL_POSITION = 10_000
    }

    private fun getMidnightCalendar(): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    /**
     * Returns the epoch day (days since 1970-01-01) for today's midnight.
     * Used by ScorePagerAdapter as a stable base for fragment IDs so that
     * after a date rollover, stale cached fragments are not reused.
     */
    fun getTodayEpochDay(): Long = todayMidnight.timeInMillis / (1_000L * 60 * 60 * 24)

    /**
     * Call this when the app resumes to ensure "Today" is actually today.
     * Returns true if the date actually changed.
     */
    fun refreshToday(): Boolean {
        val newTodayMidnight = getMidnightCalendar()
        val newTodayString = sdf.format(newTodayMidnight.time)

        if (newTodayString != todayString) {
            val oldSelected = _selectedDate.value
            val wasSelectedToday = oldSelected == todayString

            todayMidnight = newTodayMidnight
            todayString = newTodayString

            if (wasSelectedToday) {
                _selectedDate.value = todayString
            }
            return true
        }
        return false
    }

    fun selectDate(date: String) {
        if (_selectedDate.value == date) return
        _selectedDate.value = date
    }

    /** Converts a ViewPager position to a DateTab. */
    fun getDateForPosition(position: Int): DateTab {
        val offset = position - INITIAL_POSITION
        val cal = todayMidnight.clone() as Calendar
        cal.add(Calendar.DAY_OF_YEAR, offset)

        val dateStr = sdf.format(cal.time)
        val label = if (offset == 0) "Today" else labelSdf.format(cal.time)

        return DateTab(date = dateStr, label = label, isToday = offset == 0)
    }

    /**
     * Converts a "yyyy-MM-dd" date string to the ViewPager position that
     * represents it.  Computes the day-offset from today's midnight and adds
     * it to INITIAL_POSITION.  Clamps to valid page range so we never scroll
     * out of bounds.
     */
    fun getPositionForDate(date: String): Int {
        return try {
            val target = Calendar.getInstance().apply {
                time = sdf.parse(date)!!
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffMs = target.timeInMillis - todayMidnight.timeInMillis
            // Use round to handle potential DST issues better than floor division
            val diffDays = Math.round(diffMs.toDouble() / (1_000 * 60 * 60 * 24)).toInt()
            (INITIAL_POSITION + diffDays).coerceIn(0, MAX_PAGES - 1)
        } catch (e: Exception) {
            INITIAL_POSITION
        }
    }
}
