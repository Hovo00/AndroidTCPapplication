package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

private const val COMMANDS_FILENAME = "commands.json"

class CommandsFragment : Fragment() {

    private lateinit var tabList: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val tabs = mutableListOf<String>()
    private val commandData = mutableMapOf<String, TargetCommand>()
    private var contentFrameId: Int = 0
    private lateinit var viewModel: SharedViewModel
    private val gson = Gson()

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
            layoutParams = LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0xFFEEEEEE.toInt())
        }

        val contentFrame = FrameLayout(requireContext()).apply {
            id = View.generateViewId()
            contentFrameId = id
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        layout.addView(tabList)
        layout.addView(contentFrame)

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, tabs)
        tabList.adapter = adapter

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
            }

            commandData[targetName] = command
            saveCommands() // Save after updating data

            if (isNewTab) {
                adapter.notifyDataSetChanged()
            }

            // Update currently visible tab
            childFragmentManager.fragments.forEach { frag ->
                if (frag is TabFragment && frag.getLabel() == targetName) {
                    frag.updateCommand(command)
                }
            }

            // Show first tab automatically or if it's the one being updated and is new
            if (tabs.size == 1 && isNewTab) {
                 showTab(command)
            } else if (!isNewTab && childFragmentManager.findFragmentById(contentFrameId) == null && tabs.isNotEmpty()) {
                // If a tab was updated but nothing is shown (e.g. after rotation and load), show the first one.
                 commandData[tabs[0]]?.let { showTab(it) }
            }
        }

        loadCommands() // Load commands when view is created
        return layout
    }

    private fun showTab(command: TargetCommand) {
        val fragment = TabFragment.newInstance(command)
        childFragmentManager.beginTransaction()
            .replace(contentFrameId, fragment)
            .commit()
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
                    tabs.addAll(commandData.keys)
                    adapter.notifyDataSetChanged()

                    if (tabs.isNotEmpty()) {
                       commandData[tabs[0]]?.let { showTab(it) } // Show the first tab
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CommandsFragment", "Error loading commands: ${e.localizedMessage}")
        }
    }
}