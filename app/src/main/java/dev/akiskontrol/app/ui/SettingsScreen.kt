package dev.akiskontrol.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.akiskontrol.app.data.AppRepository
import dev.akiskontrol.app.BuildConfig
import dev.akiskontrol.core.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun SettingsScreen(state: AppState, repository: AppRepository, onExportBackup: () -> Unit, onImportBackup: () -> Unit, onOpenVpnSettings: () -> Unit, feedback: (String) -> Unit) {
    var versionTaps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0) }
    var showAdminLogin by remember { mutableStateOf(false) }
    var adminUnlocked by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("Ayarlar", "Akış'ı kendine göre düzenle.") }
        item {
            Panel {
                SectionTitle("Görünüm ve kontrol")
                SettingToggle("Koyu tema", "Daha düşük parlaklık, daha rahat görünüm.", state.settings.darkTheme) { repository.saveSettings(state.settings.copy(darkTheme = it)) }
                HorizontalDivider()
                SettingToggle("Gelişmiş görünüm", "Sistem uygulamalarını ve ek filtreyi göster.", state.settings.advanced) { repository.saveSettings(state.settings.copy(advanced = it)) }
            }
        }
        item {
            Panel {
                SectionTitle("Varsayılan erişim", "Özel kuralı olmayan tüm uygulamalara uygulanır.")
                SettingToggle("Wi-Fi erişimine izin ver", "Varsayılan Wi-Fi bağlantısı.", state.settings.defaultWifiAllowed) { repository.saveSettings(state.settings.copy(defaultWifiAllowed = it)) }
                HorizontalDivider()
                SettingToggle("Mobil veriye izin ver", "Varsayılan mobil bağlantı.", state.settings.defaultMobileAllowed) { repository.saveSettings(state.settings.copy(defaultMobileAllowed = it)) }
                Text("Değişiklik, özel kuralı olmayan mevcut ve yeni uygulamalar için hemen geçerlidir. Özel kuralları Uygulamalar ekranından düzenleyebilirsin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Panel {
                SectionTitle("Yedekleme", "Kurallarını ve tercihlerini bir dosyada sakla.")
                OutlinedButton(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Yedeği dışa aktar") }
                OutlinedButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Yedekten geri yükle") }
                Text("İçe aktarma mevcut kuralları ve tercihleri değiştirir. Başka cihazda uygulama kimlikleri farklı olabileceği için kuralları kontrol et. Yönetici parolası yedeğe eklenmez.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Panel {
                SectionTitle("VPN ve süreklilik")
                Text("Android aynı anda tek VPN çalıştırır. Başka bir VPN açıldığında Akış'ın koruması durabilir. Kesintide bağlantının kapanmasını istiyorsan Android'de her zaman açık VPN ve VPN olmadan bağlantıları engelle seçeneklerini etkinleştir.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onOpenVpnSettings, modifier = Modifier.align(Alignment.End)) { Text("Android VPN ayarlarını aç"); Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp)) }
            }
        }
        if (adminUnlocked) item {
            DiagnosticsPanel(state, onRefresh = { repository.refreshApps(); feedback("Uygulama keşfi yeniden başlatıldı") }, onLock = { adminUnlocked = false })
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Waves, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Akış", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Bağlantının kontrolü sende.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Kurallar ve kullanım kayıtları cihazında tutulur. Hız ve kota denetimi koruma açıkken çalışır. Uygulamayı ilk kez kullanırken küçük bir indirmeyle ayarlarını kontrol et.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Sürüm ${BuildConfig.VERSION_NAME} · Alfa", modifier = Modifier.fillMaxWidth().clickable {
                    val now = System.currentTimeMillis()
                    versionTaps = if (now - lastTap > 3000) 1 else versionTaps + 1
                    lastTap = now
                    if (versionTaps >= 7) { versionTaps = 0; if (!adminUnlocked) showAdminLogin = true else feedback("Yerel bakım paneli zaten açık") }
                }.padding(vertical = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showAdminLogin) AdminLoginDialog(repository, onDismiss = { showAdminLogin = false }) {
        showAdminLogin = false
        adminUnlocked = true
        feedback("Yerel bakım paneli açıldı")
    }
}

@Composable
private fun DiagnosticsPanel(state: AppState, onRefresh: () -> Unit, onLock: () -> Unit) {
    val format = remember { DateTimeFormatter.ofPattern("dd.MM HH:mm:ss", Turkish).withZone(ZoneId.systemDefault()) }
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Yerel bakım", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onLock) { Icon(Icons.Outlined.Lock, "Bakım panelini kilitle") }
        }
        Text("Bu panel yalnızca bu cihazdaki tanılama içindir. Uzak yönetim veya mağaza yetkisi sağlamaz.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Durum: ${state.statusMessage}\nAğ: ${state.network.label()}\nUygulama: ${state.apps.size} · Kural: ${state.rules.size}\nKullanım kaydı: ${state.usage.size}", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Uygulamaları yeniden tara") }
        SectionTitle("Son olaylar")
        if (state.logs.isEmpty()) Text("Henüz tanılama kaydı yok.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.logs.takeLast(50).asReversed().forEach { log ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(format.format(Instant.ofEpochMilli(log.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(log.message, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }
    }
}

@Composable
private fun AdminLoginDialog(repository: AppRepository, onDismiss: () -> Unit, onAuthenticated: () -> Unit) {
    var exists by remember { mutableStateOf<Boolean?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository) {
        runCatching { withContext(Dispatchers.IO) { repository.hasAdmin() } }.onSuccess { exists = it }.onFailure { error = "Yönetici bilgisi okunamadı." }
    }
    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.AdminPanelSettings, null) },
        title = { Text(if (exists == false) "Yerel yönetici oluştur" else "Yerel yönetici girişi") },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bu cihazdaki bakım panelini açar. Uzak yönetim veya mağaza hesabı değildir.", style = MaterialTheme.typography.bodySmall)
                if (exists == null) CircularProgressIndicator(Modifier.size(24.dp))
                else {
                    if (exists == false) Text("İlk kullanımda kendi kullanıcı adını ve parolanı belirle. Parola en az 8 karakter olmalı.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = username, onValueChange = { if (it.length <= 64) username = it }, label = { Text("Kullanıcı adı") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { if (it.length <= 1024) password = it }, label = { Text("Parola") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Parolayı gizle" else "Parolayı göster") } })
                    if (exists == false) OutlinedTextField(value = confirmation, onValueChange = { if (it.length <= 1024) confirmation = it }, label = { Text("Parolayı tekrar yaz") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(enabled = exists != null && !busy, onClick = {
                error = when {
                    username.trim().length !in 3..64 || username.any { it.isISOControl() } -> "Kullanıcı adı 3–64 karakter olmalı."
                    password.length < 8 -> "Parola en az 8 karakter olmalı."
                    exists == false && password != confirmation -> "Parolalar eşleşmiyor."
                    else -> null
                }
                if (error == null) {
                    busy = true
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                if (exists == false) { repository.createAdmin(username.trim(), password); true }
                                else repository.verifyAdmin(username.trim(), password)
                            }
                        }
                        busy = false
                        result.onSuccess { ok ->
                            if (ok) { password = ""; confirmation = ""; onAuthenticated() }
                            else error = "Bilgiler doğrulanamadı. Tekrarlanan denemelerde bir süre bekle."
                        }.onFailure { error = it.message ?: "İşlem tamamlanamadı." }
                    }
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (exists == false) "Oluştur ve aç" else "Giriş yap")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Vazgeç") } },
    )
}
