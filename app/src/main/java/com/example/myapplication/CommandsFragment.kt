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


class VerticalTextView(context: Context, attrs: AttributeSet? = null) : AppCompatTextView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.rotate(-90f)
        canvas.translate(-height.toFloat(), 0f)
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

    private lateinit var commonHeader: TextView
    private lateinit var commonTableLayout: TableLayout
    private var commonTableVisible = false

    private fun getColorFromTheme(attrRes: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

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

        tabList = ListView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.command_list_width),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(getColorFromTheme(com.google.android.material.R.attr.colorSurface))
        }

        val contentWrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        commonHeader = TextView(requireContext()).apply {
            text = "Հրանոթի հրամանատարի գրառում ▶"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(getColorFromTheme(com.google.android.material.R.attr.colorPrimary))
            setTextColor(getColorFromTheme(com.google.android.material.R.attr.colorOnPrimary))
            setPadding(16, 16, 16, 16)
            setOnClickListener { toggleCommonTable() }
        }

        commonTableLayout = TableLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(getColorFromTheme(com.google.android.material.R.attr.colorSurface))
            setStretchAllColumns(true)
            visibility = View.GONE
        }
        buildCommonTable()

        val contentFrame = FrameLayout(requireContext()).apply {
            id = View.generateViewId()
            contentFrameId = id
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        contentWrapper.addView(commonHeader)
        contentWrapper.addView(commonTableLayout)
        contentWrapper.addView(contentFrame)

        layout.addView(tabList)
        layout.addView(contentWrapper)

        listAdapter = CommandAdapter(requireContext(), tabs) { commandName ->
            handleDeleteCommand(commandName)
        }
        tabList.adapter = listAdapter

        tabList.setOnItemClickListener { _, _, position, _ ->
            val label = tabs[position]
            commandData[label]?.let { showTab(it) }
        }

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
        val border = android.graphics.drawable.GradientDrawable()
        border.setColor(Color.TRANSPARENT)
        border.setStroke(1, getColorFromTheme(com.google.android.material.R.attr.colorOnSurface))

        return FrameLayout(context).apply {
            background = border
            addView(view)
            setPadding(1, 1, 1, 1)
        }
    }
    private fun buildCommonTable() {
        commonTableLayout.removeAllViews()

        val context = requireContext()
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val numCols = 6

        val tableContainerWidth =
            resources.displayMetrics.widthPixels - resources.getDimensionPixelSize(R.dimen.command_list_width)
        val cellWidth = tableContainerWidth / numCols

        fun dpToPx(dp: Int): Int =
            (dp * resources.displayMetrics.density).toInt()

        val isLandscape = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels
        val row1Height = dpToPx(if (isLandscape) 40 else 50)
        val row2Height = dpToPx(if (isLandscape) 100 else 140)
        val row3Height = dpToPx(if (isLandscape) 40 else 50)

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
            if (isDarkMode) {
                setBackgroundColor(Color.parseColor("#004d00")) // dark green
                setTextColor(Color.WHITE)
            } else {
                setBackgroundColor(Color.parseColor("#97db9a")) // light green
                setTextColor(Color.BLACK)
            }
            textSize = 12f
        }
        val params234 = TableRow.LayoutParams(cellWidth * 3, row1Height)
        params234.span = 3
        row1.addView(createBorderedWrapper(context, merged234), params234)

        val merged56 = TextView(context).apply {
            text = "Անկյունաչափերի տարբերությունը"
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            if (isDarkMode) {
                setBackgroundColor(Color.parseColor("#004d00")) // dark green
                setTextColor(Color.WHITE)
            } else {
                setBackgroundColor(Color.parseColor("#97db9a")) // light green
                setTextColor(Color.BLACK)
            }
            textSize = 10f
        }
        val params56 = TableRow.LayoutParams(cellWidth * 2, row1Height)
        params56.span = 2
        row1.addView(createBorderedWrapper(context, merged56), params56)

        commonTableLayout.addView(row1)

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
            val textView = VerticalTextView(context).apply {
                text = r2Texts[col]
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                if (isDarkMode) {
                    setBackgroundColor(Color.parseColor("#01240f")) // dark green
                    setTextColor(Color.WHITE)
                } else {
                    setBackgroundColor(Color.parseColor("#62a865")) // light green
                    setTextColor(Color.BLACK)
                }
                textSize = 10f
            }
            val params = TableRow.LayoutParams(cellWidth, row2Height)
            row2.addView(createBorderedWrapper(context, textView), params)
        }
        commonTableLayout.addView(row2)

        val row3 = TableRow(context)
        for (col in 1..numCols) {
            val cellView: View = if (col <= 4) {
                val editText = EditText(context).apply {
                    hint = "00-00"
                    gravity = Gravity.CENTER
                    if (isDarkMode) {
                        setBackgroundColor(Color.parseColor("#1e1e1e")) // dark gray
                        setTextColor(Color.WHITE)
                        setHintTextColor(Color.LTGRAY)
                    } else {
                        setBackgroundColor(Color.WHITE)
                        setTextColor(Color.BLACK)
                        setHintTextColor(Color.DKGRAY)
                    }
                    textSize = 12f
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    filters = arrayOf(android.text.InputFilter.LengthFilter(5))
                }

                var isSelfChange = false

                editText.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                    override fun afterTextChanged(s: android.text.Editable?) {
                        if (isSelfChange) return
                        isSelfChange = true

                        val raw = s?.toString() ?: ""
                        val digits = raw.replace("[^\\d]".toRegex(), "").take(4)

                        val firstPart = digits.take(2)
                        var secondPart = if (digits.length > 2) digits.substring(2) else ""

                        if (firstPart.isNotEmpty()) {
                            val v = firstPart.toInt()
                            if (v > 59) {
                                val clamped = "59"
                                val newDigits = if (digits.length > 2) clamped + digits.substring(2) else clamped
                                secondPart = if (newDigits.length > 2) newDigits.substring(2) else ""
                            }
                        }
                        if (secondPart.isNotEmpty()) {
                            val v2 = secondPart.toInt()
                            if (v2 > 99) secondPart = "99"
                        }

                        val formatted = when {
                            digits.length <= 2 -> firstPart
                            else -> {
                                val a = firstPart.padStart(2, '0')
                                val b = secondPart.padStart(digits.length - 2, '0')
                                "$a-$b"
                            }
                        }

                        if (formatted != raw) {
                            editText.setText(formatted)
                            editText.setSelection(formatted.length.coerceAtMost(editText.text.length))
                        }

                        isSelfChange = false
                    }
                })

                editText.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (!hasFocus) {
                        val raw = editText.text.toString()
                        val digits = raw.replace("[^\\d]".toRegex(), "").padEnd(4, '0').take(4)
                        var first = digits.substring(0, 2)
                        var second = digits.substring(2, 4)

                        val firstInt = first.toInt()
                        if (firstInt > 59) first = "59"
                        val secondInt = second.toInt()
                        if (secondInt > 99) second = "99"

                        val final = String.format("%02d-%02d", first.toInt(), second.toInt())
                        editText.setText(final)
                        editText.setSelection(final.length)
                    }
                }

                editText
            } else {
                TextView(context).apply {
                    text = "00-00"
                    gravity = Gravity.CENTER
                    setBackgroundColor(getColorFromTheme(com.google.android.material.R.attr.colorSurfaceContainer))
                    setTextColor(getColorFromTheme(com.google.android.material.R.attr.colorOnSurface))
                    textSize = 12f
                }
            }

            val params = TableRow.LayoutParams(cellWidth, row3Height)
            row3.addView(createBorderedWrapper(context, cellView), params)
        }

        commonTableLayout.addView(row3)
    }

    private fun showTab(command: TargetCommand) {
        currentVisibleTab = command.targetName
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
                    tabs.addAll(commandData.keys.sorted())
                    listAdapter?.notifyDataSetChanged()

                    if (tabs.isNotEmpty()) {
                       commandData[tabs[0]]?.let { showTab(it) }
                    } else {
                       clearContentFrame()
                    }
                } else {
                    Log.w("CommandsFragment", "Command file exists but content is invalid or empty.")
                    commandData.clear()
                    tabs.clear()
                    listAdapter?.notifyDataSetChanged()
                    clearContentFrame()
                }
            } else {
                commandData.clear()
                tabs.clear()
                listAdapter?.notifyDataSetChanged()
                clearContentFrame()
            }
        } catch (e: Exception) {
            Log.e("CommandsFragment", "Error loading commands: ${e.localizedMessage}")
            commandData.clear()
            tabs.clear()
            listAdapter?.notifyDataSetChanged()
            clearContentFrame()
        }
    }

    private fun handleDeleteCommand(commandName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Command")
            .setMessage("Are you sure you want to delete the command \"$commandName\"?")
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
                    if (wasCurrentVisibleTab || currentVisibleTab == null) {
                         commandData[tabs[0]]?.let { showTab(it) }
                    }
                } else {
                    if (!wasCurrentVisibleTab || currentVisibleTab != null) {
                        clearContentFrame()
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }
}