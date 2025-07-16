package com.lended.h2opal.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lended.h2opal.R
import com.lended.h2opal.models.RemainderModel

class RemainderAdapter(private val reminders: MutableList<RemainderModel>) :
    RecyclerView.Adapter<RemainderAdapter.ReminderViewHolder>() {

    inner class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val quoteText: TextView = itemView.findViewById(R.id.quoteForHydrate)
        val levelText: TextView = itemView.findViewById(R.id.hydartionLvl)
        val timeText: TextView = itemView.findViewById(R.id.timings)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.remainder_card_layout, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val item = reminders[position]
        holder.quoteText.text = item.quote
        holder.levelText.text = item.level
        holder.timeText.text = item.time
    }

    override fun getItemCount(): Int = reminders.size

    fun removeItem(position: Int) {
        reminders.removeAt(position)
        notifyItemRemoved(position)
    }

    fun addItem(item: RemainderModel) {
        reminders.add(0, item)
        notifyItemInserted(0)
    }

    fun getList(): List<RemainderModel> = reminders
}
