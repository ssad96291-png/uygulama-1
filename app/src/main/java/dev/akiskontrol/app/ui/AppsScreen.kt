package dev.akiskontrol.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akiskontrol.app.data.AppRepository
import dev.akiskontrol.app.vpn.AkisVpnService
import dev.akiskontrol.core.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.ceil

@Composable
internal fun AppsScreen(
    state: AppState,
    repository: AppRepository,
    onEdit: (AppEntry) -> Unit,
    onRulesSaved: () -> Unit,
    feedback: (String) -> Unit,
    onToggleProtection: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var blockedOnly by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var showTemporaryActions by rememberSaveable { mutableStateOf(false) }
    var actionsMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var bulkEditor by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    val showSystem = state.settings.advanced && state.settings.showSystemApps
    val visible = remember(state.apps, state.rules, state.settings, search, blockedOnly) {
        state.apps.filter { app ->
            val rule = state.rules[app.uid]
            (showSystem || !app.isSystem) &&
                (search.isBlank() || app.label.contains(search, true) || app.packageName.contains(search, true)) &&
                (!blockedOnly || !(rule?.wifi?.allowed ?: state.settings.defaultWifiAllowed) || !(rule?.mobile?.allowed ?: state.settings.defaultMobileAllowed))
        }.sortedWith(compareBy<AppEntry> { it.label.lowercase(Turkish) }.thenBy { it.packageName })
    }
    LaunchedEffect(showSystem, state.apps) {
        val eligible = state.apps.filter { showSystem || !it.isSystem }.map { it.uid }.toSet()
        selected = selected.intersect(eligible)
    }
    val today = LocalDate.now().toString()
    val todayUsage = state.usage.filter { it.day == today }
    val wifiUsage = todayUsage.filter { it.network == NetworkKind.WIFI }.sumBytes { addBytes(it.downloadBytes, it.uploadBytes) }
    val mobileUsage = todayUsage.filter { it.network == NetworkKind.MOBILE }.sumBytes { addBytes(it.downloadBytes, it.uploadBytes) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Waves, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Akış", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Ayarları aç") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Uygulamalar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { actionsMenu = true }) { Icon(Icons.Outlined.MoreVert, "Uygulama listesi seçenekleri") }
                    DropdownMenu(expanded = actionsMenu, onDismissRequest = { actionsMenu = false }) {
                        DropdownMenuItem(text = { Text(if (selectionMode) "Toplu seçimi bitir" else "Toplu düzenle") }, leadingIcon = { Icon(Icons.Outlined.Layers, null) }, onClick = { selectionMode = !selectionMode; if (!selectionMode) selected = emptySet(); actionsMenu = false })
                        DropdownMenuItem(text = { Text(if (blockedOnly) "Tüm uygulamaları göster" else "Yalnız engellileri göster") }, leadingIcon = { Icon(Icons.Outlined.Block, null) }, onClick = { blockedOnly = !blockedOnly; actionsMenu = false })
                        DropdownMenuItem(text = { Text(if (showTemporaryActions) "Geçici izin düğmelerini gizle" else "Geçici izin düğmelerini göster") }, leadingIcon = { Icon(Icons.Outlined.Timer, null) }, onClick = { showTemporaryActions = !showTemporaryActions; actionsMenu = false })
                        if (state.settings.advanced) DropdownMenuItem(text = { Text(if (showSystem) "Sistem uygulamalarını gizle" else "Sistem uygulamalarını göster") }, leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, null) }, onClick = {
                            repository.saveSettings(state.settings.copy(showSystemApps = !state.settings.showSystemApps)); actionsMenu = false
                        })
                        DropdownMenuItem(text = { Text("Listeyi yenile") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { repository.refreshApps(); feedback("Uygulama listesi yenileniyor"); actionsMenu = false })
                        DropdownMenuItem(text = { Text("Son kural değişikliğini geri al") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Undo, null) }, onClick = {
                            feedback(if (repository.undoLastRules()) "Son kural değişikliği geri alındı" else "Geri alınacak kural değişikliği yok"); actionsMenu = false
                        })
                    }
                }
            }
            CompactProtectionCard(state, onToggleProtection)
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints {
                val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DailyNetworkCard("Wi-Fi", wifiUsage, Icons.Outlined.Wifi, Modifier.fillMaxWidth())
                    DailyNetworkCard("Mobil", mobileUsage, Icons.Outlined.SignalCellularAlt, Modifier.fillMaxWidth())
                } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DailyNetworkCard("Wi-Fi", wifiUsage, Icons.Outlined.Wifi, Modifier.weight(1f))
                    DailyNetworkCard("Mobil", mobileUsage, Icons.Outlined.SignalCellularAlt, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Uygulama ara") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)),
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Outlined.Close, "Aramayı temizle") } },
            )
            if (blockedOnly || showSystem) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (blockedOnly) InputChip(selected = true, onClick = { blockedOnly = false }, label = { Text("Engelliler") }, trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) })
                if (showSystem) InputChip(selected = true, onClick = { repository.saveSettings(state.settings.copy(showSystemApps = false)) }, label = { Text("Sistem dahil") }, trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) })
            }
            Spacer(Modifier.height(12.dp))
        }
        if (selectionMode) item {
            Panel {
                Text("${selected.size} uygulama kimliği seçildi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Aynı Android kimliğini paylaşan uygulamalar birlikte yönetilir. Toplu işlem, seçilenlerin iki ağdaki tüm kurallarını değiştirir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selected = visible.filterNot { it.isSystem }.map { it.uid }.toSet() }, modifier = Modifier.weight(1f)) { Text("Kullanıcı uygulamalarını seç") }
                    TextButton(onClick = { selected = emptySet() }) { Text("Temizle") }
                }
                Button(onClick = { bulkEditor = true }, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Seçilenlerin kurallarını düzenle") }
                TextButton(onClick = { selectionMode = false; selected = emptySet() }, modifier = Modifier.fillMaxWidth()) { Text("Seçimi bitir") }
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Uygulama", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (!stacked) {
                        Text("Wi-Fi", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("Mobil", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
        if (visible.isEmpty()) item {
            InfoEmpty(Icons.Outlined.Search, if (state.apps.isEmpty()) "Uygulamalar hazırlanıyor" else "Eşleşen uygulama yok", if (state.apps.isEmpty()) "Cihazdaki internet erişimi olan uygulamalar burada listelenir. Listeyi yenileyebilirsin." else "Aramayı veya filtreleri değiştirerek tekrar dene.")
        }
        itemsIndexed(visible, key = { _, app -> app.packageName }) { index, app ->
            val rule = state.rules[app.uid] ?: AppRule(app.uid, NetworkRule(allowed = state.settings.defaultWifiAllowed), NetworkRule(allowed = state.settings.defaultMobileAllowed))
            val temporaryUntil = state.temporaryAllowUntil[app.uid]?.takeIf { it > now }
            AppRuleRow(
                app = app, rule = rule, first = index == 0, last = index == visible.lastIndex,
                selectionMode = selectionMode, selected = app.uid in selected,
                temporaryUntil = temporaryUntil, now = now, showTemporaryAction = showTemporaryActions,
                onSelect = { selected = if (app.uid in selected) selected - app.uid else selected + app.uid },
                onEdit = { onEdit(app) },
                onWifi = { repository.saveRule(rule.copy(wifi = rule.wifi.copy(allowed = !rule.wifi.allowed))) },
                onMobile = { repository.saveRule(rule.copy(mobile = rule.mobile.copy(allowed = !rule.mobile.allowed))) },
                onTemporary = {
                    if (temporaryUntil != null) { repository.clearTemporary(app.uid); feedback("${app.label}: geçici izin kaldırıldı") }
                    else { repository.allowTemporarily(app.uid, 15); feedback("${app.label}: 15 dakika izin verildi; toplam kota geçerli") }
                },
            )
        }
        item {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = {
                if (selectionMode && selected.isNotEmpty()) bulkEditor = true
                else { selectionMode = !selectionMode; if (!selectionMode) selected = emptySet() }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Layers, null, Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(if (selectionMode) { if (selected.isEmpty()) "Seçimi bitir" else "${selected.size} seçileni düzenle" } else "Toplu düzenle")
            }
            Text("${visible.size} uygulama${if (showSystem) " · sistem dahil" else " · kullanıcı uygulamaları"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            Text("Kurallar koruma açıkken uygulanır. Sistem uygulamalarını engellemek bildirimleri ve cihaz hizmetlerini etkileyebilir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        }
    }
    if (bulkEditor && selected.isNotEmpty()) {
        RuleEditorDialog("${selected.size} kimliğe ortak kurallar", repository.ruleFor(selected.first()), { bulkEditor = false }) { wifi, mobile ->
            repository.saveRules(selected.map { AppRule(it, wifi, mobile) })
            bulkEditor = false
            selectionMode = false
            selected = emptySet()
            onRulesSaved()
        }
    }
}

@Composable
private fun CompactProtectionCard(state: AppState, onToggle: () -> Unit) {
    val running = state.status == ProtectionStatus.RUNNING
    val starting = state.status == ProtectionStatus.STARTING
    val canStop = running || starting || (state.status == ProtectionStatus.ERROR && AkisVpnService.hasActiveTunnel)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (running) Icons.Outlined.VerifiedUser else Icons.Outlined.Shield, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(when (state.status) {
                    ProtectionStatus.RUNNING -> "Koruma açık"
                    ProtectionStatus.STARTING -> "Bağlanıyor…"
                    ProtectionStatus.ERROR -> "Bağlantı durdu"
                    ProtectionStatus.STOPPED -> "Koruma kapalı"
                }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(if (state.network == NetworkKind.WIFI) "Wi-Fi bağlantısı" else state.network.label(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                if (state.status == ProtectionStatus.ERROR) Text(state.statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            FilledIconButton(onClick = onToggle, modifier = Modifier.size(48.dp).semantics { contentDescription = if (canStop) "Korumayı durdur" else "Korumayı başlat" }, shape = CircleShape) {
                if (starting) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun DailyNetworkCard(label: String, used: Long, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(icon, null, Modifier.size(25.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Bugün", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$label · ${bytesLabel(used)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AppRuleRow(
    app: AppEntry, rule: AppRule, first: Boolean, last: Boolean,
    selectionMode: Boolean, selected: Boolean, temporaryUntil: Long?, now: Long, showTemporaryAction: Boolean,
    onSelect: () -> Unit, onEdit: () -> Unit, onWifi: () -> Unit, onMobile: () -> Unit, onTemporary: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp, bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
            val stacked = maxWidth < 332.dp || LocalDensity.current.fontScale > 1.3f
            val rowTitle: @Composable RowScope.() -> Unit = {
                if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onSelect() }, modifier = Modifier.semantics { contentDescription = "${app.label} seç" })
                else AppIcon(app, Modifier.size(40.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(app.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis)
                    Text(appRuleSummary(rule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis)
                    if (app.isSystem) Text("Sistem uygulaması", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column {
                if (stacked) {
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = if (selectionMode) onSelect else onEdit).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = rowTitle)
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Wi-Fi", style = MaterialTheme.typography.bodyMedium); NetworkSwitch(app.label, "Wi-Fi", rule.wifi.allowed, onWifi) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Mobil", style = MaterialTheme.typography.bodyMedium); NetworkSwitch(app.label, "Mobil veri", rule.mobile.allowed, onMobile) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clickable(role = Role.Button, onClick = if (selectionMode) onSelect else onEdit).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = rowTitle)
                        NetworkSwitch(app.label, "Wi-Fi", rule.wifi.allowed, onWifi)
                        NetworkSwitch(app.label, "Mobil veri", rule.mobile.allowed, onMobile)
                    }
                }
                if (!selectionMode && (showTemporaryAction || temporaryUntil != null)) {
                    TextButton(onClick = onTemporary, modifier = Modifier.padding(horizontal = 8.dp), contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
                        Icon(if (temporaryUntil == null) Icons.Outlined.Timer else Icons.Outlined.TimerOff, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (temporaryUntil == null) "15 dakika serbest bırak" else "Geçici izin: ${ceil((temporaryUntil - now) / 60_000.0).toInt()} dk · İptal et", style = MaterialTheme.typography.labelMedium)
                    }
                    if (temporaryUntil != null) Text("Geçici izin uygulama sınırlarını kaldırır; toplam kota geçerlidir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun NetworkSwitch(appLabel: String, network: String, allowed: Boolean, onToggle: () -> Unit) {
    Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
        Switch(checked = allowed, onCheckedChange = { onToggle() }, modifier = Modifier.semantics { contentDescription = "$appLabel · $network internet erişimi" })
    }
}

private fun appRuleSummary(rule: AppRule): String = when {
    !rule.wifi.allowed && !rule.mobile.allowed -> "İnternet erişimi kapalı"
    !rule.wifi.allowed -> "Wi-Fi erişimi kapalı"
    !rule.mobile.allowed -> "Mobil erişim kapalı"
    rule.wifi.downloadBps > 0L -> "Wi-Fi · ${speedLabel(rule.wifi.downloadBps)} sınır"
    rule.mobile.downloadBps > 0L -> "Mobil · ${speedLabel(rule.mobile.downloadBps)} sınır"
    rule.wifi.uploadBps > 0L -> "Wi-Fi yükleme · ${speedLabel(rule.wifi.uploadBps)}"
    rule.mobile.uploadBps > 0L -> "Mobil yükleme · ${speedLabel(rule.mobile.uploadBps)}"
    rule.wifi.quotaBytes > 0L -> "Wi-Fi · ${bytesLabel(rule.wifi.quotaBytes)} kota"
    rule.mobile.quotaBytes > 0L -> "Mobil · ${bytesLabel(rule.mobile.quotaBytes)} kota"
    else -> "Sınırsız"
}
