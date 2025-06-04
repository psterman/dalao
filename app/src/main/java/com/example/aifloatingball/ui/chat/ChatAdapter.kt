package com.example.aifloatingball.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.manager.ChatManager.ChatMessage

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userMessageTextView: TextView = itemView.findViewById(R.id.text_message_user)
        val aiMessageTextView: TextView = itemView.findViewById(R.id.text_message_ai)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        if (message.role == "user") {
            holder.userMessageTextView.text = message.content
            holder.userMessageTextView.visibility = View.VISIBLE
            holder.aiMessageTextView.visibility = View.GONE
        } else {
            if (message.isLoading) {
                holder.aiMessageTextView.text = "正在思考..."
            } else {
                holder.aiMessageTextView.text = message.content
            }
            holder.aiMessageTextView.visibility = View.VISIBLE
            holder.userMessageTextView.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = messages.size
} 