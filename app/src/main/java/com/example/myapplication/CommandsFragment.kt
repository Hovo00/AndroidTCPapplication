package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import android.widget.TextView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.EditText
import android.graphics.Color
import android.graphics.Typeface
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView


import android.view.Gravity

class VerticalTextView(context: Context, attrs: AttributeSet? = null) : AppCompatTextView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()

        // Rotate around top-left corner
        canvas.rotate(-90f)
        canvas.translate(-height.toFloat(), 0f)

        // Center the text properly after rotation
        val dx = (height - layout.width) / 2f
        val dy = (width - layout.height) / 2f
        canvas.translate(dx, dy)

        layout?.draw(canvas)
        canvas.restore()
    }
}




private const val COMMANDS_FILENAME = "commands.json"

class CommandsFragment : Fragment() {

    private lateinit var tabList: ListView
    private var listAdapter: CommandAdapter? = null
    private val tabs = mutableListOf<String>()
    private val commandData = mutableMapOf<String, TargetCommand>()
    private var contentFrameId: Int = 0
    private lateinit var viewModel: SharedViewModel
    private val gson = Gson()
    private var currentVisibleTab: String? = null

    // Common UI
    private lateinit var commonHeader: TextView
    private lateinit var commonTableLayout: TableLayout
    private var commonTableVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ---- Left side: tab list ----
        tabList = ListView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.command_list_width),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFFEEEEEE.toInt())
        }

        // ---- Right side: content container ----
        val contentWrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        // --- Common header (toggle) ---
        commonHeader = TextView(requireContext()).apply {
            text = "Հրանոթի հրամանատարի գրառում ▶"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(Color.LTGRAY)
            setPadding(16, 16, 16, 16)
            setOnClickListener { toggleCommonTable() }
        }

        // --- Common table ---
        commonTableLayout = TableLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.WHITE)
            setStretchAllColumns(true)
            visibility = View.GONE
        }
        buildCommonTable()

        // --- Frame for TabFragments ---
        val contentFrame = FrameLayout(requireContext()).apply {
            id = View.generateViewId()
            contentFrameId = id
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        // add to wrapper
        contentWrapper.addView(commonHeader)
        contentWrapper.addView(commonTableLayout)
        contentWrapper.addView(contentFrame)

        // add to root layout
        layout.addView(tabList)
        layout.addView(contentWrapper)

        // setup adapter + listeners (unchanged)
        listAdapter = CommandAdapter(requireContext(), tabs) { commandName ->
            handleDeleteCommand(commandName)
        }
        tabList.adapter = listAdapter

        tabList.setOnItemClickListener { _, _, position, _ ->
            val label = tabs[position]
            commandData[label]?.let { showTab(it) }
        }

        // viewmodel
        viewModel = ViewModelProvider(requireActivity())[SharedViewModel::class.java]

        viewModel.incomingCommand.observe(viewLifecycleOwner) { command ->
            val targetName = command.targetName
            val isNewTab = targetName !in tabs

            if (isNewTab) {
                tabs.add(targetName)
                tabs.sort()
            }
            commandData[targetName] = command
            saveCommands()
            listAdapter?.notifyDataSetChanged()

            val shouldShowThisCommand = currentVisibleTab == targetName || (isNewTab && tabs.size == 1)
            val isContentFrameEmpty = childFragmentManager.findFragmentById(contentFrameId) == null

            if (shouldShowThisCommand) {
                showTab(command)
            } else if (isContentFrameEmpty && tabs.isNotEmpty()) {
                commandData[tabs[0]]?.let { showTab(it) }
            }
        }

        loadCommands()
        return layout
    }

    private fun toggleCommonTable() {
        commonTableVisible = !commonTableVisible
        commonTableLayout.visibility = if (commonTableVisible) View.VISIBLE else View.GONE
        commonHeader.text = if (commonTableVisible) "Հրանոթի հրամանատարի գրառում ▲" else "Հրանոթի հրամանատարի գրառում ▶"
    }
    private fun createBorderedWrapper(context: Context, view: View): FrameLayout {
        // Create a drawable for the border
        val border = android.graphics.drawable.GradientDrawable()
        border.setColor(Color.TRANSPARENT) // Cell background
        border.setStroke(1, Color.BLACK)   // 1 pixel black border

        // Create the wrapper, set its background, and add the view to it
        return FrameLayout(context).apply {
            background = border
            addView(view) // Add the TextView or EditText inside the border
            setPadding(1, 1, 1, 1) // Optional: padding to prevent content from touching the border
        }
    }
    private fun buildCommonTable() {
        commonTableLayout.removeAllViews()

        val context = requireContext()
        val numCols = 6

        val tableContainerWidth =
            resources.displayMetrics.widthPixels - resources.getDimensionPixelSize(R.dimen.command_list_width)
        val cellWidth = tableContainerWidth / (numCols)

        fun dpToPx(dp: Int): Int =
            (dp * resources.displayMetrics.density).toInt()

        val isLandscape = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels
        val row1Height = dpToPx(if (isLandscape) 40 else 50)
        val row2Height = dpToPx(if (isLandscape) 100 else 140)
        val row3Height = dpToPx(if (isLandscape) 40 else 50)

        // --- Row 1 ---
        val row1 = TableRow(context)

        val empty = TextView(context).apply {
            text = ""
            gravity = Gravity.CENTER
            textSize = 12f
        }
        val paramsEmpty = TableRow.LayoutParams(cellWidth, row1Height)
        row1.addView(createBorderedWrapper(context, empty), paramsEmpty)

        val merged234 = TextView(context).apply {
            text = "Հինական անկյունաչափերը"
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#E8EAF6"))
            textSize = 12f
        }
        val params234 = TableRow.LayoutParams(cellWidth * 3, row1Height)
        params234.span = 3
        row1.addView(createBorderedWrapper(context, merged234), params234)

        val merged56 = TextView(context).apply {
            text = "Անկյունաչափերի տարբերությունը"
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#E3F2FD"))
            textSize = 10f
        }
        val params56 = TableRow.LayoutParams(cellWidth * 2, row1Height)
        params56.span = 2
        row1.addView(createBorderedWrapper(context, merged56), params56)

        commonTableLayout.addView(row1)

        // --- Row 2 (non-editable, vertical text, Armenian) ---
        // --- Row 2 (non-editable, vertical text, Armenian) ---
        val r2Texts = listOf(
            "բուսոլի նշումն ըստ հ.ու.",
            "Ըստ հիմնական ՆԿ-ի",
            "Ըստ պահեստային ՆԿ-ի",
            "Ըստ գիշերային ՆԿ-ի",
            "Պահեստային ՆԿ-ին",
            "Գիշերային ՆԿ-ին"
        )

        val row2 = TableRow(context)
        for (col in 0 until numCols) {
            // 1. Create the TextView that will be rotated
            val textView = VerticalTextView(context).apply {
                text = r2Texts[col]
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setBackgroundColor(Color.parseColor("#FAFAFA"))
                textSize = 10f
            }
            val params = TableRow.LayoutParams(cellWidth, row2Height)
            row2.addView(createBorderedWrapper(context, textView), params)


        }
        commonTableLayout.addView(row2)



        // --- Row 3 (r3c1–4 editable, r3c5–6 non-editable) ---
        val row3 = TableRow(context)
        for (col in 1..numCols) {
            val cellView: View = if (col <= 4) {
                EditText(context).apply {
                    hint = "r3c$col"
                    gravity = Gravity.CENTER
                    background = null
                    textSize = 12f
                }
            } else {
                TextView(context).apply {
                    text = "r3c$col"
                    gravity = Gravity.CENTER
                    setBackgroundColor(Color.parseColor("#F5F5F5"))
                    textSize = 12f
                }
            }
            val params = TableRow.LayoutParams(cellWidth, row3Height)
            row3.addView(createBorderedWrapper(context, cellView), params)
        }
        commonTableLayout.addView(row3)
    }






