package com.bandlightconnect.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class AutomationAdapter(
    private val automationList: MutableList<Automation>,
    private val foldersMap: Map<String, String>,
    private val onItemClicked: (Automation, Int) -> Unit,
    private val onListChanged: () -> Unit
) : RecyclerView.Adapter<AutomationAdapter.AutomationViewHolder>() {

    class AutomationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textName: TextView = itemView.findViewById(R.id.textNomeAutomacao)
        val textFolder: TextView = itemView.findViewById(R.id.textFolderName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AutomationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_automacao, parent, false)
        return AutomationViewHolder(view)
    }

    override fun onBindViewHolder(holder: AutomationViewHolder, position: Int) {
        val currentItem = automationList[position]
        holder.textName.text = currentItem.name

        if (currentItem.folderId != null && foldersMap.containsKey(currentItem.folderId)) {
            holder.textFolder.visibility = View.VISIBLE
            holder.textFolder.text = "📁 ${foldersMap[currentItem.folderId]}"
        } else {
            holder.textFolder.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClicked(currentItem, holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = automationList.size

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(automationList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(automationList, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onListChanged()
    }
}