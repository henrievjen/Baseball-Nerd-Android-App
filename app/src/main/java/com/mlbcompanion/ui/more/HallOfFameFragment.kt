package com.baseballnerd.app.ui.more

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.baseballnerd.app.R
import com.baseballnerd.app.data.model.HallOfFameRow
import com.baseballnerd.app.data.repository.GamesRepository
import com.baseballnerd.app.databinding.FragmentHallOfFameBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class HallOfFameFragment : Fragment() {

    private var _binding: FragmentHallOfFameBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HallOfFameViewModel by viewModels {
        HallOfFameViewModelFactory(GamesRepository())
    }

    private var spinnerInitialized = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHallOfFameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        setupYearSpinner()

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                binding.scrollContent.visibility = View.GONE
                binding.tvError.visibility = View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            binding.tvError.visibility = if (err != null) View.VISIBLE else View.GONE
            binding.tvError.text = err ?: ""
        }

        viewModel.hofRows.observe(viewLifecycleOwner) { rows ->
            renderHof(rows)
        }
    }

    private fun setupYearSpinner() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        // HOF elections began in 1936
        val years = (currentYear downTo 1936).map { it.toString() }

        val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner_year, years)
        adapter.setDropDownViewResource(R.layout.item_spinner_year_dropdown)
        binding.spinnerYear.adapter = adapter

        spinnerInitialized = false
        binding.spinnerYear.setSelection(0, false)
        spinnerInitialized = true

        binding.spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!spinnerInitialized) return
                viewModel.loadHof(years[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        viewModel.loadHof(years[0])
    }

    private fun renderHof(rows: List<HallOfFameRow>) {
        val container = binding.layoutHofContent
        container.removeAllViews()

        val inductedOnly = rows.filter { it.inducted == "Y" }

        if (inductedOnly.isEmpty()) {
            binding.scrollContent.visibility = View.GONE
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = "No players were inducted in this year's election."
            return
        }

        binding.tvError.visibility = View.GONE
        binding.scrollContent.visibility = View.VISIBLE

        inductedOnly.forEach { row ->
            container.addView(buildHofRow(row))
        }
    }

    private fun buildHofRow(row: HallOfFameRow): View {
        val playerId = row.person?.id
        val isClickable = playerId != null

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (isClickable) {
                this.isClickable = true
                isFocusable = true
                val tv = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                setBackgroundResource(tv.resourceId)
                setOnClickListener {
                    findNavController().navigate(
                        R.id.playerStatsFragment,
                        bundleOf("playerId" to playerId)
                    )
                }
            }
        }

        // Photo
        val photo = android.widget.ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply {
                marginEnd = dpToPx(16)
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(context, R.drawable.shape_team_circle)
            clipToOutline = true
        }
        if (playerId != null) {
            photo.load("https://midfield.mlbstatic.com/v1/people/$playerId/spots/120") {
                crossfade(true)
                placeholder(R.drawable.ic_baseball_placeholder)
                error(R.drawable.ic_baseball_placeholder)
            }
        } else {
            photo.setImageResource(R.drawable.ic_baseball_placeholder)
        }
        layout.addView(photo)

        // Text
        val textLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textLayout.addView(TextView(requireContext()).apply {
            text = row.person?.fullName ?: "Unknown Player"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        })

        textLayout.addView(TextView(requireContext()).apply {
            text = listOfNotNull(row.category, row.votedBy).joinToString(" · ")
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        })

        layout.addView(textLayout)

        // Percentage or icon
        row.percentage?.let { pct ->
            layout.addView(TextView(requireContext()).apply {
                text = String.format("%.1f%%", pct)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.mlb_gold))
                gravity = Gravity.END
            })
        }

        return layout
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class HallOfFameViewModel(private val repository: GamesRepository) : ViewModel() {

    private val _hofRows = androidx.lifecycle.MutableLiveData<List<HallOfFameRow>>()
    val hofRows: androidx.lifecycle.LiveData<List<HallOfFameRow>> = _hofRows

    private val _loading = androidx.lifecycle.MutableLiveData<Boolean>()
    val loading: androidx.lifecycle.LiveData<Boolean> = _loading

    private val _error = androidx.lifecycle.MutableLiveData<String?>()
    val error: androidx.lifecycle.LiveData<String?> = _error

    fun loadHof(year: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            // Note: We'll need to update GamesRepository to support year filtering for HOF
            runCatching { repository.getHallOfFame(year) }
                .onSuccess { _hofRows.value = it }
                .onFailure { _error.value = "Could not load Hall of Fame data for $year." }
            _loading.value = false
        }
    }
}

class HallOfFameViewModelFactory(private val repository: GamesRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HallOfFameViewModel(repository) as T
}
