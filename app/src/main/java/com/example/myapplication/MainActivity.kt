package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val commandsFragment = CommandsFragment()
    private val terminalFragment = TerminalFragment()

    private val settingsFragment = SettingsFragment()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Default tab
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.mainContainer, commandsFragment)
            .commit()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_commands -> {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.mainContainer, commandsFragment)
                        .commit()
                    true
                }
                R.id.nav_terminal -> {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.mainContainer, terminalFragment)
                        .commit()
                    true
                }
                R.id.nav_settings -> {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.mainContainer, settingsFragment)
                        .commit()
                    true
                }
                else -> false
            }
        }
    }
}
