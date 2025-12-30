package com.example.myapplication

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.graphics.Color
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.core.content.ContextCompat

class CommandAdapter(
    context: Context,
    private val commands: List<String>,
    private val onDeleteClicked: (commandName: String) -> Unit,
    private val isNewCommand: (commandName: String) -> Boolean
) : ArrayAdapter<String>(context, 0, commands) {

    private var activeTab: String? = null

    fun setActiveTab(tabName: String?) {
        activeTab = tabName
        notifyDataSetChanged()
    }

    private fun getColorFromTheme(attrRes: Int): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_command, parent, false)

        val commandNameTextView = view.findViewById<TextView>(R.id.command_name_text_view)
        val deleteButton = view.findViewById<ImageView>(R.id.delete_command_image_view)

        val commandName = getItem(position)

        commandNameTextView.text = commandName

        deleteButton.setOnClickListener {
            commandName?.let {
                onDeleteClicked(it)
            }
        }

        // Highlight active tab
        if (commandName == activeTab) {
            view.setBackgroundColor(ContextCompat.getColor(context, R.color.active_tab_background))
            commandNameTextView.setTextColor(ContextCompat.getColor(context, R.color.active_tab_text))
        } else {
            view.setBackgroundColor(getColorFromTheme(com.google.android.material.R.attr.colorSurface))
            commandNameTextView.setTextColor(getColorFromTheme(com.google.android.material.R.attr.colorOnSurface))
        }

        // Blinking for new commands
        if (commandName != null && isNewCommand(commandName)) {
            val anim: Animation = AlphaAnimation(0.0f, 1.0f)
            anim.duration = 500 // 0.5 seconds
            anim.startOffset = 20
            anim.repeatMode = Animation.REVERSE
            anim.repeatCount = Animation.INFINITE
            view.startAnimation(anim)
        } else {
            view.clearAnimation()
        }

        return view
    }
}
