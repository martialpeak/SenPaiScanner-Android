package com.senpaiscanner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.senpaiscanner.R
import com.senpaiscanner.data.SettingsRepository
import com.senpaiscanner.databinding.ActivityMainBinding
import com.senpaiscanner.model.ProbeMode
import com.senpaiscanner.model.ScanConfig
import com.senpaiscanner.model.ScanPreset
import com.senpaiscanner.util.ExportHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private lateinit var adapter: ResultsAdapter
    private var selectedMode = ProbeMode.HTTP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ResultsAdapter { result ->
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ip", result.endpoint))
            Toast.makeText(this, getString(R.string.copied_one, result.endpoint), Toast.LENGTH_SHORT).show()
        }
        binding.recyclerResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerResults.adapter = adapter
        binding.recyclerResults.itemAnimator = null

        binding.tvConcurrencyLabel.text = getString(R.string.label_workers_value, 50)
        binding.tvTimeoutLabel.text = getString(R.string.label_timeout_value, 5)

        setupSliders()
        setupButtons()
        setupPresets()
        updateModeChips(ProbeMode.HTTP)
        loadSavedForm()
        observeViewModel()
    }

    private fun loadSavedForm() {
        lifecycleScope.launch {
            val form = SettingsRepository.scanForm.first()
            binding.etCount.setText(form.count.toString())
            binding.etPort.setText(form.port.toString())
            binding.sliderConcurrency.value = form.concurrency.toFloat()
            binding.sliderTimeout.value = form.timeoutSec.toFloat()
            binding.etCidr.setText(form.cidr)
            binding.switchHealthyOnly.isChecked = form.healthyOnly
            updateModeChips(form.mode)
            binding.tvConcurrencyLabel.text = getString(R.string.label_workers_value, form.concurrency)
            binding.tvTimeoutLabel.text = getString(R.string.label_timeout_value, form.timeoutSec)
        }
    }

    private fun setupPresets() {
        binding.btnPresetQuick.setOnClickListener { applyPreset(ScanPreset.QUICK) }
        binding.btnPresetNormal.setOnClickListener { applyPreset(ScanPreset.NORMAL) }
        binding.btnPresetDeep.setOnClickListener { applyPreset(ScanPreset.DEEP) }
    }

    private fun applyPreset(preset: ScanPreset) {
        binding.etCount.setText(preset.count.toString())
        binding.sliderConcurrency.value = preset.concurrency.toFloat()
        binding.sliderTimeout.value = preset.timeoutSec.toFloat()
        updateModeChips(preset.mode)
        binding.tvConcurrencyLabel.text = getString(R.string.label_workers_value, preset.concurrency)
        binding.tvTimeoutLabel.text = getString(R.string.label_timeout_value, preset.timeoutSec)
        Toast.makeText(this, getString(R.string.preset_applied, preset.name), Toast.LENGTH_SHORT).show()
    }

    private fun setupSliders() {
        binding.sliderConcurrency.addOnChangeListener { _, value, _ ->
            binding.tvConcurrencyLabel.text = getString(R.string.label_workers_value, value.toInt())
        }
        binding.sliderTimeout.addOnChangeListener { _, value, _ ->
            binding.tvTimeoutLabel.text = getString(R.string.label_timeout_value, value.toInt())
        }
    }

    private fun setupButtons() {
        binding.btnScan.setOnClickListener {
            if (vm.scanning.value) {
                vm.stopScan()
                binding.btnScan.text = getString(R.string.btn_scan)
            } else {
                startScan()
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnCopy.setOnClickListener { copyAll() }
        binding.btnShare.setOnClickListener { shareText(ExportHelper.toPlainLines(vm.allResults())) }
        binding.btnExportMenu.setOnClickListener { showExportDialog() }
        binding.btnHelp.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_message)
                .setPositiveButton(R.string.help_ok, null)
                .show()
        }

        binding.chipTcp.setOnClickListener { updateModeChips(ProbeMode.TCP) }
        binding.chipTls.setOnClickListener { updateModeChips(ProbeMode.TLS) }
        binding.chipHttp.setOnClickListener { updateModeChips(ProbeMode.HTTP) }
    }

    private fun showExportDialog() {
        val items = arrayOf(
            getString(R.string.export_plain),
            getString(R.string.export_csv),
            getString(R.string.export_clash),
            getString(R.string.export_singbox),
            getString(R.string.export_v2ray)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.export_csv)
            .setItems(items) { _, which ->
                val results = vm.allResults()
                val sni = vm.appSettings.value.probeSni
                val text = when (which) {
                    0 -> ExportHelper.toPlainLines(results)
                    1 -> ExportHelper.toCsv(results)
                    2 -> ExportHelper.toClash(results, sni)
                    3 -> ExportHelper.toSingBox(results, sni)
                    else -> ExportHelper.toV2rayUri(results)
                }
                shareText(text)
            }
            .show()
    }

    private fun shareText(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, R.string.no_healthy_ips, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.share_via)))
    }

    private fun copyAll() {
        val ips = vm.getHealthyIps()
        if (ips.isEmpty()) {
            Toast.makeText(this, R.string.no_healthy_ips, Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("CF IPs", ips.joinToString("\n")))
        Toast.makeText(this, getString(R.string.copied_ips, ips.size), Toast.LENGTH_SHORT).show()
    }

    private fun updateModeChips(mode: ProbeMode) {
        selectedMode = mode
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.chip_selected_bg)
        val normalBg = ContextCompat.getDrawable(this, R.drawable.chip_bg)
        val accentColor = ContextCompat.getColor(this, R.color.accent)
        val normalColor = ContextCompat.getColor(this, R.color.text_primary)

        listOf(
            binding.chipTcp to ProbeMode.TCP,
            binding.chipTls to ProbeMode.TLS,
            binding.chipHttp to ProbeMode.HTTP
        ).forEach { (chip, chipMode) ->
            chip.background = if (mode == chipMode) selectedBg else normalBg
            chip.setTextColor(if (mode == chipMode) accentColor else normalColor)
        }
    }

    private fun buildScanConfig(): ScanConfig {
        val app = vm.appSettings.value
        return ScanConfig(
            count = binding.etCount.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(10, 100_000) ?: 500,
            concurrency = binding.sliderConcurrency.value.toInt(),
            timeoutMs = binding.sliderTimeout.value.toLong() * 1000L,
            tries = 3,
            port = binding.etPort.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(1, 65535) ?: 443,
            mode = selectedMode,
            cidr = binding.etCidr.text?.toString()?.trim().orEmpty(),
            sni = app.probeSni,
            wsPath = app.probePath,
            healthyOnly = binding.switchHealthyOnly.isChecked,
            useIpv6 = app.useIpv6,
            stopAfterHealthy = app.stopAfterHealthy,
            skipKnownFailed = app.skipKnownFailed,
            maxResults = app.maxResults
        )
    }

    private fun startScan() {
        hideKeyboard()
        val cfg = buildScanConfig()
        vm.saveForm(
            SettingsRepository.ScanFormState(
                port = cfg.port,
                count = cfg.count,
                concurrency = cfg.concurrency,
                timeoutSec = (cfg.timeoutMs / 1000).toInt(),
                mode = cfg.mode,
                cidr = cfg.cidr,
                healthyOnly = cfg.healthyOnly
            )
        )
        if (cfg.count > 300 || vm.appSettings.value.useForegroundService) {
            requestNotificationPermissionIfNeeded()
        }
        vm.startScan(cfg, vm.appSettings.value.useForegroundService)
        binding.btnScan.text = getString(R.string.btn_stop)
        binding.btnCopy.visibility = View.GONE
        binding.btnShare.visibility = View.GONE
        binding.btnExportMenu.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.recyclerResults.visibility = View.VISIBLE
        binding.tvEta.visibility = View.VISIBLE
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            vm.displayResults.collectLatest { results ->
                adapter.submitList(results)
            }
        }

        lifecycleScope.launch {
            vm.stats.collectLatest { stats ->
                binding.tvStatsTested.text = stats.tested.toString()
                binding.tvStatsHealthy.text = stats.healthy.toString()
                binding.tvStatsFailed.text = stats.failed.toString()
                binding.tvStatsInFlight.text = stats.inFlight.toString()

                if (stats.totalTargets > 0 && vm.scanning.value) {
                    binding.progressScan.setProgressCompat(
                        (stats.tested * 100 / stats.totalTargets).coerceIn(0, 100),
                        true
                    )
                    binding.tvEta.text = getString(
                        R.string.eta_format,
                        stats.etaSeconds,
                        "%.1f".format(stats.ipsPerSecond)
                    )
                }
            }
        }

        lifecycleScope.launch {
            vm.scanning.collectLatest { scanning ->
                binding.progressScan.visibility = if (scanning) View.VISIBLE else View.INVISIBLE
                binding.tvEta.visibility = if (scanning) View.VISIBLE else View.GONE
                if (!scanning) binding.btnScan.text = getString(R.string.btn_scan)
            }
        }

        lifecycleScope.launch {
            vm.done.collectLatest { done ->
                if (!done) return@collectLatest
                val healthy = vm.stats.value.healthy
                val hasResults = vm.displayResults.value.isNotEmpty()
                binding.btnCopy.visibility = View.VISIBLE
                binding.btnShare.visibility = View.VISIBLE
                binding.btnExportMenu.visibility = View.VISIBLE
                if (!hasResults) {
                    binding.tvEmpty.text = getString(R.string.empty_no_results)
                    binding.tvEmpty.visibility = View.VISIBLE
                }
                val stable = vm.compareCount.value
                val msg = if (stable > 0) {
                    getString(R.string.scan_done_compare, healthy, stable)
                } else {
                    getString(R.string.scan_done, healthy)
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                updateColoSpinner()
            }
        }

        lifecycleScope.launch {
            vm.onHealthyFound.collect {
                if (vm.appSettings.value.vibrateOnHealthy) vibrateShort()
            }
        }
    }

    private fun updateColoSpinner() {
        val colos = listOf(getString(R.string.colo_all)) + vm.availableColos()
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, colos)
        binding.spinnerColo.adapter = adapterSpinner
        binding.spinnerColo.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val value = colos[pos]
                vm.setColoFilter(if (pos == 0) null else value)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun vibrateShort() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PermissionChecker.PERMISSION_GRANTED
        ) return
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) vm.stopScan()
    }
}
