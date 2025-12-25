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
    private lateinit var correctionContainer: LinearLayout

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
    private fun addCorrectionText(text: String) {
        val tv = TextView(requireContext()).apply {
            this.text = text
            textSize = 15f
            setPadding(12, 8, 12, 8)
            setTextColor(getColorFromTheme(com.google.android.material.R.attr.colorOnSurface))
            setBackgroundColor(
                getColorFromTheme(com.google.android.material.R.attr.colorSurfaceVariant)
            )
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 8
        }

        correctionContainer.addView(tv, params)
    }
    private fun getColorFromTheme(attrRes: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }


    fun getLabel(): String = targetCommand?.targetName ?: ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(getColorFromTheme(android.R.attr.colorBackground))
        }

        // ---- ORDER TEXT (top) ----
        orderTextView = TextView(requireContext()).apply {
            text = targetCommand?.orderText ?: ""
            setTypeface(null, Typeface.BOLD)
            textSize = 18f
            setPadding(16, 16, 16, 16)
            setTextColor(getColorFromTheme(com.google.android.material.R.attr.colorOnBackground))
        }

        rootLayout.addView(orderTextView)

        // ✅ ADD THIS BLOCK HERE
        correctionContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 8, 16, 8)
        }

        rootLayout.addView(correctionContainer)

        // ---- TABLE BELOW ----
        tableLayout = TableLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setStretchAllColumns(true)
        }

        rootLayout.addView(tableLayout)

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
    fun updateCommand(command: TargetCommand) {
        targetCommand = command

        activity?.runOnUiThread {
            orderTextView.text = command.orderText ?: ""

            if (command.isCorrection) {
                addCorrectionText(command.orderText)
            } else {
                // full refresh (new target)
                correctionContainer.removeAllViews()
                buildTable()
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
