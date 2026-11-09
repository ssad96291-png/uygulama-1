package dev.akiskontrol.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.akiskontrol.app.MainActivity
import dev.akiskontrol.app.R
import dev.akiskontrol.core.*
import java.time.LocalDate

/** Local, once-per-period warnings. No remote analytics or notification service. */
internal class QuotaAlerts(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences("quota-alerts", Context.MODE_PRIVATE)
    private val sent = LinkedHashSet(preferences.getStringSet("sent", emptySet()) ?: emptySet())
    init { manager.createNotificationChannel(NotificationChannel(CHANNEL, "Kota uyarıları", NotificationManager.IMPORTANCE_DEFAULT)) }
    @Synchronized fun evaluate(state: AppState) {
        if (state.status != ProtectionStatus.RUNNING || !manager.areNotificationsEnabled()) return
        val today = LocalDate.now()
        val byApp = state.usage.groupBy { it.uid to it.network }
        val byNetwork = state.usage.groupBy { it.network }
        val names = state.apps.associate { it.uid to it.label }
        fun amount(entries: List<UsageEntry>, start: LocalDate): Long = entries.asSequence()
            .filter { it.day >= start.toString() && it.day <= today.toString() }
            .fold(0L) { sum, item -> saturate(saturate(sum,item.downloadBytes),item.uploadBytes) }
        state.rules.forEach { (uid, rule) ->
            listOf(NetworkKind.WIFI to rule.wifi, NetworkKind.MOBILE to rule.mobile).forEach { (network, limits) ->
                if (limits.quotaBytes > 0) {
                    val start = periodStart(today, limits.period, limits.renewalDay)
                    val used = amount(byApp[uid to network].orEmpty(),start)
                    warn("$uid/$network/$start/${limits.quotaBytes}", names[uid] ?: "Uygulama", network, used, limits.quotaBytes,
                        if (limits.quotaAction == QuotaAction.BLOCK) "Kota dolduğunda erişim engellenir." else "Kota dolduğunda düşük hız uygulanır.")
                }
            }
        }
        listOf(NetworkKind.WIFI to state.globalQuota.wifiBytes, NetworkKind.MOBILE to state.globalQuota.mobileBytes).forEach { (network,limit) ->
            if (limit > 0) {
                val start = periodStart(today,state.globalQuota.period,state.globalQuota.renewalDay)
                warn("global/$network/$start/$limit", "Toplam veri kotası",network,amount(byNetwork[network].orEmpty(),start),limit,"Kota dolduğunda bu ağdaki trafik engellenir.")
            }
        }
    }
    private fun warn(key: String, name: String, network: NetworkKind, used: Long, limit: Long, action: String) {
        val ratio = used.toDouble()/limit
        val threshold = when { ratio >= 1.0 -> 100; ratio >= 0.95 -> 95; ratio >= 0.8 -> 80; else -> return }
        val noticeKey = "$key/$threshold"
        if (noticeKey in sent) return
        val pending = PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val networkName = if (network==NetworkKind.WIFI) "Wi-Fi" else "Mobil veri"
        val message = "$networkName kotasının %$threshold düzeyine ulaşıldı. $action"
        val notification = NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_status)
            .setContentTitle(name).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending).setAutoCancel(true).build()
        try { manager.notify(1000 + (key.hashCode() and 0x3fffffff),notification) } catch (_: SecurityException) { return }
        // Crossing several thresholds in one transfer should not send older warnings afterward.
        listOf(80,95,100).filter { it<=threshold }.forEach { sent.add("$key/$it") }
        while (sent.size>1200) sent.remove(sent.first())
        preferences.edit().putStringSet("sent",sent.toSet()).apply()
    }
    private fun saturate(a:Long,b:Long):Long = if(a>Long.MAX_VALUE-b) Long.MAX_VALUE else a+b
    companion object { private const val CHANNEL="akis.quotas" }
}
