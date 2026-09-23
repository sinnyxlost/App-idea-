package com.devy.fleasionshizuku.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.devy.fleasionshizuku.ProxyVpnService
import com.devy.fleasionshizuku.R
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val autoLaunch = view.findViewById<MaterialSwitch>(R.id.switchAutoLaunch)
        autoLaunch.isChecked = ProxyVpnService.autoLaunch
        autoLaunch.setOnCheckedChangeListener { _, checked ->
            ProxyVpnService.autoLaunch = checked
        }
    }
}
