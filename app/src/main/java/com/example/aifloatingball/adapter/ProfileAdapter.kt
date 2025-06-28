package com.example.aifloatingball.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptProfile

class ProfileAdapter(
    private var profiles: MutableList<PromptProfile>,
    private val onProfileClicked: (PromptProfile) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prompt_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = profiles[position]
        holder.bind(profile, onProfileClicked)
    }

    override fun getItemCount(): Int = profiles.size

    fun updateData(newProfiles: List<PromptProfile>) {
        this.profiles.clear()
        this.profiles.addAll(newProfiles)
        notifyDataSetChanged()
    }

    class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.profile_name)
        private val defaultTextColor = nameTextView.currentTextColor

        fun bind(
            profile: PromptProfile,
            onProfileClicked: (PromptProfile) -> Unit
        ) {
            nameTextView.text = profile.name
            itemView.setOnClickListener { onProfileClicked(profile) }

            if (profile.isSelected) {
                itemView.setBackgroundColor(itemView.context.getColor(R.color.selected_item_background))
                nameTextView.setTextColor(Color.BLACK)
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT)
                nameTextView.setTextColor(defaultTextColor)
            }
        }
    }
} 