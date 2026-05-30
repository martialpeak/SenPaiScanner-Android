package com.senpaiscanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.senpaiscanner.R
import com.senpaiscanner.model.ScanResult

class ResultsAdapter : ListAdapter<ScanResult, ResultsAdapter.VH>(DIFF) {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvIp: TextView = itemView.findViewById(R.id.tv_ip)
        val tvLatency: TextView = itemView.findViewById(R.id.tv_latency)
        val tvLoss: TextView = itemView.findViewById(R.id.tv_loss)
        val tvColo: TextView = itemView.findViewById(R.id.tv_colo)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        val tvTls: TextView = itemView.findViewById(R.id.tv_tls)
        val dot: View = itemView.findViewById(R.id.dot_health)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_result, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = getItem(position)
        val ctx = holder.itemView.context

        holder.tvIp.text = "${r.ip}:${r.port}"
        holder.tvLatency.text = r.latencyLabel
        holder.tvLoss.text = r.lossLabel
        holder.tvColo.text = r.colo.ifEmpty { "—" }
        holder.tvStatus.text = if (r.httpStatus > 0) r.httpStatus.toString() else "—"
        holder.tvTls.text = when {
            r.tlsOk -> "TLS ✓"
            r.probeMode == "tcp" -> "TCP"
            else -> "—"
        }

        val color = when {
            !r.isHealthy -> ContextCompat.getColor(ctx, R.color.bad)
            r.latencyMs < 100 -> ContextCompat.getColor(ctx, R.color.good)
            r.latencyMs < 300 -> ContextCompat.getColor(ctx, R.color.warn)
            else -> ContextCompat.getColor(ctx, R.color.warn)
        }
        holder.dot.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(color)
        )
        holder.tvLatency.setTextColor(color)
    }

    // Need probeMode accessible
    private val ScanResult.probeMode: String get() = when {
        tlsOk -> "tls"
        httpStatus > 0 -> "http"
        else -> "tcp"
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ScanResult>() {
            override fun areItemsTheSame(a: ScanResult, b: ScanResult) =
                a.ip == b.ip && a.port == b.port
            override fun areContentsTheSame(a: ScanResult, b: ScanResult) = a == b
        }
    }
}
