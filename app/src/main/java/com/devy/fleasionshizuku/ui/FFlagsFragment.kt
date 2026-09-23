package com.devy.fleasionshizuku.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.devy.fleasionshizuku.FFlagManager
import com.devy.fleasionshizuku.R
import com.devy.fleasionshizuku.ShizukuManager
import com.google.android.material.materialswitch.MaterialSwitch

class FFlagsFragment : Fragment() {

    private lateinit var flagList: LinearLayout
    private lateinit var fflagLog: TextView
    private val shizukuManager by lazy { ShizukuManager(requireContext()) }
    private val switches = mutableMapOf<String, MaterialSwitch>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_fflags, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        flagList = view.findViewById(R.id.flagList)
        fflagLog = view.findViewById(R.id.fflagLog)

        val current = FFlagManager.readAll(requireContext())

        FFlagManager.KNOWN_FLAGS.forEach { (key, default) ->
            val sw = MaterialSwitch(requireContext()).apply {
                text = key
                textSize = 12f
                setTextColor(0xFF000000.toInt())
                isChecked = current.containsKey(key)
                setPadding(0, 12, 0, 12)
            }
            switches[key] = sw
            flagList.addView(sw)
        }

        view.findViewById<Button>(R.id.btnApplyFlags).setOnClickListener {
            val selected = mutableMapOf<String, String>()
            switches.forEach { (key, sw) ->
                if (sw.isChecked) {
                    val default = FFlagManager.KNOWN_FLAGS.firstOrNull { it.first == key }?.second ?: "True"
                    selected[key] = default
                }
            }
            FFlagManager.writeFlags(requireContext(), selected, shizukuManager) { log(it) }
            log("✓ Applied. Restart Roblox to take effect.")
        }

        view.findViewById<Button>(R.id.btnRemoveFlags).setOnClickListener {
            FFlagManager.removeAll(requireContext(), shizukuManager) { log(it) }
        }
    }

    private fun log(s: String) { fflagLog.append("$s\n") }
}
