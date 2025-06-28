package com.example.aifloatingball.settings

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.FaviconLoader
import com.google.android.material.switchmaterial.SwitchMaterial

class SearchEngineAdapter(
    private var searchEngines: MutableList<SearchEngine>,
    private val settingsManager: SettingsManager,
    private val onItemClick: (SearchEngine) -> Unit
) : RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

    private val enabledEngines = settingsManager.getEnabledSearchEngines().toMutableSet()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val engineIcon: ImageView = view.findViewById(R.id.engine_icon)
        val engineName: TextView = view.findViewById(R.id.engine_name)
        val engineUrl: TextView = view.findViewById(R.id.engine_url)
        val engineSwitch: SwitchMaterial = view.findViewById(R.id.engine_switch)
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_search_engine, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = searchEngines[position]
        val context = holder.itemView.context

        FaviconLoader.loadIcon(holder.engineIcon, engine.url, R.drawable.ic_web_default)

        if (engine.isCustom) {
            val displayName = engine.displayName
            val suffix = " (自定义)"
            val fullText = "$displayName$suffix"
            val spannable = SpannableString(fullText)

            val secondaryColor = getColorFromAttr(context, android.R.attr.textColorSecondary)

            spannable.setSpan(
                ForegroundColorSpan(secondaryColor),
                displayName.length,
                fullText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            holder.engineName.text = spannable
            holder.engineUrl.text = engine.searchUrl
            holder.engineUrl.isVisible = true
            holder.dragHandle.isVisible = true
            holder.itemView.setOnClickListener {
                onItemClick(engine)
            }
            holder.itemView.isClickable = true
        } else {
            holder.engineName.text = engine.displayName
            holder.engineUrl.isVisible = false
            holder.dragHandle.isVisible = false
            holder.itemView.isClickable = false
        }

        holder.engineSwitch.isChecked = enabledEngines.contains(engine.name)

        holder.engineSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enabledEngines.add(engine.name)
            } else {
                enabledEngines.remove(engine.name)
            }
            settingsManager.saveEnabledSearchEngines(enabledEngines)
        }
    }

    override fun getItemCount() = searchEngines.size

    fun updateData(newEngines: List<SearchEngine>) {
        searchEngines.clear()
        searchEngines.addAll(newEngines)
        notifyDataSetChanged()
    }

    private fun getColorFromAttr(context: Context, @AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }
} 