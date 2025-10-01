package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

class TerminalFragment : Fragment() {

    private lateinit var terminalOutput: TextView
    private lateinit var viewModel: SharedViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        terminalOutput = TextView(requireContext()).apply {
            text = "UDP Terminal Started...\n"
            setPadding(16, 16, 16, 16)
        }
        return ScrollView(requireContext()).apply {
            addView(terminalOutput)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel = ViewModelProvider(requireActivity())[SharedViewModel::class.java]

        viewModel.logMessages.observe(viewLifecycleOwner) { msg ->
            appendLog(msg)
        }
    }

    private fun appendLog(text: String) {
        activity?.runOnUiThread {
            terminalOutput.append("\n$text")
        }
    }
}
