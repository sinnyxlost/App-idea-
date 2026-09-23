package com.devy.fleasionshizuku.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.devy.fleasionshizuku.R
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Kept as no-op toggles for now — behavior is fixed in ProxyService
        view.findViewById<MaterialSwitch>(R.id.switchAutoLaunch)?.isChecked = true
        view.findViewById<MaterialSwitch>(R.id.switchKeepLogin)?.isChecked = true
        view.findViewById<MaterialSwitch>(R.id.switchAutoInstallSky)?.isChecked = true
    }
}
