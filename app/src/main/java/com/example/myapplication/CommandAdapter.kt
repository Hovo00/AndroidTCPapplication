package com.example.myapplication

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class CommandAdapter(
    context: Context,
    private val commands: List<String>,
    private val onDeleteClicked: (commandName: String) -> Unit
) : ArrayAdapter<String>(context, 0, commands) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_command, parent, false)

        val commandNameTextView = view.findViewById<TextView>(R.id.command_name_text_view)
        val deleteButton = view.findViewById<ImageView>(R.id.delete_command_image_view)

        // getItem(position) is the way to get the data item for this position with ArrayAdapter
        val commandName = getItem(position)

        commandNameTextView.text = commandName

        deleteButton.setOnClickListener {
            // Ensure commandName is not null before invoking the callback
            commandName?.let {
                onDeleteClicked(it)
            }
        }
        return view
    }
}