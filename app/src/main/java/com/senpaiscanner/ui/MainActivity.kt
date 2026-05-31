package com.senpaiscanner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.senpaiscanner.R
import com.senpaiscanner.databinding.ActivityMainBinding
import com.senpaiscanner.model.ProbeMode
import com.senpaiscanner.model.ScanConfig
import kotlinx.coroutines.flow.collectLatest
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

        setupRecyclerView()
        setupSliders()
        setupButtons()
        updateModeChips(ProbeMode.HTTP)
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = ResultsAdapter()
        binding.recyclerResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerResults.adapter = adapter
        binding.recyclerResults.itemAnimator = null
    }

    private fun setupSliders() {
        binding.sliderConcurrency.addOnChangeListener { _, value, _ ->
            binding.tvConcurrencyLabel.text = "WORKERS  —  ${value.toInt()}"
        }
        binding.sliderTimeout.addOnChangeListener { _, value, _ ->
            binding.tvTimeoutLabel.text = "TIMEOUT  —  ${value.toInt()}s"
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

        binding.btnCopy.setOnClickListener {
            val ips = vm.getHealthyIps()
            if (ips.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_healthy_ips), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val text = ips.joinToString("\n")
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("CF IPs", text))
            Toast.makeText(this, getString(R.string.copied_ips, ips.size), Toast.LENGTH_SHORT).show()
        }

        // NEW: Share button — share as plain text
        binding.btnShare.setOnClickListener {
            val ips = vm.getHealthyIps()
            if (ips.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_healthy_ips), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, ips.joinToString("\n"))
                putExtra(Intent.EXTRA_SUBJECT, "SenPai Scanner — Healthy IPs")
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
        }

        // NEW: Export CSV
        binding.btnExport.setOnClickListener {
            val csv = vm.exportCsv()
            if (csv.lines().size <= 1) {
                Toast.makeText(this, getString(R.string.no_healthy_ips), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_TEXT, csv)
                putExtra(Intent.EXTRA_SUBJECT, "SenPai Scanner Export")
            }
            startActivity(Intent.createChooser(intent, getString(R.string.export_csv)))
        }

        binding.btnClear.setOnClickListener {
            binding.etConfigUrl.text?.clear()
        }

        // NEW: Help dialog — bilingual FA/EN
        binding.btnHelp.setOnClickListener { showHelpDialog() }

        binding.chipTcp.setOnClickListener { updateModeChips(ProbeMode.TCP) }
        binding.chipTls.setOnClickListener { updateModeChips(ProbeMode.TLS) }
        binding.chipHttp.setOnClickListener { updateModeChips(ProbeMode.HTTP) }
    }

    private fun updateModeChips(mode: ProbeMode) {
        selectedMode = mode
        val selectedBg  = resources.getDrawable(R.drawable.chip_selected_bg, theme)
        val normalBg    = resources.getDrawable(R.drawable.chip_bg, theme)
        val accentColor = resources.getColor(R.color.accent, theme)
        val normalColor = resources.getColor(R.color.text_primary, theme)

        listOf(
            binding.chipTcp to ProbeMode.TCP,
            binding.chipTls to ProbeMode.TLS,
            binding.chipHttp to ProbeMode.HTTP
        ).forEach { (chip, chipMode) ->
            chip.background = if (mode == chipMode) selectedBg else normalBg
            chip.setTextColor(if (mode == chipMode) accentColor else normalColor)
        }
    }

    private fun startScan() {
        hideKeyboard()

        val count       = binding.etCount.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(10, 100_000) ?: 500
        val concurrency = binding.sliderConcurrency.value.toInt()
        val timeout     = binding.sliderTimeout.value.toLong() * 1000L
        val port        = binding.etPort.text?.toString()?.trim()?.toIntOrNull() ?: 443
        val cidr        = binding.etCidr.text?.toString()?.trim() ?: ""
        val configUrl   = binding.etConfigUrl.text?.toString()?.trim() ?: ""
        val healthyOnly = binding.switchHealthyOnly.isChecked   // NEW toggle

        var cfg = ScanConfig(
            count       = count,
            concurrency = concurrency,
            timeoutMs   = timeout,
            tries       = 3,
            port        = port,
            mode        = selectedMode,
            cidr        = cidr,
            configUrl   = configUrl,
            healthyOnly = healthyOnly
        )

        if (configUrl.isNotEmpty()) {
            cfg = vm.applyConfigUrl(configUrl).copy(
                count       = count,
                concurrency = concurrency,
                timeoutMs   = timeout,
                healthyOnly = healthyOnly
            )
        }

        vm.startScan(cfg)
        binding.btnScan.text = getString(R.string.btn_stop)
        binding.btnCopy.visibility = View.GONE
        binding.btnShare.visibility = View.GONE
        binding.btnExport.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.recyclerResults.visibility = View.VISIBLE
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            vm.results.collectLatest { results ->
                adapter.submitList(results)
                val lm = binding.recyclerResults.layoutManager as? LinearLayoutManager
                if (results.isNotEmpty() && (lm == null || lm.findFirstVisibleItemPosition() <= 0)) {
                    binding.recyclerResults.scrollToPosition(0)
                }
            }
        }

        lifecycleScope.launch {
            vm.stats.collectLatest { stats ->
                binding.tvStatsTested.text  = stats.tested.toString()
                binding.tvStatsHealthy.text = stats.healthy.toString()
                binding.tvStatsFailed.text  = stats.failed.toString()
                binding.tvStatsInFlight.text = stats.inFlight.toString()

                val cfg = vm.config
                if (cfg.count > 0 && stats.tested > 0) {
                    val progress = (stats.tested * 100 / cfg.count).coerceIn(0, 100)
                    binding.progressScan.setProgressCompat(progress, true)
                }
            }
        }

        lifecycleScope.launch {
            vm.scanning.collectLatest { scanning ->
                binding.progressScan.visibility = if (scanning) View.VISIBLE else View.INVISIBLE
                if (!scanning) binding.btnScan.text = getString(R.string.btn_scan)
            }
        }

        lifecycleScope.launch {
            vm.done.collectLatest { done ->
                if (done) {
                    binding.btnCopy.visibility   = View.VISIBLE
                    binding.btnShare.visibility  = View.VISIBLE
                    binding.btnExport.visibility = View.VISIBLE
                    val healthy = vm.stats.value.healthy
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.scan_done, healthy),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ─── Help Dialog — bilingual ──────────────────────────────────────────────
    private fun showHelpDialog() {
        val message = """
🇮🇷 راهنما
──────────────
• حالت TCP: فقط اتصال پایه چک می‌شه (سریع‌ترین)
• حالت TLS: TLS handshake کامل انجام میشه
• حالت HTTP: درخواست HTTP واقعی + کشف datacenter (پیشنهادی)

• Config URL: آدرس VLESS/Trojan/VMess رو وارد کن تا SNI و پورت خودکار تنظیم بشه
• CIDR: محدوده IP سفارشی — مثلاً 104.16.0.0/14
• Workers: تعداد اتصال همزمان (50 پیش‌فرض)
• Healthy Only: فقط IPهای سالم نمایش داده بشن

──────────────
🇬🇧 Guide
──────────────
• TCP mode: raw connect only — fastest, minimal info
• TLS mode: full TLS handshake — confirms encryption works
• HTTP mode: real HTTP request + datacenter detection (recommended)

• Config URL: paste VLESS/Trojan/VMess link — SNI & port auto-detected
• CIDR: custom IP range e.g. 104.16.0.0/14
• Workers: concurrent connections (50 default)
• Healthy Only: hide failed IPs from list

──────────────
⭐ Quality
• ★★★  < 80ms
• ★★☆  80–200ms
• ★☆☆  > 200ms
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("SenPai Scanner — Help / راهنما")
            .setMessage(message)
            .setPositiveButton("OK / باشه", null)
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    override fun onDestroy() {
        super.onDestroy()
        vm.stopScan()
    }
}
