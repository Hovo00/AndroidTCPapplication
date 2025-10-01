package com.example.myapplication

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

class CommandsFragment : Fragment() {

    private lateinit var tabList: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val tabs = mutableListOf<String>()
    private val commandData = mutableMapOf<String, TargetCommand>()
    private var contentFrameId: Int = 0
    private lateinit var viewModel: SharedViewModel

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
            if (targetName !in tabs) {
                tabs.add(targetName)
                adapter.notifyDataSetChanged()
            }

            commandData[targetName] = command

            // Update currently visible tab
            childFragmentManager.fragments.forEach { frag ->
                if (frag is TabFragment && frag.getLabel() == targetName) {
                    frag.updateCommand(command)
                }
            }

            // Show first tab automatically
            if (tabs.size == 1) showTab(command)
        }

        return layout
    }

    private fun showTab(command: TargetCommand) {
        val fragment = TabFragment.newInstance(command)
        childFragmentManager.beginTransaction()
            .replace(contentFrameId, fragment)
            .commit()
    }
}
