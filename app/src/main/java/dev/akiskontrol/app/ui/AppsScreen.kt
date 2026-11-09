package dev.akiskontrol.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akiskontrol.app.data.AppRepository
import dev.akiskontrol.core.*
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
internal fun AppsScreen(state: AppState, repository: AppRepository, onEdit: (AppEntry) -> Unit, onRulesSaved: () -> Unit, feedback: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var blockedOnly by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            PageHeading("Uygulamalar", "Her uygulama, her ağ için ayrı kontrol.") {
                IconButton(onClick = { repository.refreshApps(); feedback("Uygulama listesi yenileniyor") }) { Icon(Icons.Outlined.Refresh, "Listeyi yenile") }
            }
        }
        item {
            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Uygulama ara") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(16.dp),
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Outlined.Close, "Aramayı temizle") } })
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = blockedOnly, onClick = { blockedOnly = !blockedOnly }, label = { Text("Engelliler") }, leadingIcon = { Icon(Icons.Outlined.Block, null, Modifier.size(16.dp)) })
                FilterChip(selected = selectionMode, onClick = { selectionMode = !selectionMode; if (!selectionMode) selected = emptySet() }, label = { Text(if (selectionMode) "Seçimi bitir" else "Toplu seç") }, leadingIcon = { Icon(Icons.Outlined.Checklist, null, Modifier.size(16.dp)) })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { feedback(if (repository.undoLastRules()) "Son kural değişikliği geri alındı" else "Geri alınacak kural değişikliği yok") }) { Icon(Icons.Outlined.Undo, "Son kural değişikliğini geri al") }
            }
            if (state.settings.advanced) {
                SettingToggle("Sistem uygulamaları", "Ağ ve cihaz hizmetlerini de listele.", state.settings.showSystemApps) {
                    repository.saveSettings(state.settings.copy(showSystemApps = it))
                }
            }
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
            }
        }
        item { Text("${visible.size} uygulama${if (showSystem) " · sistem dahil" else " · kullanıcı uygulamaları"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (visible.isEmpty()) item {
            InfoEmpty(Icons.Outlined.Search, if (state.apps.isEmpty()) "Uygulamalar hazırlanıyor" else "Eşleşen uygulama yok", if (state.apps.isEmpty()) "Cihazdaki internet erişimi olan uygulamalar burada listelenir. Listeyi yenileyebilirsin." else "Aramayı veya filtreleri değiştirerek tekrar dene.")
        }
        items(visible, key = { it.packageName }) { app ->
            val rule = state.rules[app.uid] ?: AppRule(app.uid, NetworkRule(allowed = state.settings.defaultWifiAllowed), NetworkRule(allowed = state.settings.defaultMobileAllowed))
            val temporaryUntil = state.temporaryAllowUntil[app.uid]?.takeIf { it > now }
            AppRuleCard(app, rule, selectionMode, app.uid in selected, temporaryUntil, now,
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
        item { Text("Kurallar koruma açıkken uygulanır. Sistem uygulamalarını engellemek bildirimleri ve cihaz hizmetlerini etkileyebilir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
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
private fun AppRuleCard(app: AppEntry, rule: AppRule, selectionMode: Boolean, selected: Boolean, temporaryUntil: Long?, now: Long, onSelect: () -> Unit, onEdit: () -> Unit, onWifi: () -> Unit, onMobile: () -> Unit, onTemporary: () -> Unit) {
    Card(onClick = if (selectionMode) onSelect else onEdit, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onSelect() }) else AppIcon(app)
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (app.isSystem) "Sistem · ${app.packageName}" else app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!selectionMode) Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = rule.wifi.allowed, onClick = onWifi, label = { Text(if (rule.wifi.allowed) "Wi-Fi açık" else "Wi-Fi kapalı") }, leadingIcon = { Icon(Icons.Outlined.Wifi, null, Modifier.size(16.dp)) }, modifier = Modifier.weight(1f))
                FilterChip(selected = rule.mobile.allowed, onClick = onMobile, label = { Text(if (rule.mobile.allowed) "Mobil açık" else "Mobil kapalı") }, leadingIcon = { Icon(Icons.Outlined.SignalCellularAlt, null, Modifier.size(16.dp)) }, modifier = Modifier.weight(1f))
            }
            if (rule.wifi.downloadBps > 0 || rule.wifi.uploadBps > 0 || rule.wifi.quotaBytes > 0) Text("Wi-Fi: ${rule.wifi.shortLabel()}${if (rule.wifi.quotaBytes > 0) " · ${rule.wifi.period.label()} ${bytesLabel(rule.wifi.quotaBytes)}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (rule.mobile.downloadBps > 0 || rule.mobile.uploadBps > 0 || rule.mobile.quotaBytes > 0) Text("Mobil: ${rule.mobile.shortLabel()}${if (rule.mobile.quotaBytes > 0) " · ${rule.mobile.period.label()} ${bytesLabel(rule.mobile.quotaBytes)}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!selectionMode) {
                TextButton(onClick = onTemporary, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                    Icon(if (temporaryUntil == null) Icons.Outlined.Timer else Icons.Outlined.TimerOff, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (temporaryUntil == null) "15 dakika serbest bırak" else "Geçici izin: ${ceil((temporaryUntil - now) / 60_000.0).toInt()} dk · İptal et", style = MaterialTheme.typography.labelMedium)
                }
                if (temporaryUntil != null) Text("Geçici izin iki ağdaki uygulama sınırlarını kaldırır. Toplam kota geçerli kalır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
