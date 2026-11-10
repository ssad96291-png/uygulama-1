package dev.akiskontrol.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.akiskontrol.core.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Per-app editing keeps both network drafts until the explicit save action. */
@Composable
internal fun AppRuleEditorScreen(
    app: AppEntry,
    rule: AppRule,
    state: AppState,
    onDismiss: () -> Unit,
    onSave: (NetworkRule, NetworkRule) -> Unit,
) {
    val wifi = rememberSaveable(app.uid, saver = NetworkDraft.Saver) { NetworkDraft(rule.wifi) }
    val mobile = rememberSaveable(app.uid, saver = NetworkDraft.Saver) { NetworkDraft(rule.mobile) }
    var selectedNetwork by rememberSaveable(app.uid) { mutableStateOf(0) }
    var advanced by rememberSaveable(app.uid) { mutableStateOf(false) }
    var error by rememberSaveable(app.uid) { mutableStateOf<String?>(null) }
    val draft = if (selectedNetwork == 0) wifi else mobile
    val network = if (selectedNetwork == 0) NetworkKind.WIFI else NetworkKind.MOBILE
    BackHandler(onBack = onDismiss)

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kaydetmeden geri dön") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Let the identity header scroll away when the keyboard or large text
            // leaves little height for the form; Back and Save stay reachable.
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppIcon(app, Modifier.size(52.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(app.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Bağlantı kuralları", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            EditorSegments(
                labels = listOf("Wi-Fi", "Mobil veri"),
                selected = selectedNetwork,
                onSelect = { selectedNetwork = it; error = null },
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("İnternet erişimi", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = draft.allowed,
                        onCheckedChange = { draft.allowed = it },
                        modifier = Modifier.semantics { contentDescription = "${network.label()} internet erişimi" },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EditorSectionHeading(Icons.Outlined.Speed, "Hız sınırları")
                CompactAmountInput("İndirme", draft.download, rate = true)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                CompactAmountInput("Yükleme", draft.upload, rate = true)
                Text("0 = sınırsız · 1 MB/sn = 8 Mbps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditorSectionHeading(Icons.Outlined.Storage, "Veri kotası")
                val selectedPeriod = if (draft.quota.unlimited) 0 else QuotaPeriod.entries.indexOf(draft.period) + 1
                EditorSegments(
                    labels = listOf("Yok") + QuotaPeriod.entries.map { it.label() },
                    selected = selectedPeriod,
                    onSelect = { index ->
                        draft.quota.unlimited = index == 0
                        draft.quota.error = null
                        if (index > 0) {
                            draft.period = QuotaPeriod.entries[index - 1]
                            if (draft.quota.text.toBigDecimalOrNull()?.signum() == 0) draft.quota.text = "1"
                        }
                    },
                )
                if (!draft.quota.unlimited) {
                    CompactAmountInput("Kota", draft.quota, rate = false)
                    QuotaUsage(draft, app.uid, network, state)
                } else {
                    Text("Bu ağda veri kotası yok.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(horizontal = 14.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Gelişmiş ayarlar", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Icon(if (advanced) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.ChevronRight, if (advanced) "Gelişmiş ayarları daralt" else "Gelişmiş ayarları genişlet")
                    }
                    if (advanced) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!draft.quota.unlimited) {
                                if (draft.period == QuotaPeriod.MONTHLY) RenewalDayInput(draft.renewalDay)
                                Text("Kota dolunca", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                EditorSegments(
                                    labels = listOf("Engelle", "Yavaşlat"),
                                    selected = if (draft.action == QuotaAction.BLOCK) 0 else 1,
                                    onSelect = { draft.action = if (it == 0) QuotaAction.BLOCK else QuotaAction.SLOW },
                                )
                                if (draft.action == QuotaAction.SLOW) {
                                    AmountInput("Kota sonrası hız", draft.afterQuota)
                                    Text("İndirme ve yükleme için ayrı ayrı uygulanır; daha düşük hız sınırları korunur.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("Hız sınırları uygulamanın tüm bağlantıları için ortaktır. Veri kotası indirme ve yüklemenin toplamıdır; günlük kota gece yarısı, haftalık kota pazartesi yenilenir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (draft.quota.unlimited) Text("Kota sonrası davranışı düzenlemek için önce bir veri kotası seç.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!draft.quota.unlimited && draft.period == QuotaPeriod.MONTHLY && !advanced) {
                Text("Aylık yenilenme: ayın ${draft.renewalDay.text}. günü. Gelişmiş ayarlardan değiştirebilirsin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                error?.let { Text(it, Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        val wifiRule = wifi.rule()
                        val mobileRule = mobile.rule()
                        if (wifiRule == null || mobileRule == null) {
                            selectedNetwork = if (wifiRule == null) 0 else 1
                            val invalid = if (wifiRule == null) wifi else mobile
                            advanced = invalid.renewalDay.error != null || invalid.afterQuota.error != null
                            error = "İşaretli alanları kontrol et."
                        } else {
                            runCatching { onSave(wifiRule, mobileRule) }.onFailure { error = it.message ?: "Kurallar kaydedilemedi." }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Kaydet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun EditorSectionHeading(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EditorSegments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
        Row(Modifier.fillMaxWidth().padding(3.dp).selectableGroup()) {
            labels.forEachIndexed { index, label ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                    color = if (index == selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                ) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = index == selected, role = Role.Tab, onClick = { onSelect(index) }).padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal, color = if (index == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactAmountInput(title: String, draft: AmountDraft, rate: Boolean) {
    var menu by remember(draft) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(.9f), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = if (rate && draft.unlimited) "0" else draft.text,
                onValueChange = {
                    draft.text = it
                    draft.error = null
                    if (rate) draft.unlimited = it.trim() == "0"
                },
                modifier = Modifier.weight(1f).semantics { contentDescription = "$title değeri" },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = draft.error != null,
                shape = RoundedCornerShape(12.dp),
            )
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { menu = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = "$title birimi" },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(draft.unit.label.replace("/s", "/sn"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    draft.units.forEach { unit ->
                        DropdownMenuItem(text = { Text(unit.label.replace("/s", "/sn")) }, onClick = { draft.changeUnit(unit); menu = false })
                    }
                }
            }
        }
        draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun QuotaUsage(draft: NetworkDraft, uid: Int, network: NetworkKind, state: AppState) {
    val today = LocalDate.now()
    val renewal = draft.renewalDay.text.trim().toIntOrNull()?.takeIf { it in 1..31 } ?: 1
    val range = quotaDateRange(draft.period, renewal, today)
    val used = state.usage.filter { it.uid == uid && it.network == network && range.contains(it.day) }
        .sumBytes { addBytes(it.downloadBytes, it.uploadBytes) }
    // Preview parsing is read-only: typing must not mutate validation errors.
    val limit = runCatching {
        draft.quota.text.trim().replace(',', '.').toBigDecimal()
            .multiply(BigDecimal.valueOf(draft.quota.unit.multiplier))
            .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }.getOrDefault(0L)
    val periodLabel = when (draft.period) { QuotaPeriod.DAILY -> "Bugün"; QuotaPeriod.WEEKLY -> "Bu hafta"; QuotaPeriod.MONTHLY -> "Bu kota döneminde" }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("$periodLabel ${bytesLabel(used)} kullanıldı", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (limit in 1..MAX_QUOTA_BYTES) {
            LinearProgressIndicator(
                progress = { (used.toDouble() / limit).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (used >= limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
