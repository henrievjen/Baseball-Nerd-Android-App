package com.baseballnerd.app.ui.stats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.baseballnerd.app.R
import com.baseballnerd.app.data.model.StatLeader
import com.baseballnerd.app.databinding.ItemStatLeaderBinding

class LeadersAdapter(private val onItemClick: (StatLeader) -> Unit) : RecyclerView.Adapter<LeadersAdapter.ViewHolder>() {
    private var items = listOf<StatLeader>()

    fun submitList(newList: List<StatLeader>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStatLeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(
        private val binding: ItemStatLeaderBinding,
        private val onItemClick: (StatLeader) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(leader: StatLeader) {
            binding.tvRank.text = leader.rank.toString()
            binding.tvName.text = leader.person?.fullName ?: "Unknown"
            binding.tvTeam.text = leader.team?.name ?: ""
            binding.tvValue.text = leader.value
            
            val personId = leader.person?.id
            if (personId != null) {
                binding.ivPhoto.load("https://midfield.mlbstatic.com/v1/people/$personId/spots/120") {
                    crossfade(true)
                    placeholder(R.drawable.ic_baseball_placeholder)
                    error(R.drawable.ic_baseball_placeholder)
                }
            } else {
                binding.ivPhoto.setImageResource(R.drawable.ic_baseball_placeholder)
            }

            binding.root.setOnClickListener { onItemClick(leader) }
        }
    }
}
