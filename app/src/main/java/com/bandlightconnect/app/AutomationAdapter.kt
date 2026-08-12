package com.bandlightconnect.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AutomationAdapter(
    private val automationList: List<Automation>,
    private val onItemClicked: (Automation, Int) -> Unit
) : RecyclerView.Adapter<AutomationAdapter.AutomationViewHolder>() {

    class AutomationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textName: TextView = itemView.findViewById(R.id.textNomeAutomacao)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AutomationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_automacao, parent, false)
        return AutomationViewHolder(view)
    }

    override fun onBindViewHolder(holder: AutomationViewHolder, position: Int) {
        val currentItem = automationList[position]
        holder.textName.text = currentItem.name

        // handle item click
        holder.itemView.setOnClickListener {
            onItemClicked(currentItem, position)
        }
    }

    override fun getItemCount(): Int = automationList.size
}