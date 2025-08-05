package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.AddRSSContactActivity
import com.example.aifloatingball.R
import com.google.android.material.button.MaterialButton

/**
 * 预设RSS源适配器
 */
class PresetRSSAdapter(
    private val onRSSClick: (AddRSSContactActivity.PresetRSS) -> Unit
) : RecyclerView.Adapter<PresetRSSAdapter.RSSViewHolder>() {

    private var presetRSS = listOf<AddRSSContactActivity.PresetRSS>()

    /**
     * 更新RSS列表
     */
    fun updateRSS(rss: List<AddRSSContactActivity.PresetRSS>) {
        this.presetRSS = rss
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RSSViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preset_rss, parent, false)
        return RSSViewHolder(view)
    }

    override fun onBindViewHolder(holder: RSSViewHolder, position: Int) {
        holder.bind(presetRSS[position])
    }

    override fun getItemCount(): Int = presetRSS.size

    inner class RSSViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rssIcon: ImageView = itemView.findViewById(R.id.rss_icon)
        private val rssName: TextView = itemView.findViewById(R.id.rss_name)
        private val rssDescription: TextView = itemView.findViewById(R.id.rss_description)
        private val addButton: MaterialButton = itemView.findViewById(R.id.add_button)

        fun bind(presetRSS: AddRSSContactActivity.PresetRSS) {
            rssName.text = presetRSS.name
            rssDescription.text = presetRSS.description

            addButton.setOnClickListener {
                onRSSClick(presetRSS)
            }

            // 整个卡片也可以点击
            itemView.setOnClickListener {
                onRSSClick(presetRSS)
            }
        }
    }
} 