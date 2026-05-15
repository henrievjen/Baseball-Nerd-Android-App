package com.baseballnerd.app.ui.scores

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.datepicker.MaterialDatePicker
import com.baseballnerd.app.databinding.FragmentScoresBinding
import java.text.SimpleDateFormat
import java.util.*

class ScoresFragment : Fragment() {

    private var _binding: FragmentScoresBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScoresViewModel by viewModels()
    private lateinit var dateTabsAdapter: DateTabsAdapter

    // Used to convert the MaterialDatePicker's UTC-midnight millis → "yyyy-MM-dd"
    private val utcSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScoresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDateTabs()
        setupViewPager()
        setupCalendarButton()
        setupTodayButton()
    }

    override fun onResume() {
        super.onResume()
        // Refresh "Today" anchor in case the date changed while app was in background
        if (viewModel.refreshToday()) {
            // If date changed, we need to rebuild the adapter and tabs to reflect new "Today" position
            setupDateTabs()
            setupViewPager()
        }
    }

    // ── Today button ─────────────────────────────────────────────────────────

    private fun setupTodayButton() {
        binding.btnToday.setOnClickListener {
            binding.viewPager.setCurrentItem(ScoresViewModel.INITIAL_POSITION, true)
        }
    }

    // ── Calendar button ───────────────────────────────────────────────────────

    private fun setupCalendarButton() {
        binding.btnCalendar.setOnClickListener {
            // Seed the picker at the currently selected date so it opens there
            val currentDate = viewModel.selectedDate.value
            val initialMs = if (currentDate != null) {
                try {
                    // Parse local date, then express as UTC midnight for the picker
                    val localCal = Calendar.getInstance().apply {
                        time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(currentDate)!!
                    }
                    // MaterialDatePicker works in UTC millis. Use UTC midnight for the
                    // same calendar day to avoid off-by-one from timezone shifts.
                    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        set(Calendar.YEAR, localCal.get(Calendar.YEAR))
                        set(Calendar.MONTH, localCal.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, localCal.get(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    utcCal.timeInMillis
                } catch (e: Exception) {
                    MaterialDatePicker.todayInUtcMilliseconds()
                }
            } else {
                MaterialDatePicker.todayInUtcMilliseconds()
            }

            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(initialMs)
                .build()

            picker.addOnPositiveButtonClickListener { selectionMs ->
                // Convert the UTC-midnight millis back to a local "yyyy-MM-dd" string.
                // Using UTC formatter avoids timezone issues.
                val dateStr = utcSdf.format(Date(selectionMs))
                navigateToDate(dateStr)
            }

            picker.show(parentFragmentManager, "mlb_date_picker")
        }
    }

    /** Navigate the ViewPager (and date tabs) to any arbitrary date string. */
    private fun navigateToDate(date: String) {
        val position = viewModel.getPositionForDate(date)
        binding.viewPager.setCurrentItem(position, true)
        // The ViewPager page-change callback will call updateDateTabsSelection,
        // so we don't need to do it manually here.
    }

    // ── ViewPager ─────────────────────────────────────────────────────────────

    private fun setupViewPager() {
        val adapter = ScorePagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 1

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val dateTab = viewModel.getDateForPosition(position)
                viewModel.selectDate(dateTab.date)
                updateDateTabsSelection(position)
            }
        })

        val currentPosition = viewModel.getPositionForDate(viewModel.selectedDate.value ?: "")
        binding.viewPager.setCurrentItem(currentPosition, false)
    }

    // ── Date tabs ─────────────────────────────────────────────────────────────

    private fun setupDateTabs() {
        dateTabsAdapter = DateTabsAdapter(viewModel) { position ->
            binding.viewPager.currentItem = position
        }
        binding.rvDateTabs.apply {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            adapter = dateTabsAdapter
        }

        val currentPosition = viewModel.getPositionForDate(viewModel.selectedDate.value ?: "")
        dateTabsAdapter.setSelectedPosition(currentPosition)
        binding.rvDateTabs.scrollToPosition(currentPosition)

        // Centering post-layout
        binding.rvDateTabs.post {
            updateDateTabsSelection(currentPosition)
        }
    }

    private fun updateDateTabsSelection(position: Int) {
        dateTabsAdapter.setSelectedPosition(position)

        val layoutManager = binding.rvDateTabs.layoutManager as LinearLayoutManager
        val itemView = layoutManager.findViewByPosition(position)
        if (itemView == null) {
            binding.rvDateTabs.scrollToPosition(position)
        } else {
            val recyclerWidth = binding.rvDateTabs.width
            val offset = (recyclerWidth - itemView.width) / 2
            layoutManager.scrollToPositionWithOffset(position, offset)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Pager adapter ─────────────────────────────────────────────────────────

    private inner class ScorePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

        // Snapshot today's epoch day at adapter-creation time.
        // getItemId() encodes the actual calendar date (not just the position),
        // so if the date rolls over overnight and a new adapter is created, the
        // IDs for every position change — the FragmentManager won't reuse stale
        // fragments that were created with yesterday's dates.
        private val todayEpochDay: Long = viewModel.getTodayEpochDay()

        override fun getItemCount(): Int = ScoresViewModel.MAX_PAGES

        override fun getItemId(position: Int): Long =
            todayEpochDay + (position - ScoresViewModel.INITIAL_POSITION)

        override fun containsItem(itemId: Long): Boolean {
            val position = itemId - todayEpochDay + ScoresViewModel.INITIAL_POSITION
            return position in 0 until ScoresViewModel.MAX_PAGES
        }

        override fun createFragment(position: Int): Fragment {
            val dateTab = viewModel.getDateForPosition(position)
            return ScorePageFragment.newInstance(dateTab.date)
        }
    }
}
