package com.baseballnerd.app.ui.scores

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.baseballnerd.app.R
import com.baseballnerd.app.databinding.ItemDateTabBinding

class DateTabsAdapter(
    private val viewModel: ScoresViewModel,
    private val onDateClick: (Int) -> Unit
) : RecyclerView.Adapter<DateTabsAdapter.DateTabViewHolder>() {

    private var selectedPosition: Int = ScoresViewModel.INITIAL_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateTabViewHolder {
        val binding = ItemDateTabBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DateTabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DateTabViewHolder, position: Int) {
        val dateTab = viewModel.getDateForPosition(position)
        holder.bind(dateTab, position == selectedPosition)
        holder.itemView.setOnClickListener {
            onDateClick(position)
        }
    }

    override fun getItemCount(): Int = ScoresViewModel.MAX_PAGES

    fun setSelectedPosition(position: Int) {
        val oldPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
    }

    class DateTabViewHolder(private val binding: ItemDateTabBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(dateTab: DateTab, isSelected: Boolean) {
            binding.tvDateLabel.text = dateTab.label
            val context = binding.root.context
            if (isSelected) {
                binding.tvDateLabel.setTextColor(ContextCompat.getColor(context, R.color.white))
                binding.tvDateLabel.setBackgroundResource(R.drawable.bg_date_tab_selected)
            } else {
                binding.tvDateLabel.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                binding.tvDateLabel.background = null
            }
        }
    }
}
