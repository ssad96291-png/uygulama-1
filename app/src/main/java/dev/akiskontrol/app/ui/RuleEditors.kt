package dev.akiskontrol.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.akiskontrol.core.AppRule
import dev.akiskontrol.core.GlobalQuota
import dev.akiskontrol.core.MAX_QUOTA_BYTES
import dev.akiskontrol.core.MAX_RATE_BPS
import dev.akiskontrol.core.NetworkRule
import dev.akiskontrol.core.QuotaAction
import dev.akiskontrol.core.QuotaPeriod
import java.math.BigDecimal
import java.math.RoundingMode

internal data class InputUnit(val label: String, val multiplier: Long)
private val rateUnits = listOf(InputUnit("KB/s", 1_000), InputUnit("MB/s", 1_000_000), InputUnit("Mbps", 125_000))
private val quotaUnits = listOf(InputUnit("MB", 1_000_000), InputUnit("GB", 1_000_000_000))
private val decimalInput = Regex("-?(?:[0-9]+(?:[.,][0-9]*)?|[.,][0-9]+)")

@Stable
internal class AmountDraft(value: Long, val units: List<InputUnit>, val maximum: Long, val canBeUnlimited: Boolean = true) {
    var unlimited by mutableStateOf(value == 0L && canBeUnlimited)
    var unit by mutableStateOf(units.filter { value >= it.multiplier }.maxByOrNull { it.multiplier } ?: units.first())
    var text by mutableStateOf(if (value == 0L) "1" else exactNumber(value, unit.multiplier))
    var error by mutableStateOf<String?>(null)

    fun changeUnit(next: InputUnit) {
        val cleaned = text.trim()
        val amount = if (cleaned.length <= 32 && decimalInput.matches(cleaned)) cleaned.replace(',', '.').toBigDecimalOrNull() else null
        if (amount != null && amount >= BigDecimal.ZERO) {
            text = amount.multiply(BigDecimal.valueOf(unit.multiplier))
                .divide(BigDecimal.valueOf(next.multiplier), 12, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString()
        }
        unit = next
        error = null
    }

    fun value(title: String): Long? {
        error = null
        if (canBeUnlimited && unlimited) return 0L
        return try {
            require(text.length <= 32) { "Değer çok uzun." }
            val cleaned = text.trim()
            require(decimalInput.matches(cleaned)) { "Geçerli bir sayı yaz (ör. 1,5)." }
            val decimal = cleaned.replace(',', '.').toBigDecimalOrNull()
                ?: throw IllegalArgumentException("Geçerli bir sayı yaz.")
            require(decimal >= BigDecimal.ZERO) { "Değer negatif olamaz." }
            val amount = decimal.multiply(BigDecimal.valueOf(unit.multiplier))
                .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
            require(amount > 0L || canBeUnlimited) { "Hız sıfırdan büyük olmalı." }
            require(amount <= maximum) { "En fazla ${if (maximum == MAX_RATE_BPS) "1.000 MB/s" else "9.000.000 GB"} kullanabilirsin." }
            amount
        } catch (_: ArithmeticException) {
            error = "$title çok büyük veya 1 bayttan daha hassas."
            null
        } catch (e: IllegalArgumentException) {
            error = e.message ?: "Değeri kontrol et."
            null
        }
    }
}

private fun exactNumber(value: Long, multiplier: Long): String = BigDecimal.valueOf(value)
    .divide(BigDecimal.valueOf(multiplier), 12, RoundingMode.UNNECESSARY)
    .stripTrailingZeros().toPlainString()

@Stable
internal class RenewalDayDraft(private val initial: Int) {
    var text by mutableStateOf(initial.toString())
    var error by mutableStateOf<String?>(null)

