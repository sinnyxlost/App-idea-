package com.devy.fleasionshizuku.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.devy.fleasionshizuku.ConfigRepository
import com.devy.fleasionshizuku.R

class ConfigsFragment : Fragment() {

    private lateinit var configList: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_configs, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configList = view.findViewById(R.id.configList)

        view.findViewById<Button>(R.id.btnReloadConfigs).setOnClickListener { reload() }
        view.findViewById<Button>(R.id.btnImportConfig).setOnClickListener {
            reload()
        }
        reload()
    }

    private fun reload() {
        configList.removeAllViews()
        val configs = ConfigRepository.loadAllConfigs(requireContext())
        if (configs.isEmpty()) {
            val tv = TextView(requireContext())
            tv.text = "No configs found in /sdcard/Fleasion/configs/"
            configList.addView(tv)
            return
        }
        configs.forEach { cfg ->
            val tv = TextView(requireContext())
            tv.text = "• ${cfg.name} — ${cfg.rules.size} rules"
            tv.setPadding(0, 8, 0, 8)
            configList.addView(tv)
        }
    }
}
