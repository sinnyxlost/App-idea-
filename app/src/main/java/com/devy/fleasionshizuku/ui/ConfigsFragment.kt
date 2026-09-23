package com.devy.fleasionshizuku.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.devy.fleasionshizuku.ConfigRepository
import com.devy.fleasionshizuku.FleasionConfig
import com.devy.fleasionshizuku.FleasionConfigParser
import com.devy.fleasionshizuku.R
import java.io.File

class ConfigsFragment : Fragment() {

    private lateinit var configList: LinearLayout
    private lateinit var statusLine: TextView

    private val pickConfig = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            toast("Picker cancelled.")
            return@registerForActivityResult
        }
        val uri: Uri? = result.data?.data
        if (uri == null) {
            toast("No file selected.")
            return@registerForActivityResult
        }
        importConfig(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_configs, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configList = view.findViewById(R.id.configList)
        statusLine = TextView(requireContext()).apply {
            setPadding(0, 12, 0, 12)
            setTextColor(0xFF000000.toInt())
        }
        configList.addView(statusLine)

        view.findViewById<Button>(R.id.btnImportConfig).setOnClickListener {
            launchConfigPicker()
        }

        view.findViewById<Button>(R.id.btnReloadConfigs).setOnClickListener {
            reload()
        }

        reload()
    }

    private fun launchConfigPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/json",
                "text/plain",
                "application/octet-stream",
                "*/*"
            ))
        }
        try {
            pickConfig.launch(intent)
        } catch (t: Throwable) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            pickConfig.launch(fallback)
        }
    }

    private fun importConfig(uri: Uri) {
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Throwable) {}

        val name = queryName(uri) ?: "imported_${System.currentTimeMillis()}.json"

        // Copy into app configs folder so it survives
        val destDir = File(requireContext().filesDir, "imported_configs").apply { mkdirs() }
        val dest = File(destDir, name)

        try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (t: Throwable) {
            toast("Copy failed: ${t.message}")
            return
        }

        // Parse + validate
        val cfg: FleasionConfig = try {
            FleasionConfigParser.parse(dest.readText(), name)
        } catch (t: Throwable) {
            toast("Invalid config: ${t.message}")
            return
        }

        if (cfg.rules.isEmpty()) {
            toast("Config has 0 rules — not useful.")
            return
        }

        ConfigBridge.registerRuntime(cfg)
        toast("Imported ${cfg.name} (${cfg.rules.size} rules)")
        reload()
    }

    private fun queryName(uri: Uri): String? {
        return try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (_: Throwable) { null }
    }

    private fun reload() {
        // Clear list but keep status line
        configList.removeAllViews()
        configList.addView(statusLine)

        val configs = ConfigRepository.loadAllConfigs(requireContext())
        val runtime = ConfigBridge.snapshot()

        statusLine.text = "Loaded: ${configs.size} on disk, ${runtime.size} in memory"

        val all = configs + runtime
        if (all.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = "No configs yet. Tap Import to add one."
                setPadding(0, 8, 0, 8)
                setTextColor(0xFF000000.toInt())
            }
            configList.addView(tv)
            return
        }

        all.forEach { cfg ->
            val tv = TextView(requireContext()).apply {
                text = "• ${cfg.name} — ${cfg.rules.size} rules"
                setPadding(0, 8, 0, 8)
                setTextColor(0xFF000000.toInt())
            }
            configList.addView(tv)
        }

        // Push to the active proxy if running
        ConfigBridge.pushToProxyIfRunning(requireContext())
    }

    private fun toast(s: String) {
        statusLine.text = s
    }
}