    fun value(required: Boolean): Int? {
        error = null
        val day = text.trim().toIntOrNull()?.takeIf { it in 1..31 }
        if (day != null) return day
        if (!required) return initial
        error = "1 ile 31 arasında bir gün yaz."
        return null
    }
}

@Stable
internal class NetworkDraft(private val initial: NetworkRule) {
    var allowed by mutableStateOf(initial.allowed)
    val download = AmountDraft(initial.downloadBps, rateUnits, MAX_RATE_BPS)
    val upload = AmountDraft(initial.uploadBps, rateUnits, MAX_RATE_BPS)
    val quota = AmountDraft(initial.quotaBytes, quotaUnits, MAX_QUOTA_BYTES)
    var period by mutableStateOf(initial.period)
    val renewalDay = RenewalDayDraft(initial.renewalDay)
    var action by mutableStateOf(initial.quotaAction)
    val afterQuota = AmountDraft(initial.afterQuotaBps, rateUnits, MAX_RATE_BPS, canBeUnlimited = false)

    /** Save raw inputs, including unfinished/invalid edits, without validating or changing them. */
    fun saveState(): Map<String, Any?> = buildMap {
        put("initial.allowed", initial.allowed)
        put("initial.download", initial.downloadBps)
        put("initial.upload", initial.uploadBps)
        put("initial.quota", initial.quotaBytes)
        put("initial.period", initial.period.name)
        put("initial.action", initial.quotaAction.name)
        put("initial.afterQuota", initial.afterQuotaBps)
        put("initial.renewal", initial.renewalDay)
        put("allowed", allowed)
        put("period", period.name)
        put("action", action.name)
        put("renewal.text", renewalDay.text)
        put("renewal.error", renewalDay.error)
        listOf("download" to download, "upload" to upload, "quota" to quota, "afterQuota" to afterQuota).forEach { (name, draft) ->
            put("$name.text", draft.text)
            put("$name.unit", draft.units.indexOf(draft.unit))
            put("$name.unlimited", draft.unlimited)
            put("$name.error", draft.error)
        }
    }

    companion object {
        val Saver = mapSaver<NetworkDraft>(save = { it.saveState() }, restore = { restoreState(it) })

        fun restoreState(saved: Map<String, Any?>): NetworkDraft {
            val initial = NetworkRule(
                allowed = saved.getValue("initial.allowed") as Boolean,
                downloadBps = saved.getValue("initial.download") as Long,
                uploadBps = saved.getValue("initial.upload") as Long,
                quotaBytes = saved.getValue("initial.quota") as Long,
                period = QuotaPeriod.valueOf(saved.getValue("initial.period") as String),
                quotaAction = QuotaAction.valueOf(saved.getValue("initial.action") as String),
                afterQuotaBps = saved.getValue("initial.afterQuota") as Long,
                renewalDay = saved.getValue("initial.renewal") as Int,
            )
            return NetworkDraft(initial).apply {
                allowed = saved.getValue("allowed") as Boolean
                period = QuotaPeriod.valueOf(saved.getValue("period") as String)
                action = QuotaAction.valueOf(saved.getValue("action") as String)
                renewalDay.text = saved.getValue("renewal.text") as String
                renewalDay.error = saved["renewal.error"] as String?
                listOf("download" to download, "upload" to upload, "quota" to quota, "afterQuota" to afterQuota).forEach { (name, draft) ->
                    draft.text = saved.getValue("$name.text") as String
                    draft.unit = draft.units[saved.getValue("$name.unit") as Int]
                    draft.unlimited = saved.getValue("$name.unlimited") as Boolean
                    draft.error = saved["$name.error"] as String?
                }
            }
        }
    }

