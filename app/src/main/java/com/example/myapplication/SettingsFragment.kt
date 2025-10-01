package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings_menu, container, false)

        view.findViewById<Button>(R.id.btnVisualSettings).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContainer, VisualSettingsFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<Button>(R.id.btnInfoSettings).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContainer, InfoSettingsFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }
}
