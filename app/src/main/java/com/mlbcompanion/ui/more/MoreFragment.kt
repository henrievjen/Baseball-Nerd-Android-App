package com.baseballnerd.app.ui.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.baseballnerd.app.R
import com.baseballnerd.app.databinding.FragmentMoreBinding

class MoreFragment : Fragment() {

    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.menuItemSettings.setOnClickListener {
            findNavController().navigate(R.id.action_moreFragment_to_settingsFragment)
        }
        binding.menuItemPositions.setOnClickListener {
            findNavController().navigate(R.id.action_moreFragment_to_positionsFragment)
        }
        binding.menuItemWhosHot.setOnClickListener {
            findNavController().navigate(R.id.action_moreFragment_to_whosHotFragment)
        }
        binding.menuItemAwards.setOnClickListener {
            findNavController().navigate(R.id.action_moreFragment_to_awardsFragment)
        }
        binding.menuItemAbout.setOnClickListener {
            findNavController().navigate(R.id.action_moreFragment_to_aboutFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