    fun rule(): NetworkRule? {
        val down = download.value("İndirme hızı")
        val up = upload.value("Yükleme hızı")
        val bytes = quota.value("Kota")
        val after = if (bytes != null && bytes > 0 && action == QuotaAction.SLOW) {
            afterQuota.value("Kota sonrası hız")
        } else initial.afterQuotaBps
        val day = renewalDay.value(required = !quota.unlimited && period == QuotaPeriod.MONTHLY)
        if (down == null || up == null || bytes == null || after == null || day == null) return null
        return NetworkRule(
            allowed = allowed,
            downloadBps = down,
            uploadBps = up,
            quotaBytes = bytes,
            period = period,
            quotaAction = action,
            afterQuotaBps = after,
            renewalDay = day,
        )
    }
}

@Composable
internal fun RuleEditorDialog(
    title: String,
    initial: AppRule,
    onDismiss: () -> Unit,
    onSave: (NetworkRule, NetworkRule) -> Unit,
) {
    val wifi = remember(initial) { NetworkDraft(initial.wifi) }
    val mobile = remember(initial) { NetworkDraft(initial.mobile) }
    var selectedNetwork by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog(
        title = title,
        subtitle = "Her ağ için ayrı izin, hız ve kota",
        onDismiss = onDismiss,
        onSave = {
            val wifiRule = wifi.rule()
            val mobileRule = mobile.rule()
            if (wifiRule == null || mobileRule == null) {
                selectedNetwork = if (wifiRule == null) 0 else 1
                error = "İşaretli alanları kontrol et."
            } else {
                runCatching { onSave(wifiRule, mobileRule) }.onFailure {
                    error = it.message ?: "Kurallar kaydedilemedi."
                }
            }
        },
        error = error,
    ) {
        TabRow(selectedTabIndex = selectedNetwork) {
            Tab(selected = selectedNetwork == 0, onClick = { selectedNetwork = 0 }, text = { Text("Wi-Fi") })
            Tab(selected = selectedNetwork == 1, onClick = { selectedNetwork = 1 }, text = { Text("Mobil veri") })
        }
        key(selectedNetwork) {
            NetworkEditor(if (selectedNetwork == 0) wifi else mobile)
        }
    }
}

@Composable
internal fun GlobalQuotaDialog(
    initial: GlobalQuota,
    onDismiss: () -> Unit,
    onSave: (GlobalQuota) -> Unit,
) {
    val wifi = remember(initial) { AmountDraft(initial.wifiBytes, quotaUnits, MAX_QUOTA_BYTES) }
    val mobile = remember(initial) { AmountDraft(initial.mobileBytes, quotaUnits, MAX_QUOTA_BYTES) }
    var period by remember(initial) { mutableStateOf(initial.period) }
    val renewalDay = remember(initial) { RenewalDayDraft(initial.renewalDay) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog(
        title = "Ortak veri kotası",
        subtitle = "Tüm uygulamaların toplam kullanımı",
        onDismiss = onDismiss,
        onSave = {
            val wifiValue = wifi.value("Wi-Fi kotası")
            val mobileValue = mobile.value("Mobil veri kotası")
            val day = renewalDay.value(required = period == QuotaPeriod.MONTHLY)
            if (wifiValue == null || mobileValue == null || day == null) {
                error = "İşaretli alanları kontrol et."
            } else {
                runCatching {
                    onSave(GlobalQuota(wifiBytes = wifiValue, mobileBytes = mobileValue, period = period, renewalDay = day))
                }.onFailure {
                    error = it.message ?: "Kota kaydedilemedi."
                }
            }
        },
        error = error,
    ) {
        Text(
            "Wi-Fi ve mobil veri ayrı hesaplanır. Bir ağın kotası dolduğunda o ağdaki tüm uygulamaların erişimi yeni döneme kadar durur. Geçici izin de bu kotaya dahildir.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RulePeriodPicker(period) { period = it }
        if (period == QuotaPeriod.MONTHLY) RenewalDayInput(renewalDay)
        AmountInput("Wi-Fi kotası", wifi, unlimitedLabel = "Kota yok")
        AmountInput("Mobil veri kotası", mobile, unlimitedLabel = "Kota yok")
        Text("Kota, indirme ve yüklemenin toplamıdır. Günler gece yarısı, haftalar pazartesi başlar; aylık kota seçtiğin günde yenilenir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NetworkEditor(draft: NetworkDraft) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("İnternet erişimi", style = MaterialTheme.typography.titleMedium)
                Text(if (draft.allowed) "Bu ağda bağlantıya izin ver" else "Bu ağda bağlantıyı engelle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = draft.allowed, onCheckedChange = { draft.allowed = it })
        }
        HorizontalDivider()
        Text("Hız sınırları", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        AmountInput("İndirme hızı", draft.download)
        AmountInput("Yükleme hızı", draft.upload)
        Text("0 = sınırsız. 1 MB/s = 8 Mbps. Hız sınırı aynı uygulamanın tüm bağlantılarına birlikte uygulanır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        AmountInput("Veri kotası", draft.quota, unlimitedLabel = "Kota yok")
        if (!draft.quota.unlimited) {
            RulePeriodPicker(draft.period) { draft.period = it }
            if (draft.period == QuotaPeriod.MONTHLY) RenewalDayInput(draft.renewalDay)
            Text("Kota dolunca", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = draft.action == QuotaAction.BLOCK, onClick = { draft.action = QuotaAction.BLOCK }, label = { Text("Engelle") })
                FilterChip(selected = draft.action == QuotaAction.SLOW, onClick = { draft.action = QuotaAction.SLOW }, label = { Text("Yavaşlat") })
            }
            if (draft.action == QuotaAction.SLOW) {
                AmountInput("Kota sonrası hız", draft.afterQuota)
                Text("İndirme ve yükleme için ayrı ayrı uygulanır. Daha düşük mevcut hız sınırları korunur.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Kota, indirme ve yüklemenin toplamıdır. Her dönem otomatik yenilenir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun RenewalDayInput(draft: RenewalDayDraft) {
    OutlinedTextField(
        value = draft.text,
        onValueChange = { draft.text = it; draft.error = null },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Aylık yenilenme günü (1–31)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = draft.error != null,
        supportingText = {
            Text(draft.error ?: "Seçtiğin gün ayda yoksa o ayın son günü kullanılır.")
        },
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
internal fun AmountInput(title: String, draft: AmountDraft, unlimitedLabel: String = "Sınırsız") {
    var unitMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            if (draft.canBeUnlimited) {
                FilterChip(
                    selected = draft.unlimited,
                    onClick = { draft.unlimited = !draft.unlimited; draft.error = null },
                    label = { Text(unlimitedLabel) },
                )
            }
        }
        if (!draft.unlimited) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = draft.text,
                    onValueChange = { draft.text = it; draft.error = null },
                    modifier = Modifier.weight(1f),
                    label = { Text("Değer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = draft.error != null,
                    supportingText = if (draft.error != null) ({ Text(draft.error.orEmpty()) }) else null,
                    shape = RoundedCornerShape(14.dp),
                )
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { unitMenu = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 56.dp)) {
                        Text("${draft.unit.label} ▾")
                    }
                    DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                        draft.units.forEach { unit ->
                            DropdownMenuItem(text = { Text(unit.label) }, onClick = { draft.changeUnit(unit); unitMenu = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RulePeriodPicker(period: QuotaPeriod, onChange: (QuotaPeriod) -> Unit) {
    Column {
        Text("Yenilenme dönemi", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuotaPeriod.entries.forEach { item ->
                FilterChip(selected = item == period, onClick = { onChange(item) }, label = { Text(item.label()) })
            }
        }
    }
}

@Composable
private fun EditorDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    error: String?,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp).imePadding().widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 720.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column {
                Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) { content() }
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (error != null) Text(error, modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("Vazgeç") }
                        Button(onClick = onSave, modifier = Modifier.padding(start = 8.dp)) { Text("Kaydet") }
                    }
                }
            }
        }
    }
}
