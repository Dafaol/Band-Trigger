package com.bandlightconnect.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

sealed class UiItem {
    data class FolderItem(val folder: Folder) : UiItem()
    data class AutomationItem(val automation: Automation) : UiItem()
}

class AutomationAdapter(
    private var items: MutableList<UiItem>,
    private val onFolderClicked: (Folder) -> Unit,
    private val onFolderEditClicked: (Folder) -> Unit,
    private val onAutomationClicked: (Automation) -> Unit,
    private val onListReordered: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_FOLDER = 1
        const val TYPE_AUTOMATION = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is UiItem.FolderItem -> TYPE_FOLDER
            is UiItem.AutomationItem -> TYPE_AUTOMATION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOLDER) {
            FolderViewHolder(inflater.inflate(R.layout.item_folder, parent, false))
        } else {
            AutomationViewHolder(inflater.inflate(R.layout.item_automacao, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is UiItem.FolderItem -> {
                val folderHolder = holder as FolderViewHolder
                folderHolder.textName.text = item.folder.name

                folderHolder.itemView.setOnClickListener { onFolderClicked(item.folder) }
                folderHolder.btnOptions.setOnClickListener { onFolderEditClicked(item.folder) }
            }
            is UiItem.AutomationItem -> {
                val autoHolder = holder as AutomationViewHolder
                autoHolder.textName.text = item.automation.name
                autoHolder.textFolder.visibility = View.GONE

                autoHolder.itemView.setOnClickListener { onAutomationClicked(item.automation) }
            }
        }
    }

    override fun getItemCount() = items.size

    fun getItems() = items

    fun updateData(newItems: List<UiItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        // TRAVA REMOVIDA! Agora você pode arrastar automações para cima ou para baixo de pastas livremente!
        val fromItem = items[fromPosition]
        val toItem = items[toPosition]

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) Collections.swap(items, i, i + 1)
        } else {
            for (i in fromPosition downTo toPosition + 1) Collections.swap(items, i, i - 1)
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(R.id.textFolderName)
        val ivDragHandle: ImageView = view.findViewById(R.id.ivDragHandle)
        val btnOptions: ImageButton = view.findViewById(R.id.btnFolderOptions)
    }
    class AutomationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(R.id.textNomeAutomacao)
        val textFolder: TextView = view.findViewById(R.id.textFolderName)
        val ivDragHandle: ImageView = view.findViewById(R.id.ivDragHandle)
    }
}