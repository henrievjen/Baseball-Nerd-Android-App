package com.baseballnerd.app.ui.more

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
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
import com.baseballnerd.app.data.model.AwardRecipient
import com.baseballnerd.app.data.model.AwardsUiGroup
import com.baseballnerd.app.data.repository.GamesRepository
import com.baseballnerd.app.databinding.FragmentAwardsBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class AwardsFragment : Fragment() {

    private var _binding: FragmentAwardsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AwardsViewModel by viewModels {
        AwardsViewModelFactory(GamesRepository())
    }

    private var spinnerInitialized = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAwardsBinding.inflate(inflater, container, false)
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

        viewModel.awards.observe(viewLifecycleOwner) { groups ->
            renderAwards(groups)
        }
    }

    private fun setupYearSpinner() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
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
                viewModel.loadAwards(years[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        viewModel.loadAwards(years[0])
    }

    private fun navigateToPlayer(playerId: Int) {
        findNavController().navigate(
            R.id.action_awardsFragment_to_playerStatsFragment,
            bundleOf("playerId" to playerId)
        )
    }

    private fun renderAwards(groups: List<AwardsUiGroup>) {
        val container = binding.layoutAwardsContent
        container.removeAllViews()

        if (groups.isEmpty()) {
            binding.scrollContent.visibility = View.GONE
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = "No award data available for this season."
            return
        }

        binding.tvError.visibility = View.GONE
        binding.scrollContent.visibility = View.VISIBLE

        groups.forEach { group ->
            addSectionHeader(container, group.label)

            group.recipients.forEach { recipient ->
                container.addView(buildRecipientRow(recipient))
            }

            addDivider(container)
        }
    }

    private fun buildRecipientRow(recipient: AwardRecipient): View {
        // Fallback chain for identifying the person object
        val person = recipient.person 
            ?: recipient.results?.firstOrNull()?.person
        
        val playerId = person?.id
        val isClickable = playerId != null

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
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
                setOnClickListener { navigateToPlayer(playerId!!) }
            }
        }

        // Player photo
        val photoSize = dpToPx(46)
        val photo = android.widget.ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(photoSize, photoSize).apply {
                marginEnd = dpToPx(12)
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
        row.addView(photo)

        // Text block
        val textBlock = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Priority 1: recipientName/recipient (Directly from AwardRecipient)
        // Priority 2: person's fullName/name details (From hydrated person)
        val displayName = recipient.recipientName
            ?: recipient.recipient
            ?: person?.fullName
            ?: person?.nameFirstLast
            ?: person?.name
            ?: "Unknown"

        textBlock.addView(TextView(requireContext()).apply {
            text = displayName
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        })

        val subParts = mutableListOf<String>()
        recipient.team?.name?.let { subParts.add(it) }

        if (subParts.isNotEmpty()) {
            textBlock.addView(TextView(requireContext()).apply {
                text = subParts.joinToString(" ")
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, dpToPx(2), 0, 0)
            })
        }

        row.addView(textBlock)

        row.addView(TextView(requireContext()).apply {
            text = "🏆"
            textSize = 18f
            setPadding(dpToPx(8), 0, 0, 0)
        })

        return row
    }

    private fun addSectionHeader(container: LinearLayout, title: String) {
        container.addView(TextView(requireContext()).apply {
            text = title.uppercase()
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.mlb_red))
            setPadding(dpToPx(16), dpToPx(20), dpToPx(16), dpToPx(4))
            letterSpacing = 0.08f
        })
    }

    private fun addDivider(container: LinearLayout) {
        container.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
            ).apply { setMargins(0, dpToPx(4), 0, 0) }
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
        })
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class AwardsViewModel(private val repository: GamesRepository) : ViewModel() {

    private val _awards = androidx.lifecycle.MutableLiveData<List<AwardsUiGroup>>()
    val awards: androidx.lifecycle.LiveData<List<AwardsUiGroup>> = _awards

    private val _loading = androidx.lifecycle.MutableLiveData<Boolean>()
    val loading: androidx.lifecycle.LiveData<Boolean> = _loading

    private val _error = androidx.lifecycle.MutableLiveData<String?>()
    val error: androidx.lifecycle.LiveData<String?> = _error

    private var lastSeason: String? = null

    fun loadAwards(season: String) {
        if (season == lastSeason) return
        lastSeason = season
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching { repository.getAwardsForSeason(season) }
                .onSuccess { _awards.value = it }
                .onFailure { _error.value = "Could not load award winners for $season." }
            _loading.value = false
        }
    }
}

class AwardsViewModelFactory(private val repository: GamesRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AwardsViewModel(repository) as T
}
