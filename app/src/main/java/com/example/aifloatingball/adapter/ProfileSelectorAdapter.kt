package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptProfile

class ProfileSelectorAdapter(
    private var profiles: List<PromptProfile>,
    private var selectedProfileId: String?,
    private val onProfileSelected: (PromptProfile) -> Unit
) : RecyclerView.Adapter<ProfileSelectorAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileName: TextView = itemView.findViewById(R.id.profile_name)
        val profileDescription: TextView = itemView.findViewById(R.id.profile_description)
        val radioButton: RadioButton = itemView.findViewById(R.id.radio_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile_selector, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val profile = profiles[position]
        
        holder.profileName.text = profile.name
        holder.profileDescription.text = profile.persona?.takeIf { it.isNotBlank() } 
            ?: "未设置角色"
        
        holder.radioButton.isChecked = profile.id == selectedProfileId
        
        holder.itemView.setOnClickListener {
            selectedProfileId = profile.id
            onProfileSelected(profile)
            notifyDataSetChanged()
        }
        
        holder.radioButton.setOnClickListener {
            selectedProfileId = profile.id
            onProfileSelected(profile)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = profiles.size

    fun updateProfiles(newProfiles: List<PromptProfile>, newSelectedId: String?) {
        profiles = newProfiles
        selectedProfileId = newSelectedId
        notifyDataSetChanged()
    }

    fun getSelectedProfile(): PromptProfile? {
        return profiles.find { it.id == selectedProfileId }
    }
} 