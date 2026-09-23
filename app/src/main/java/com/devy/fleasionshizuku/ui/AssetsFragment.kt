package com.devy.fleasionshizuku.ui

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.devy.fleasionshizuku.R
import com.devy.fleasionshizuku.ShizukuManager
import com.devy.fleasionshizuku.SkyInstaller
import java.io.File

class AssetsFragment : Fragment() {

    private lateinit var skyPath: TextView
    private lateinit var assetLog: TextView
    private var selectedSky: File? = null
    private val shizukuManager by lazy { ShizukuManager(requireContext()) }

    private val pickSky = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val f = SkyInstaller.importSky(requireContext(), uri, "sky_${System.currentTimeMillis()}.png")
                if (f != null) {
                    selectedSky = f
                    skyPath.text = "Selected: ${f.name}"
                    log("Sky imported: ${f.absolutePath}")
                } else log("Failed to import sky.")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_assets, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        skyPath = view.findViewById(R.id.skyPath)
        assetLog = view.findViewById(R.id.assetLog)

        view.findViewById<Button>(R.id.btnPickSky).setOnClickListener {
            val i = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
            }
            pickSky.launch(i)
        }

        view.findViewById<Button>(R.id.btnInstallSky).setOnClickListener {
            val sky = selectedSky
            if (sky == null) { log("No sky selected."); return@setOnClickListener }
            SkyInstaller.installToRoblox(requireContext(), sky, shizukuManager) { msg ->
                activity?.runOnUiThread { log(msg) }
            }
        }

        view.findViewById<Button>(R.id.btnPickTexture).setOnClickListener {
            log("Texture picker: same flow as sky — hook into AssetsFragment.")
        }

        view.findViewById<Button>(R.id.btnPickSound).setOnClickListener {
            log("Sound picker: same flow as sky — hook into AssetsFragment.")
        }

        view.findViewById<Button>(R.id.btnBindAsset).setOnClickListener {
            log("Bind asset: wire to FleasionConfigParser + AssetRewriter.loadFromConfig.")
        }
    }

    private fun log(s: String) {
        assetLog.append("$s\n")
    }
}
