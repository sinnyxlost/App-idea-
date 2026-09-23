package com.devy.fleasionshizuku.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.devy.fleasionshizuku.DeepCleanupManager
import com.devy.fleasionshizuku.FFlagManager
import com.devy.fleasionshizuku.R
import com.devy.fleasionshizuku.RobloxCacheCleaner
import com.devy.fleasionshizuku.ShizukuManager
import com.google.android.material.materialswitch.MaterialSwitch

class CleanupFragment : Fragment() {

    private lateinit var deepLog: TextView
    private val shizukuManager by lazy { ShizukuManager(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_cleanup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        deepLog = view.findViewById(R.id.deepLog)

        val switchFflags = view.findViewById<MaterialSwitch>(R.id.switchDeepFflags)
        val switchFullRoblox = view.findViewById<MaterialSwitch>(R.id.switchDeepRobloxWipe)

        view.findViewById<Button>(R.id.btnClearCacheOnly).setOnClickListener {
            log("=== Clear Cache Only ===")
            RobloxCacheCleaner.clearOnly(requireContext(), shizukuManager) { log(it) }
        }

        view.findViewById<Button>(R.id.btnDeepWipe).setOnClickListener {
            log("=== DEEP CLEANUP STARTED ===")
            DeepCleanupManager.wipe(
                requireContext(),
                shizukuManager,
                includeFflags = switchFflags.isChecked,
                includeRobloxFullWipe = switchFullRoblox.isChecked
            ) { log(it) }
        }
    }

    private fun log(s: String) {
        activity?.runOnUiThread { deepLog.append("$s\n") }
    }
}
