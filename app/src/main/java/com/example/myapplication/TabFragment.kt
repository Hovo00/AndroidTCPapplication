package com.example.myapplication

import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class TabFragment : Fragment() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var orderTextView: TextView
    private lateinit var tableLayout: TableLayout
    private var gunInfos: MutableList<GunInfo> = mutableListOf()
    private var targetCommand: TargetCommand? = null

    companion object {
        fun newInstance(command: TargetCommand): TabFragment {
            val fragment = TabFragment()
            fragment.targetCommand = command
            fragment.gunInfos = command.guns.toMutableList()
            return fragment
        }
    }

    private fun getColorFromTheme(attrRes: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    fun getLabel(): String = targetCommand?.targetName ?: ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(getColorFromTheme(android.R.attr.colorBackground))
        }

        orderTextView = TextView(requireContext()).apply {
            text = targetCommand?.orderText ?: ""
            setTypeface(null, Typeface.BOLD)
            textSize = 18f
            setPadding(16, 16, 16, 16)
            setTextColor(getColorFromTheme(com.google.android.material.R.attr.colorOnBackground))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val scroll = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(orderTextView)
        }

        tableLayout = TableLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setStretchAllColumns(true)
        }

        // Set background based on theme
        val nightModeFlags = requireContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            tableLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.table_background_dark))
        } else {
            tableLayout.setBackgroundColor(getColorFromTheme(com.google.android.material.R.attr.colorSurface))
        }

        if (targetCommand?.commandType == "af_correction") {
            rootLayout.addView(tableLayout)
            rootLayout.addView(scroll)
        } else {
            rootLayout.addView(scroll)
            rootLayout.addView(tableLayout)
        }

        buildTable()
        return rootLayout
    }

    private fun buildTable() {
        tableLayout.removeAllViews()
        if (gunInfos.isEmpty()) return

        val headerRow = TableRow(requireContext())
        val headers = listOf("Done", "Լց․", "Նշ․", "Մկ․", "Հ.ու․")
        headers.forEach { text ->
            val tv = TextView(requireContext()).apply {
                setTextColor(getColorFromTheme(com.google.android.material.R.attr.colorOnPrimary))
                setBackgroundColor(getColorFromTheme(com.google.android.material.R.attr.colorPrimary))
                setTypeface(null, Typeface.BOLD)
                setPadding(16, 8, 16, 8)
                textSize = 16f
                this.text = text
            }
            headerRow.addView(tv)
        }
        tableLayout.addView(headerRow)

        gunInfos.forEachIndexed { index, info ->
            val row = TableRow(requireContext())
            val bgColor = if (index % 2 == 0) {
                getColorFromTheme(com.google.android.material.R.attr.colorSurface)
            } else {
                getColorFromTheme(com.google.android.material.R.attr.colorSurfaceContainer)
            }
            val textColor = getColorFromTheme(com.google.android.material.R.attr.colorOnSurface)

            val checkBox = CheckBox(requireContext()).apply {
                isChecked = info.done
                setBackgroundColor(bgColor)
                setOnCheckedChangeListener { _, isChecked ->
                    info.done = isChecked
                }
            }
            row.addView(checkBox)

            listOf(info.lts.toString(), info.ns.toString(), info.mk, info.hu).forEach { value ->
                val tv = TextView(requireContext()).apply {
                    this.setTextColor(textColor)
                    this.setBackgroundColor(bgColor)
                    setPadding(16, 8, 16, 8)
                    textSize = 14f
                    text = value
                }
                row.addView(tv)
            }

            tableLayout.addView(row)
        }
    }
    fun updateBfCommand(command: TargetCommand) {
        targetCommand = command
        gunInfos = command.guns.toMutableList()
        activity?.runOnUiThread {
            orderTextView.text = command.orderText ?: ""
            buildTable()
        }
    }

    fun updateSfCommand(command: TargetCommand) {
        targetCommand = command
        gunInfos = command.guns.toMutableList()
        activity?.runOnUiThread {
            orderTextView.text = command.orderText ?: ""
            buildTable()
        }
    }

    fun updateAfCommand(command: TargetCommand) {
        targetCommand = command
        gunInfos = command.guns.toMutableList()
        activity?.runOnUiThread {
            orderTextView.text = command.orderText ?: ""
            buildTable()
        }
    }

    fun updateBfCorrectionCommand(command: TargetCommand) {
        targetCommand = command
        gunInfos = command.guns.toMutableList()
        activity?.runOnUiThread {
            orderTextView.text = command.orderText ?: ""
            buildTable()
        }
    }

    fun updateSfCorrectionCommand(command: TargetCommand) {
        targetCommand = command
        gunInfos = command.guns.toMutableList()
        activity?.runOnUiThread {
            orderTextView.text = command.orderText ?: ""
            buildTable()
        }
    }

    fun updateAfCorrectionCommand(command: TargetCommand) {
        targetCommand = command
        gunInfos = command.guns.toMutableList()
        activity?.runOnUiThread {
            val currentText = orderTextView.text.toString()
            val newOrderText = command.orderText ?: ""
            if (newOrderText.isNotEmpty() && !currentText.contains(newOrderText)) {
                orderTextView.text = if (currentText.isEmpty()) newOrderText else "$currentText\n$newOrderText"
            }
        }
    }

    fun updateTable(newInfos: List<GunInfo>) {
        gunInfos = newInfos.toMutableList()
        activity?.runOnUiThread {
            buildTable()
        }
    }

    fun appendGunInfo(info: GunInfo) {
        gunInfos.add(info)
        activity?.runOnUiThread {
            buildTable()
        }
    }

    fun updateOrderText(text: String) {
        targetCommand = targetCommand?.copy(orderText = text)
        activity?.runOnUiThread {
            orderTextView.text = text
        }
    }
}
