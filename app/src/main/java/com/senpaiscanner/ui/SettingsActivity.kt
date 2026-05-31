package com.senpaiscanner.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.senpaiscanner.R
import com.senpaiscanner.data.FailedIpRepository
import com.senpaiscanner.data.SettingsRepository
import com.senpaiscanner.databinding.ActivitySettingsBinding
import com.senpaiscanner.model.AppSettings
import com.senpaiscanner.worker.ScheduledScanWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings_title)

        lifecycleScope.launch {
            val s = SettingsRepository.appSettings.first()
            binding.etProbeSni.setText(s.probeSni)
            binding.etProbePath.setText(s.probePath)
            binding.etMaxResults.setText(s.maxResults.toString())
            binding.etStopAfter.setText(s.stopAfterHealthy.toString())
            binding.switchSkipFailed.isChecked = s.skipKnownFailed
            binding.switchVibrate.isChecked = s.vibrateOnHealthy
            binding.switchForeground.isChecked = s.useForegroundService
            binding.switchIpv6.isChecked = s.useIpv6
            binding.switchScheduled.isChecked = s.scheduledScanEnabled
            binding.etScheduleHours.setText(s.scheduledIntervalHours.toString())
        }

        binding.btnClearFailed.setOnClickListener {
            lifecycleScope.launch {
                FailedIpRepository.clear()
                Toast.makeText(this@SettingsActivity, R.string.settings_cleared_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val settings = AppSettings(
            probeSni = binding.etProbeSni.text?.toString()?.trim().orEmpty().ifBlank { "speed.cloudflare.com" },
            probePath = binding.etProbePath.text?.toString()?.trim().orEmpty().ifBlank { "/cdn-cgi/trace" },
            maxResults = binding.etMaxResults.text?.toString()?.toIntOrNull()?.coerceIn(50, 2000) ?: 500,
            stopAfterHealthy = binding.etStopAfter.text?.toString()?.toIntOrNull()?.coerceIn(0, 500) ?: 20,
            skipKnownFailed = binding.switchSkipFailed.isChecked,
            vibrateOnHealthy = binding.switchVibrate.isChecked,
            useForegroundService = binding.switchForeground.isChecked,
            useIpv6 = binding.switchIpv6.isChecked,
            scheduledScanEnabled = binding.switchScheduled.isChecked,
            scheduledIntervalHours = binding.etScheduleHours.text?.toString()?.toIntOrNull()?.coerceIn(1, 168) ?: 24
        )
        lifecycleScope.launch {
            SettingsRepository.saveAppSettings(settings)
            if (settings.scheduledScanEnabled) {
                ScheduledScanWorker.schedule(this@SettingsActivity, settings.scheduledIntervalHours)
            } else {
                ScheduledScanWorker.cancel(this@SettingsActivity)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
