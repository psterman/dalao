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
    private var profiles: MutableList<PromptProfile> = mutableListOf(),
    private val onProfileClicked: (PromptProfile) -> Unit,
    private val onProfileLongClicked: (PromptProfile, Int) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    private var activeProfileId: String? = null
    var onProfileDeleted: ((PromptProfile) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prompt_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        if (position < profiles.size) {
            val profile = profiles[position]
            val isActive = profile.id == activeProfileId
            holder.bind(profile, isActive, onProfileClicked)
        }
    }

    override fun getItemCount(): Int = profiles.size

    fun updateData(newProfiles: List<PromptProfile>) {
        android.util.Log.d("ProfileAdapter", "updateData调用，新档案数量: ${newProfiles.size}")
        profiles.clear()
        profiles.addAll(newProfiles)
        android.util.Log.d("ProfileAdapter", "updateData完成，当前profiles.size: ${profiles.size}")
        notifyDataSetChanged()
        android.util.Log.d("ProfileAdapter", "notifyDataSetChanged已调用")
    }

    fun setActiveProfileId(profileId: String?) {
        this.activeProfileId = profileId
        notifyDataSetChanged()
    }
    
    fun addProfile(profile: PromptProfile) {
        profiles.add(profile)
        notifyItemInserted(profiles.size - 1)
    }

    class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.profile_name)
        private val defaultTextColor = nameTextView.currentTextColor

        fun bind(
            profile: PromptProfile,
            isActive: Boolean,
            onProfileClicked: (PromptProfile) -> Unit
        ) {
            // 设置档案名称
            nameTextView.text = profile.name
            
            // 设置点击事件
            itemView.setOnClickListener { onProfileClicked(profile) }
            
            // 设置长按事件
            itemView.setOnLongClickListener {
                android.util.Log.d("ProfileAdapter", "长按档案: ${profile.name}")
                android.widget.Toast.makeText(itemView.context, "长按删除或移动档案", android.widget.Toast.LENGTH_SHORT).show()
                true
            }

            // 设置选中状态
            if (isActive) {
                itemView.setBackgroundColor(itemView.context.getColor(R.color.selected_item_background))
                nameTextView.setTextColor(Color.BLACK)
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT)
                nameTextView.setTextColor(defaultTextColor)
            }
        }
    }
} 