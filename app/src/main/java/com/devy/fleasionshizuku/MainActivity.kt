package com.devy.fleasionshizuku

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.devy.fleasionshizuku.ui.*
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    lateinit var pager: ViewPager2
    lateinit var tabs: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pager = findViewById(R.id.pager)
        tabs  = findViewById(R.id.tabs)

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 5
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> HomeFragment()
                1 -> AssetsFragment()
                2 -> ConfigsFragment()
                3 -> SettingsFragment()
                else -> LogFragment()
            }
        }
        TabLayoutMediator(tabs, pager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "Home"; 1 -> "Assets"; 2 -> "Configs"
                3 -> "Settings"; else -> "Log"
            }
        }.attach()

        tabs.tabMode = TabLayout.MODE_SCROLLABLE
    }
}
