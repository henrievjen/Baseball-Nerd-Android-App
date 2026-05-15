package com.baseballnerd.app.ui.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.baseballnerd.app.databinding.FragmentSettingsBinding
import com.baseballnerd.app.util.SettingsManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        setupDarkModeSwitch()
        setupScoreViewSpinner()
    }

    private fun setupDarkModeSwitch() {
        val settings = SettingsManager.get(requireContext())

        // Set initial state from prefs without triggering the listener
        binding.switchDarkMode.isChecked = settings.darkMode

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            settings.darkMode = isChecked
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            // Recreate the activity so the new theme is applied immediately
            requireActivity().recreate()
        }
    }

    private fun setupScoreViewSpinner() {
        val options = listOf("List View", "Card View")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerScoreView.adapter = adapter

        val settings = SettingsManager.get(requireContext())
        binding.spinnerScoreView.setSelection(settings.scoreViewType)

        binding.spinnerScoreView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settings.scoreViewType = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
