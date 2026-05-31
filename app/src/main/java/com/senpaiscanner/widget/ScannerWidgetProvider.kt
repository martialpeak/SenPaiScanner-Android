package com.senpaiscanner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.senpaiscanner.R
import com.senpaiscanner.data.ScanHistoryRepository
import com.senpaiscanner.ui.MainActivity
import kotlinx.coroutines.runBlocking

class ScannerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val summary = runBlocking { ScanHistoryRepository.widgetSummary() }
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_scanner).apply {
                setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                setTextViewText(R.id.widget_summary, summary)
                setOnClickPendingIntent(R.id.widget_root, openAppPending(context))
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun openAppPending(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
