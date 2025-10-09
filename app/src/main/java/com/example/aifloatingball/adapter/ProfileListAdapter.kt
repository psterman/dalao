package com.example.aifloatingball.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptProfile
import com.google.android.material.card.MaterialCardView

/**
 * 档案列表适配器
 * 用于在AI助手中心显示档案列表
 */
class ProfileListAdapter(
    private val context: Context,
    private var profiles: List<PromptProfile>,
    private var currentProfileId: String?,
    private val onProfileSelected: (PromptProfile) -> Unit
) : RecyclerView.Adapter<ProfileListAdapter.ProfileViewHolder>() {

    class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.card_view)
        val profileAvatar: ImageView = itemView.findViewById(R.id.profile_avatar)
        val profileName: TextView = itemView.findViewById(R.id.profile_name)
        val profileDescription: TextView = itemView.findViewById(R.id.profile_description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_prompt_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = profiles[position]
        val isSelected = profile.id == currentProfileId

        // 设置档案信息
        holder.profileName.text = profile.name
        holder.profileDescription.text = profile.description.ifEmpty { "通用助手" }

        // 设置选中状态
        holder.cardView.isChecked = isSelected

        // 设置点击事件
        holder.cardView.setOnClickListener {
            onProfileSelected(profile)
        }

        // 设置头像
        holder.profileAvatar.setImageResource(R.drawable.ic_person)
    }

    override fun getItemCount(): Int = profiles.size
    
    /**
     * 更新档案列表和当前选中的档案ID
     */
    fun updateProfiles(newProfiles: List<PromptProfile>, newCurrentProfileId: String?) {
        profiles = newProfiles
        currentProfileId = newCurrentProfileId
        notifyDataSetChanged()
    }
}
