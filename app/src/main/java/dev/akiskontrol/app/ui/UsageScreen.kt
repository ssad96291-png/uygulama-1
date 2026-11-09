package dev.akiskontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akiskontrol.core.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
internal fun UsageScreen(state: AppState, onExportCsv: () -> Unit) {
    var period by rememberSaveable { mutableStateOf(QuotaPeriod.DAILY) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var network by rememberSaveable { mutableStateOf<NetworkKind?>(null) }
    val range = dateRange(period, offset)
    val usage = remember(state.usage, range, network) { state.usage.filter { range.contains(it.day) && (network == null || it.network == network) } }
    val download = usage.sumBytes { it.downloadBytes }
    val upload = usage.sumBytes { it.uploadBytes }
    val total = addBytes(download, upload)
    val perApp = remember(usage) { usage.groupBy { it.uid }.map { (uid, records) -> uid to records.sumBytes { addBytes(it.downloadBytes, it.uploadBytes) } }.sortedByDescending { it.second }.filter { it.second > 0 } }
    val apps = remember(state.apps) { state.apps.associateBy { it.uid } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("Kullanım", "Verinin nereye gittiğini gör.") }
        item { PeriodPicker(period) { period = it; offset = 0 } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (offset > -1200) offset-- }, enabled = offset > -1200) { Icon(Icons.Outlined.ChevronLeft, "Önceki dönem") }
                Text(range.title(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { offset++ }, enabled = offset < 0) { Icon(Icons.Outlined.ChevronRight, "Sonraki dönem") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = network == null, onClick = { network = null }, label = { Text("Tümü") }, modifier = Modifier.weight(1f))
                FilterChip(selected = network == NetworkKind.WIFI, onClick = { network = NetworkKind.WIFI }, label = { Text("Wi-Fi") }, modifier = Modifier.weight(1f))
                FilterChip(selected = network == NetworkKind.MOBILE, onClick = { network = NetworkKind.MOBILE }, label = { Text("Mobil") }, modifier = Modifier.weight(1f))
            }
        }
        item {
            Panel {
                Text("Toplam veri", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(bytesLabel(total), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Metric("İndirme", bytesLabel(download), Modifier.weight(1f), Icons.Outlined.SouthWest)
                    Metric("Yükleme", bytesLabel(upload), Modifier.weight(1f), Icons.Outlined.NorthEast)
                }
                if (total > 0) {
                    HorizontalDivider()
                    UsageChart(usage, range)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("● İndirme", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        Text("● Yükleme", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (total == 0L) item {
            InfoEmpty(Icons.Outlined.InsertChartOutlined, "Bu dönemde kullanım yok", "Akış yalnızca koruma açıkken geçen trafiği kaydeder. Önceki sistem kullanımını içe aktaramaz. Bağlantı kurulduğunda gerçek kayıtlar burada görünür.")
        }
        if (perApp.isNotEmpty()) {
            item { SectionTitle("Uygulamaya göre", "İndirme ve yüklemenin toplamı") }
            items(perApp, key = { it.first }) { (uid, amount) ->
                val app = apps[uid]
                val appRecords = usage.filter { it.uid == uid }
                val wifi = appRecords.filter { it.network == NetworkKind.WIFI }.sumBytes { addBytes(it.downloadBytes, it.uploadBytes) }
                val mobile = appRecords.filter { it.network == NetworkKind.MOBILE }.sumBytes { addBytes(it.downloadBytes, it.uploadBytes) }
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (app != null) AppIcon(app) else Icon(Icons.Outlined.Apps, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(app?.label ?: "Eski uygulama · $uid", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(if (network != NetworkKind.MOBILE) "Wi-Fi ${bytesLabel(wifi)}" else null, if (network != NetworkKind.WIFI) "Mobil ${bytesLabel(mobile)}" else null).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(bytesLabel(amount), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(progress = { (amount.toDouble() / total.coerceAtLeast(1)).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            OutlinedButton(onClick = onExportCsv, enabled = state.usage.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tüm kayıtları CSV olarak dışa aktar")
            }
            Text("Ölçüm uygulama verisini temel alır. Operatörün faturaladığı değer farklı olabilir. Haftalar pazartesi, aylar ayın ilk günü başlar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        }
    }
}

@Composable
private fun UsageChart(usage: List<UsageEntry>, range: DateRange) {
    val daily = remember(usage, range) {
        (0..ChronoUnit.DAYS.between(range.start, range.end).toInt()).map { index ->
            val date = range.start.plusDays(index.toLong())
            val day = usage.filter { it.day == date.toString() }
            Triple(date, day.sumBytes { it.downloadBytes }, day.sumBytes { it.uploadBytes })
        }
    }
    val maximum = daily.maxOfOrNull { addBytes(it.second, it.third) }?.coerceAtLeast(1) ?: 1L
    val chartMaximum = daily.maxOfOrNull { it.second.toDouble() + it.third.toDouble() }?.coerceAtLeast(1.0) ?: 1.0
    val downloadColor = MaterialTheme.colorScheme.primary
    val uploadColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Text(if (daily.size == 1) "Gün toplamı" else "Günlük dağılım", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "${range.title()} dönemi veri grafiği. En yüksek günlük kullanım ${bytesLabel(maximum)}." }) {
        val cell = size.width / daily.size
        val barWidth = (cell * 0.66f).coerceAtMost(56.dp.toPx())
        daily.forEachIndexed { index, (_, down, up) ->
            val x = cell * index + (cell - barWidth) / 2
            val downHeight = (down.toDouble() / chartMaximum * size.height).toFloat()
            val upHeight = (up.toDouble() / chartMaximum * size.height).toFloat()
            drawRoundRect(trackColor, Offset(x, 0f), Size(barWidth, size.height), CornerRadius(4.dp.toPx()))
            if (downHeight > 0) drawRect(downloadColor, Offset(x, size.height - downHeight), Size(barWidth, downHeight))
            if (upHeight > 0) drawRect(uploadColor, Offset(x, size.height - downHeight - upHeight), Size(barWidth, upHeight))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${range.start.dayOfMonth}.${range.start.monthValue}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (range.start != range.end) Text("${range.end.dayOfMonth}.${range.end.monthValue}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
