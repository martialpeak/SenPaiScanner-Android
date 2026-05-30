package com.senpaiscanner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
                binding.btnScan.text = "▶  SCAN"
            } else {
                startScan()
            }
        }

        binding.btnCopy.setOnClickListener {
            val ips = vm.getHealthyIps()
            if (ips.isEmpty()) {
                Toast.makeText(this, "No healthy IPs yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val text = ips.joinToString("\n")
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("CF IPs", text))
            Toast.makeText(this, "✓ Copied ${ips.size} IPs", Toast.LENGTH_SHORT).show()
        }

        binding.btnClear.setOnClickListener {
            binding.etConfigUrl.text?.clear()
        }

        binding.chipTcp.setOnClickListener { updateModeChips(ProbeMode.TCP) }
        binding.chipTls.setOnClickListener { updateModeChips(ProbeMode.TLS) }
        binding.chipHttp.setOnClickListener { updateModeChips(ProbeMode.HTTP) }
    }

    private fun updateModeChips(mode: ProbeMode) {
        selectedMode = mode
        val selectedBg = resources.getDrawable(com.senpaiscanner.R.drawable.chip_selected_bg, theme)
        val normalBg = resources.getDrawable(com.senpaiscanner.R.drawable.chip_bg, theme)
        val accentColor = resources.getColor(com.senpaiscanner.R.color.accent, theme)
        val normalColor = resources.getColor(com.senpaiscanner.R.color.text_primary, theme)

        binding.chipTcp.background = if (mode == ProbeMode.TCP) selectedBg else normalBg
        binding.chipTcp.setTextColor(if (mode == ProbeMode.TCP) accentColor else normalColor)
        binding.chipTls.background = if (mode == ProbeMode.TLS) selectedBg else normalBg
        binding.chipTls.setTextColor(if (mode == ProbeMode.TLS) accentColor else normalColor)
        binding.chipHttp.background = if (mode == ProbeMode.HTTP) selectedBg else normalBg
        binding.chipHttp.setTextColor(if (mode == ProbeMode.HTTP) accentColor else normalColor)
    }

    private fun startScan() {
        hideKeyboard()

        val count = binding.etCount.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(10, 100_000) ?: 500
        val concurrency = binding.sliderConcurrency.value.toInt()
        val timeout = binding.sliderTimeout.value.toLong() * 1000L
        val port = binding.etPort.text?.toString()?.trim()?.toIntOrNull() ?: 443
        val cidr = binding.etCidr.text?.toString()?.trim() ?: ""
        val configUrl = binding.etConfigUrl.text?.toString()?.trim() ?: ""

        var cfg = ScanConfig(
            count = count,
            concurrency = concurrency,
            timeoutMs = timeout,
            tries = 3,
            port = port,
            mode = selectedMode,
            cidr = cidr,
            configUrl = configUrl
        )

        if (configUrl.isNotEmpty()) {
            cfg = vm.applyConfigUrl(configUrl).copy(
                count = count,
                concurrency = concurrency,
                timeoutMs = timeout
            )
        }

        vm.startScan(cfg)
        binding.btnScan.text = "■  STOP"
        binding.btnCopy.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.recyclerResults.visibility = View.VISIBLE
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            vm.results.collectLatest { results ->
                adapter.submitList(results.toList())
            }
        }

        lifecycleScope.launch {
            vm.stats.collectLatest { stats ->
                binding.tvStatsTested.text = stats.tested.toString()
                binding.tvStatsHealthy.text = stats.healthy.toString()
                binding.tvStatsFailed.text = stats.failed.toString()
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
                if (!scanning) binding.btnScan.text = "▶  SCAN"
            }
        }

        lifecycleScope.launch {
            vm.done.collectLatest { done ->
                if (done) {
                    binding.btnCopy.visibility = View.VISIBLE
                    val healthy = vm.stats.value.healthy
                    Toast.makeText(
                        this@MainActivity,
                        "✓ Done — $healthy healthy IPs found",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
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
