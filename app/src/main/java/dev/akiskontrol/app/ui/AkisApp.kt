package dev.akiskontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akiskontrol.app.data.AppRepository
import dev.akiskontrol.app.vpn.AkisVpnService
import dev.akiskontrol.core.*
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Ana ekran", Icons.Outlined.Home),
    APPS("Uygulamalar", Icons.Outlined.Apps),
    USAGE("Kullanım", Icons.Outlined.BarChart),
    SETTINGS("Ayarlar", Icons.Outlined.Settings),
}

@Composable
fun AkisApp(
    repository: AppRepository,
    onToggleProtection: () -> Unit,
    onExportCsv: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenVpnSettings: () -> Unit,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.APPS) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<AppEntry?>(null) }
    var quotaEditor by rememberSaveable { mutableStateOf(false) }
    val feedback: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
    val savedRules: () -> Unit = {
        scope.launch {
            val answer = snackbar.showSnackbar("Kurallar kaydedildi", actionLabel = "Geri al", duration = SnackbarDuration.Long)
            if (answer == SnackbarResult.ActionPerformed) {
                snackbar.showSnackbar(if (repository.undoLastRules()) "Önceki kurallar geri yüklendi" else "Geri alınacak değişiklik yok")
            }
        }
    }
    LaunchedEffect(repository) { repository.refreshApps() }
    AkisTheme(state.settings.darkTheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (editing == null) AkisNavigation(destination) { destination = it }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val editedApp = editing
                if (editedApp != null) {
                    AppRuleEditorScreen(editedApp, repository.ruleFor(editedApp.uid), state, { editing = null }) { wifi, mobile ->
                        repository.saveRule(AppRule(editedApp.uid, wifi, mobile))
                        editing = null
                        savedRules()
                    }
                } else when (destination) {
                    Destination.HOME -> Dashboard(state, onToggleProtection, { destination = Destination.APPS }, { destination = Destination.USAGE }, { quotaEditor = true })
                    Destination.APPS -> AppsScreen(state, repository, onEdit = { editing = it }, onRulesSaved = savedRules, feedback = feedback, onToggleProtection = onToggleProtection, onOpenSettings = { destination = Destination.SETTINGS })
                    Destination.USAGE -> UsageScreen(state, onExportCsv)
                    Destination.SETTINGS -> SettingsScreen(state, repository, onExportBackup, onImportBackup, onOpenVpnSettings, feedback)
                }
            }
        }
        if (quotaEditor) GlobalQuotaDialog(state.globalQuota, { quotaEditor = false }) { quota ->
            repository.setGlobalQuota(quota)
            quotaEditor = false
            feedback("Toplam kota kaydedildi")
        }
    }
}

@Composable
private fun AkisNavigation(destination: Destination, onSelect: (Destination) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Destination.entries.forEach { item ->
                    val selected = destination == item
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                    ) {
                        Column(
                            modifier = Modifier.selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item) }).heightIn(min = 58.dp).padding(horizontal = 2.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                        ) {
                            val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(item.icon, null, Modifier.size(24.dp), tint = tint)
                            Text(item.label, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashboard(state: AppState, onToggle: () -> Unit, onApps: () -> Unit, onUsage: () -> Unit, onQuota: () -> Unit) {
    val running = state.status == ProtectionStatus.RUNNING
    val starting = state.status == ProtectionStatus.STARTING
    val canStop = running || starting || (state.status == ProtectionStatus.ERROR && AkisVpnService.hasActiveTunnel)
    val today = LocalDate.now().toString()
    val todayUsage = state.usage.filter { it.day == today }
    val download = todayUsage.sumBytes { it.downloadBytes }
    val upload = todayUsage.sumBytes { it.uploadBytes }
    val wifi = todayUsage.filter { it.network == NetworkKind.WIFI }.sumBytes { addBytes(it.downloadBytes, it.uploadBytes) }
    val mobile = todayUsage.filter { it.network == NetworkKind.MOBILE }.sumBytes { addBytes(it.downloadBytes, it.uploadBytes) }
    val blocked = state.apps.map { it.uid }.distinct().count { uid ->
        val rule = state.rules[uid]
        !(rule?.wifi?.allowed ?: state.settings.defaultWifiAllowed) || !(rule?.mobile?.allowed ?: state.settings.defaultMobileAllowed)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            PageHeading("Akış", "Bağlantının kontrolü sende.") {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Outlined.Waves, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(if (running) Icons.Outlined.VerifiedUser else Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(when (state.status) { ProtectionStatus.RUNNING -> "Koruma etkin"; ProtectionStatus.STARTING -> "Bağlanıyor…"; ProtectionStatus.ERROR -> "Bağlantı durdu"; ProtectionStatus.STOPPED -> "Koruma kapalı" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(state.network.label(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(state.statusMessage, style = MaterialTheme.typography.bodyMedium, color = if (state.status == ProtectionStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), contentPadding = PaddingValues(14.dp)) {
                        if (starting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(if (canStop) "Korumayı durdur" else "Korumayı başlat", fontWeight = FontWeight.SemiBold)
                    }
                    Text("Akış, bu cihazda yerel bir VPN kullanır. Kurallar koruma açıkken uygulanır; trafik uzak bir VPN sunucusuna gönderilmez.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Bugünkü kullanım", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onUsage) { Icon(Icons.Outlined.ArrowForward, "Kullanım ayrıntıları") }
                }
                Text(bytesLabel(addBytes(download, upload)), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Metric("İndirme", bytesLabel(download), Modifier.weight(1f), Icons.Outlined.SouthWest)
                    Metric("Yükleme", bytesLabel(upload), Modifier.weight(1f), Icons.Outlined.NorthEast)
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Metric("Wi-Fi", bytesLabel(wifi), Modifier.weight(1f), Icons.Outlined.Wifi)
                    Metric("Mobil veri", bytesLabel(mobile), Modifier.weight(1f), Icons.Outlined.SignalCellularAlt)
                }
                if (todayUsage.isEmpty()) Text("Henüz kayıt yok. Kullanım, koruma açıkken geçen trafikten ölçülür.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Uygulama kuralları", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${state.apps.size} uygulama · $blocked uygulamada ağ engeli", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(onClick = onApps, modifier = Modifier.fillMaxWidth()) { Text("Uygulamaları yönet") }
            }
        }
        item {
            Panel {
                Text("Toplam veri kotası", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Tüm uygulamalar için ${state.globalQuota.period.label().lowercase(Turkish)} ortak sınır. Kota dolunca ilgili ağ engellenir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                QuotaSummary("Wi-Fi", NetworkKind.WIFI, state.globalQuota.wifiBytes, state)
                QuotaSummary("Mobil veri", NetworkKind.MOBILE, state.globalQuota.mobileBytes, state)
                TextButton(onClick = onQuota, modifier = Modifier.align(Alignment.End)) { Text("Kotayı düzenle") }
            }
        }
    }
}

@Composable
private fun QuotaSummary(label: String, network: NetworkKind, limit: Long, state: AppState) {
    val range = DateRange(periodStart(LocalDate.now(), state.globalQuota.period, state.globalQuota.renewalDay), LocalDate.now())
    val used = state.usage.filter { it.network == network && range.contains(it.day) }.sumBytes { addBytes(it.downloadBytes, it.uploadBytes) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(if (limit == 0L) "Sınırsız" else "${bytesLabel(used)} / ${bytesLabel(limit)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (limit > 0) LinearProgressIndicator(progress = { (used.toDouble() / limit).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth(), color = if (used >= limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}
