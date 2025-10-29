package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.utils.FaviconLoader

class GroupMemberAdapter(
    private var members: MutableList<ChatContact>,
    private val onRemoveMember: (ChatContact) -> Unit
) : RecyclerView.Adapter<GroupMemberAdapter.MemberViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(members[position])
    }

    override fun getItemCount(): Int = members.size

    fun updateMembers(newMembers: List<ChatContact>) {
        members.clear()
        members.addAll(newMembers)
        notifyDataSetChanged()
    }

    fun removeMember(member: ChatContact) {
        val position = members.indexOf(member)
        if (position != -1) {
            members.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun addMember(member: ChatContact) {
        if (!members.contains(member)) {
            members.add(member)
            notifyItemInserted(members.size - 1)
        }
    }

    inner class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val memberAvatar: ImageView = itemView.findViewById(R.id.member_avatar)
        private val memberName: TextView = itemView.findViewById(R.id.member_name)
        private val memberStatus: TextView = itemView.findViewById(R.id.member_status)
        private val onlineIndicator: View = itemView.findViewById(R.id.online_indicator)
        private val removeMemberButton: ImageButton = itemView.findViewById(R.id.remove_member_button)

        fun bind(member: ChatContact) {
            memberName.text = member.name
            
            // 设置AI头像
            // 临时专线使用Material风格的首字母L图标
            when {
                member.name == "临时专线" -> {
                    memberAvatar.setImageResource(R.drawable.ic_temp_ai_letter_l)
                    memberAvatar.clearColorFilter()
                }
                else -> {
                    val apiUrl = member.customData["api_url"] ?: ""
                    if (apiUrl.isNotEmpty()) {
                        FaviconLoader.loadIcon(memberAvatar, apiUrl, R.drawable.ic_smart_toy)
                    } else {
                        FaviconLoader.loadAIEngineIcon(memberAvatar, member.name, R.drawable.ic_smart_toy)
                    }
                }
            }

            // 检查API配置状态
            val apiKey = member.customData["api_key"] ?: ""
            val isConfigured = apiKey.isNotEmpty()
            
            if (isConfigured) {
                memberStatus.text = "已配置"
                memberStatus.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                onlineIndicator.setBackgroundColor(itemView.context.getColor(android.R.color.holo_green_light))
            } else {
                memberStatus.text = "未配置"
                memberStatus.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
                onlineIndicator.setBackgroundColor(itemView.context.getColor(android.R.color.holo_red_light))
            }

            // 移除按钮点击事件
            removeMemberButton.setOnClickListener {
                onRemoveMember(member)
            }
        }
    }
}