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
                resources.getDimensionPixelSize(R.dimen.command_list_width), // Use dimension resource
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFFEEEEEE.toInt())
        }

        val contentFrame = FrameLayout(requireContext()).apply {
            id = View.generateViewId()
            contentFrameId = id
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        layout.addView(tabList)
        layout.addView(contentFrame)

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
                tabs.sort() // Sort before notifying adapter
            }
            commandData[targetName] = command
            saveCommands()
            
            // Notify adapter after data and tabs are updated
            listAdapter?.notifyDataSetChanged()

            // Decide which tab to show
            val shouldShowThisCommand = currentVisibleTab == targetName || (isNewTab && tabs.size == 1)
            val isContentFrameEmpty = childFragmentManager.findFragmentById(contentFrameId) == null

            if (shouldShowThisCommand) {
                showTab(command)
            } else if (isContentFrameEmpty && tabs.isNotEmpty()) {
                // If nothing is shown, and there are tabs, show the first one
                commandData[tabs[0]]?.let { showTab(it) }
            }
        }

        loadCommands()
        return layout
    }

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