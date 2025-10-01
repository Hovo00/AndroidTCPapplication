package com.example.myapplication

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*
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

    fun getLabel(): String = targetCommand?.targetName ?: ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Order text at the top
        orderTextView = TextView(requireContext()).apply {
            text = targetCommand?.orderText ?: ""
            setTypeface(null, Typeface.BOLD)
            textSize = 18f
            setPadding(16, 16, 16, 16)
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Optional: wrap in ScrollView if text can be long
        val scroll = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(orderTextView)
        }

        // Table below
        tableLayout = TableLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f // fill remaining space
            )
            setBackgroundColor(Color.WHITE)
            setStretchAllColumns(true)
        }

        rootLayout.addView(scroll)
        rootLayout.addView(tableLayout)

        buildTable()
        return rootLayout
    }

    private fun buildTable() {
        tableLayout.removeAllViews()
        if (gunInfos.isEmpty()) return

        // Header row
        val headerRow = TableRow(requireContext())
        val headers = listOf("Done", "Լց․", "Նշ․", "Մկ․", "Հ.ու․")
        headers.forEach { text ->
            val tv = TextView(requireContext()).apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.BLUE)
                setTypeface(null, Typeface.BOLD)
                setPadding(16, 8, 16, 8)
                textSize = 16f
                this.text = text
            }
            headerRow.addView(tv)
        }
        tableLayout.addView(headerRow)

        // Data rows
        gunInfos.forEachIndexed { index, info ->
            val row = TableRow(requireContext())
            val bgColor = if (index % 2 == 0) Color.WHITE else Color.parseColor("#D0E8FF")

            // First column → CheckBox
            val checkBox = CheckBox(requireContext()).apply {
                isChecked = info.done
                setBackgroundColor(bgColor)
                setOnCheckedChangeListener { _, isChecked ->
                    info.done = isChecked
                }
            }
            row.addView(checkBox)

            // Other columns
            listOf(info.lts.toString(), info.ns.toString(), info.mk, info.hu).forEach { value ->
                val tv = TextView(requireContext()).apply {
                    setTextColor(Color.BLACK)
                    setBackgroundColor(bgColor)
                    setPadding(16, 8, 16, 8)
                    textSize = 14f
                    text = value
                }
                row.addView(tv)
            }

            tableLayout.addView(row)
        }
    }
    fun updateCommand(command: TargetCommand) {
        targetCommand = command
        gunInfos = command.guns.toMutableList()
        activity?.runOnUiThread {
            orderTextView.text = command.orderText ?: ""
            buildTable()
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
