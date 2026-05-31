package com.senpaiscanner

import android.app.Application
import com.senpaiscanner.data.FailedIpRepository
import com.senpaiscanner.data.ScanHistoryRepository
import com.senpaiscanner.data.SettingsRepository
import com.senpaiscanner.scanner.IpSource

class SenPaiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsRepository.init(this)
        FailedIpRepository.init(this)
        ScanHistoryRepository.init(this)
        IpSource.init(this)
    }
}