//    private fun buildCommonTable() {
//        commonTableLayout.removeAllViews()
//
//        // --- Row 1 ---
//        val row1 = TableRow(requireContext())
//
//        // Col1 (merged vertically with row2) - vertical text
//        val cell1 = TextView(requireContext()).apply {
//            text = "     "
//            setPadding(8, 8, 8, 8)
//            setBackgroundColor(Color.parseColor("#CCCCCC"))
//            setTypeface(null, Typeface.BOLD)
//            gravity = Gravity.CENTER
//            rotation = -90f
//            textSize = 12f
//        }
//        val params1 = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 1f)
//        cell1.layoutParams = params1
//        row1.addView(cell1)
//
//        // Cols 2+3+4 merged horizontally
//        val cell2to4 = TextView(requireContext()).apply {
//            text = "Հինական\n(անկյունաչափերը)"
//            setPadding(16, 8, 16, 8)
//            setBackgroundColor(Color.parseColor("#DDDDDD"))
//            setTypeface(null, Typeface.BOLD)
//            gravity = Gravity.CENTER
//            textSize = 12f
//        }
//        val params2to4 = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f)
//        params2to4.span = 3
//        row1.addView(cell2to4, params2to4)
//
//        // Cols 5+6 merged horizontally
//        val cell5to6 = TextView(requireContext()).apply {
//            text = "Անկյունաչափերի\n(տարբերությունը )"
//            setPadding(16, 8, 16, 8)
//            setBackgroundColor(Color.parseColor("#BBBBBB"))
//            setTypeface(null, Typeface.BOLD)
//            gravity = Gravity.CENTER
//            textSize = 12f
//        }
//        val params5to6 = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2f)
//        params5to6.span = 2
//        row1.addView(cell5to6, params5to6)
//
//        commonTableLayout.addView(row1)
//
//        // --- Row 2 ---
//        val row2 = TableRow(requireContext())
//
//        // Placeholder for merged Col1 (invisible)
//        val emptyView = View(requireContext()).apply {
//            layoutParams = TableRow.LayoutParams(0, 0)
//            visibility = View.INVISIBLE
//        }
//        row2.addView(emptyView)
//
//        val r2Texts = listOf(
//            "բուսոլի նշումն ըստ հ.ու.",
//            "Ըստ հիմնական ՆԿ-ի",
//            "Ըստ պահեստային ՆԿ-ի",
//            "Ըստ գիշերային ՆԿ-ի",
//            "Պահեստային ՆԿ-ին",
//            "Գիշերային ՆԿ-ին"
//        )
//
//        for (text in r2Texts) {
//            val container = FrameLayout(requireContext()).apply {
//                layoutParams = TableRow.LayoutParams(
//                    90, // width for vertical text
//                    240 // height of the row
//                )
//                setBackgroundColor(Color.parseColor("#EEEEEE"))
//            }
//
//            val cell = TextView(requireContext()).apply {
//                this.text = text
//                setPadding(0, 0, 0, 0)
//                gravity = Gravity.CENTER
//                rotation = -90f
//                textSize = 10f
//            }
//
//            container.addView(cell)
//            row2.addView(container)
//        }
//        commonTableLayout.addView(row2)
//
//        // --- Row 3 ---
//        val row3 = TableRow(requireContext())
//        for (colIndex in 1..6) {
//            if (colIndex in 1..4) {
//                // Editable, horizontal
//                val cell = EditText(requireContext()).apply {
//                    hint = "R3C$colIndex"
//                    setPadding(16, 8, 16, 8)
//                    gravity = Gravity.CENTER
//                    setBackgroundColor(Color.WHITE)
//                    textSize = 12f
//                }
//                val params = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
//                row3.addView(cell, params)
//            } else {
//                // Not editable, horizontal
//                val cell = TextView(requireContext()).apply {
//                    text = "R3C$colIndex"
//                    setPadding(16, 8, 16, 8)
//                    gravity = Gravity.CENTER
//                    setBackgroundColor(Color.parseColor("#EEEEEE"))
//                    textSize = 12f
//                }
//                val params = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
//                row3.addView(cell, params)
//            }
//        }
//        commonTableLayout.addView(row3)
//    }


    private fun showTab(command: TargetCommand) {
        currentVisibleTab = command.targetName
        // Assuming TabFragment.newInstance(command) is correctly defined elsewhere
        val fragment = TabFragment.newInstance(command) 
        childFragmentManager.beginTransaction()
            .replace(contentFrameId, fragment)
            .commit()
    }
    
    private fun clearContentFrame() {
        val currentFragment = childFragmentManager.findFragmentById(contentFrameId)
        if (currentFragment != null) {
            childFragmentManager.beginTransaction().remove(currentFragment).commitAllowingStateLoss()
        }
        currentVisibleTab = null
    }

    private fun saveCommands() {
        try {
            val file = File(requireContext().filesDir, COMMANDS_FILENAME)
            val jsonString = gson.toJson(commandData)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Log.e("CommandsFragment", "Error saving commands: ${e.localizedMessage}")
        }
    }

    private fun loadCommands() {
        try {
            val file = File(requireContext().filesDir, COMMANDS_FILENAME)
            if (file.exists()) {
                val jsonString = file.readText()
                val type = object : TypeToken<MutableMap<String, TargetCommand>>() {}.type
                val loadedData: MutableMap<String, TargetCommand>? = gson.fromJson(jsonString, type)

                if (loadedData != null) {
                    commandData.clear()
                    commandData.putAll(loadedData)
                    tabs.clear()
                    tabs.addAll(commandData.keys.sorted()) // Ensure tabs are sorted
                    listAdapter?.notifyDataSetChanged()

                    if (tabs.isNotEmpty()) {
                       commandData[tabs[0]]?.let { showTab(it) } // Show first tab
                    } else {
                       clearContentFrame() // No tabs to show
                    }
                } else {
                    // File exists but data is null (e.g. parse error or empty file)
                    Log.w("CommandsFragment", "Command file exists but content is invalid or empty.")
                    commandData.clear()
                    tabs.clear()
                    listAdapter?.notifyDataSetChanged()
                    clearContentFrame()
                }
            } else {
                // File does not exist, treat as no commands
                commandData.clear()
                tabs.clear()
                listAdapter?.notifyDataSetChanged()
                clearContentFrame()
            }
        } catch (e: Exception) {
            Log.e("CommandsFragment", "Error loading commands: ${e.localizedMessage}")
            // Fallback: clear data and UI
            commandData.clear()
            tabs.clear()
            listAdapter?.notifyDataSetChanged()
            clearContentFrame()
        }
    }

    private fun handleDeleteCommand(commandName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Command")
            .setMessage("Are you sure you want to delete the command \"$commandName\"?")// Corrected string template
            .setPositiveButton("Yes") { _, _ ->
                val wasCurrentVisibleTab = currentVisibleTab == commandName
                
                tabs.remove(commandName)
                commandData.remove(commandName)
                listAdapter?.notifyDataSetChanged()
                saveCommands()

                if (wasCurrentVisibleTab) {
                    clearContentFrame() 
                }

                if (tabs.isNotEmpty()) {
                    // If the deleted tab was visible OR if no tab is currently visible, show the first available tab.
                    if (wasCurrentVisibleTab || currentVisibleTab == null) {
                         commandData[tabs[0]]?.let { showTab(it) }
                    }
                    // If another tab was already visible and it wasn't the one deleted, it remains visible.
                } else {
                    // No tabs left, ensure content frame is clear (already done if wasCurrentVisibleTab and currentVisibleTab was not null)
                    if (!wasCurrentVisibleTab || currentVisibleTab != null) { // ensure clear if wasCurrentVisibleTab was null
                        clearContentFrame()
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }
}