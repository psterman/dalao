package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.AIEngine
import com.example.aifloatingball.R

class CardLayoutAdapter(
    private val engines: List<AIEngine>,
    private val onCardClick: (Int) -> Unit,
    private val onCardLongClick: (View, Int) -> Boolean
) : RecyclerView.Adapter<CardLayoutAdapter.CardViewHolder>() {

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val engineIcon: ImageView = view.findViewById(R.id.engine_icon)
        val engineName: TextView = view.findViewById(R.id.engine_name)
        val webView: WebView = view.findViewById(R.id.web_view)
        val contentArea: View = view.findViewById(R.id.content_area)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_ai_engine, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val engine = engines[position]
        
        holder.engineIcon.setImageResource(engine.iconResId)
        holder.engineName.text = engine.name
        
        holder.itemView.setOnClickListener { onCardClick(position) }
        holder.itemView.setOnLongClickListener { view -> onCardLongClick(view, position) }
    }

    override fun getItemCount() = engines.size
} 