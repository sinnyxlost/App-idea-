package com.devy.fleasionshizuku.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.devy.fleasionshizuku.R
import com.devy.fleasionshizuku.ConfigBridge
import com.devy.fleasionshizuku.ShizukuManager
import com.devy.fleasionshizuku.SkyInstaller
import com.devy.fleasionshizuku.FleasionConfig
import com.devy.fleasionshizuku.FleasionRule
import java.io.File

class AssetsFragment : Fragment() {

    private lateinit var skyPath: TextView
    private lateinit var assetLog: TextView
    private lateinit var assetIdInput: EditText

    private var selectedFile: File? = null
    private var selectedKind: String = "sky"
    private val shizukuManager by lazy { ShizukuManager(requireContext()) }

    /** Fires after user picks ANY file from the picker. */
    private val pickAnyFile = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            log("Picker cancelled.")
            return@registerForActivityResult
        }
        val uri: Uri? = result.data?.data
        if (uri == null) {
            log("No file selected.")
            return@registerForActivityResult
        }
        importPicked(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_assets, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        skyPath = view.findViewById(R.id.skyPath)
        assetLog = view.findViewById(R.id.assetLog)
        assetIdInput = view.findViewById(R.id.assetIdInput)

        view.findViewById<Button>(R.id.btnPickSky).setOnClickListener {
            selectedKind = "sky"
            launchPicker("image/*")
        }

        view.findViewById<Button>(R.id.btnPickTexture).setOnClickListener {
            selectedKind = "texture"
            launchPicker("image/*")
        }

        view.findViewById<Button>(R.id.btnPickSound).setOnClickListener {
            selectedKind = "sound"
            launchPicker("audio/*")
        }

        view.findViewById<Button>(R.id.btnInstallSky).setOnClickListener {
            val f = selectedFile
            if (f == null) { log("No file selected. Pick one first."); return@setOnClickListener }
            SkyInstaller.installToRoblox(requireContext(), f, shizukuManager) { msg ->
                activity?.runOnUiThread { log(msg) }
            }
        }

        view.findViewById<Button>(R.id.btnBindAsset).setOnClickListener {
            val f = selectedFile
            if (f == null) { log("Pick a file first."); return@setOnClickListener }
            val id = assetIdInput.text.toString().trim()
            if (id.isEmpty() || !id.all { it.isDigit() }) {
                log("Enter a valid numeric asset ID.")
                return@setOnClickListener
            }
            val cfg = FleasionConfig(name = "asset_$id")
            cfg.rules.add(FleasionRule(matchId = id, replacementPath = f.absolutePath))
            ConfigBridge.registerRuntime(cfg)
            log("Bound asset $id → ${f.name}")
            log("It will now be served by the proxy.")
        }

        log("Ready. Pick a file to begin.")
    }

    /** Open the Android file picker for any file type. */
    private fun launchPicker(mime: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            // Also allow "*/*" fallback if user needs any file
            if (mime == "audio/*" || mime == "image/*") {
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(mime, "*/*"))
            }
        }
        try {
            pickAnyFile.launch(intent)
        } catch (t: Throwable) {
            // Fallback to GET_CONTENT if OPEN_DOCUMENT fails
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            pickAnyFile.launch(fallback)
        }
    }

    private fun importPicked(uri: Uri) {
        // Preserve read permission across process restarts
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Throwable) {}

        val name = "${selectedKind}_${System.currentTimeMillis()}.${extFromUri(uri)}"
        val f = SkyInstaller.importSky(requireContext(), uri, name)
        if (f == null) {
            log("Failed to import file.")
            return
        }
        selectedFile = f
        skyPath.text = "Selected (${selectedKind}): ${f.name}"
        log("Imported ${selectedKind}: ${f.absolutePath}")
    }

    private fun extFromUri(uri: Uri): String {
        val mime = requireContext().contentResolver.getType(uri) ?: "bin"
        return when {
            mime.contains("png") -> "png"
            mime.contains("jpeg") -> "jpg"
            mime.contains("jpg") -> "jpg"
            mime.contains("webp") -> "webp"
            mime.contains("dds") -> "dds"
            mime.contains("ogg") -> "ogg"
            mime.contains("mpeg") -> "mp3"
            mime.contains("wav") -> "wav"
            else -> "bin"
        }
    }

    private fun log(s: String) {
        assetLog.append("$s\n")
    }
}
