package com.devy.fleasionshizuku.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.devy.fleasionshizuku.ProxyVpnService
import com.devy.fleasionshizuku.R
import com.devy.fleasionshizuku.ShizukuManager
import rikka.shizuku.Shizuku

class HomeFragment : Fragment() {

    private lateinit var statusText: TextView
    private lateinit var homeLog: TextView
    private val shizukuManager by lazy { ShizukuManager(requireContext()) }
    private val shizukuPermissionCode = 1001

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) startProxyService()
        else log("VPN permission denied.")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        statusText = view.findViewById(R.id.statusText)
        homeLog = view.findViewById(R.id.homeLog)

        view.findViewById<Button>(R.id.btnShizuku).setOnClickListener { requestShizuku() }
        view.findViewById<Button>(R.id.btnLaunch).setOnClickListener { startProxy() }
        view.findViewById<Button>(R.id.btnStop).setOnClickListener {
            requireContext().stopService(Intent(requireContext(), ProxyVpnService::class.java))
            log("Proxy stopped.")
        }
        updateStatus()
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) {
            log("Shizuku not running. Start Shizuku app first.")
            return
        }
        if (Shizuku.isPreV11() ||
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            updateStatus()
            shizukuManager.runAutoSetup(true) { msg -> activity?.runOnUiThread { log(msg) } }
        } else {
            Shizuku.requestPermission(shizukuPermissionCode)
        }
    }

    private fun startProxy() {
        val intent = VpnService.prepare(requireContext())
        if (intent != null) vpnLauncher.launch(intent) else startProxyService()
    }

    private fun startProxyService() {
        val i = Intent(requireContext(), ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_START
        }
        requireContext().startForegroundService(i)
        log("Roblox proxy starting on 127.0.0.1:8081")
    }

    private fun updateStatus() {
        statusText.text = if (Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED)
            "Shizuku: granted ✅" else "Shizuku: not granted ❌"
    }

    private fun log(s: String) {
        homeLog.append("$s\n")
    }
}
