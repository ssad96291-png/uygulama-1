package dev.akiskontrol.app.data

import dev.akiskontrol.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal data class StoredState(
    val rules: Map<Int, AppRule> = emptyMap(),
    val usage: List<UsageEntry> = emptyList(),
    val globalQuota: GlobalQuota = GlobalQuota(),
    val settings: AppSettings = AppSettings(),
    val identities: Map<Int, List<String>> = emptyMap(),
    val temporary: Map<Int, Long> = emptyMap(),
)

/** A bounded, strictly typed format. Numeric strings and fractional limits are rejected. */
internal object StateJson {
    const val MAX_JSON_CHARS = 32 * 1024 * 1024
    private const val MAX_APPS = 20_000
    private const val MAX_USAGE = 200_000
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*")

    fun encode(value: StoredState, backup: Boolean): String = JSONObject().apply {
        put("format", if (backup) "akis-backup" else "akis-state")
        put("version", 1)
        put("settings", JSONObject().apply {
            put("advanced", value.settings.advanced)
            put("darkTheme", value.settings.darkTheme)
            put("defaultWifiAllowed", value.settings.defaultWifiAllowed)
            put("defaultMobileAllowed", value.settings.defaultMobileAllowed)
            put("showSystemApps", value.settings.showSystemApps)
        })
        put("globalQuota", JSONObject().apply {
            put("wifiBytes", value.globalQuota.wifiBytes)
            put("mobileBytes", value.globalQuota.mobileBytes)
            put("period", value.globalQuota.period.name)
            put("renewalDay", value.globalQuota.renewalDay)
        })
        put("identities", JSONArray().apply {
            value.identities.toSortedMap().forEach { (uid, packages) ->
                put(JSONObject().put("uid", uid).put("packages", JSONArray(packages.sorted())))
            }
        })
        put("rules", JSONArray().apply {
            value.rules.toSortedMap().forEach { (uid, rule) ->
                put(JSONObject().put("uid", uid).put("wifi", encodeRule(rule.wifi)).put("mobile", encodeRule(rule.mobile)))
            }
        })
        put("usage", JSONArray().apply {
            value.usage.forEach { usage ->
                put(JSONObject().put("uid", usage.uid).put("network", usage.network.name)
                    .put("day", usage.day).put("downloadBytes", usage.downloadBytes).put("uploadBytes", usage.uploadBytes))
            }
        })
        // Temporary grants are local and transient, never reactivated by a portable backup.
        if (!backup) put("temporary", JSONArray().apply {
            value.temporary.toSortedMap().forEach { (uid, until) ->
                put(JSONObject().put("uid", uid).put("until", until))
            }
        })
    }.toString()

    fun decode(text: String, backup: Boolean): StoredState {
        require(text.length <= MAX_JSON_CHARS) { "Yedek dosyası çok büyük" }
        val root = JSONObject(text)
        require(root.string("format") == if (backup) "akis-backup" else "akis-state") { "Akış yedeği bekleniyor" }
        require(root.long("version") == 1L) { "Yedek sürümü desteklenmiyor" }
        val settings = root.obj("settings").let {
            AppSettings(it.bool("advanced"), it.bool("darkTheme"), it.bool("defaultWifiAllowed"),
                it.bool("defaultMobileAllowed"), it.bool("showSystemApps"))
        }
        val global = root.obj("globalQuota").let {
            GlobalQuota(
                wifiBytes = it.long("wifiBytes"), mobileBytes = it.long("mobileBytes"),
                period = enumValueOf<QuotaPeriod>(it.string("period")), renewalDay = it.renewalDay(),
            )
        }
        val identities = linkedMapOf<Int, List<String>>()
        val seenPackages = mutableSetOf<String>()
        root.array("identities", MAX_APPS).objects().forEach { item ->
            val uid = item.uid()
            require(uid !in identities) { "Yinelenen uygulama kimliği" }
            val packages = item.array("packages", MAX_APPS).let { array ->
                (0 until array.length()).map { i ->
                    val name = array.get(i)
                    require(name is String && name.length <= 255 && packagePattern.matches(name)) { "Paket adı geçersiz" }
                    require(seenPackages.add(name)) { "Yinelenen paket kimliği" }
                    name
                }
            }
            identities[uid] = packages
        }
        val rules = linkedMapOf<Int, AppRule>()
        root.array("rules", MAX_APPS).objects().forEach { item ->
            val uid = item.uid()
            require(uid !in rules) { "Yinelenen uygulama kuralı" }
            rules[uid] = AppRule(uid, decodeRule(item.obj("wifi")), decodeRule(item.obj("mobile")))
        }
        val seenUsage = mutableSetOf<Triple<Int, NetworkKind, String>>()
        val usage = root.array("usage", MAX_USAGE).objects().map { item ->
            val uid = item.uid()
            val day = item.string("day")
            require(day.length == 10 && LocalDate.parse(day).toString() == day) { "Kullanım tarihi geçersiz" }
            val network = enumValueOf<NetworkKind>(item.string("network"))
            require(seenUsage.add(Triple(uid, network, day))) { "Yinelenen kullanım kaydı" }
            UsageEntry(uid, network, day, item.long("downloadBytes"), item.long("uploadBytes"))
        }
        val temporary = linkedMapOf<Int, Long>()
        if (!backup) root.array("temporary", MAX_APPS).objects().forEach { item ->
            val uid = item.uid()
            val until = item.long("until")
            require(uid !in temporary && until >= 0) { "Geçici izin geçersiz" }
            temporary[uid] = until
        }
        // Every UID may be retained in local storage, but imports only use the package map.
        return StoredState(rules, usage, global, settings, identities, temporary)
    }

    private fun encodeRule(value: NetworkRule) = JSONObject().apply {
        put("allowed", value.allowed)
        put("downloadBps", value.downloadBps)
        put("uploadBps", value.uploadBps)
        put("quotaBytes", value.quotaBytes)
        put("period", value.period.name)
        put("quotaAction", value.quotaAction.name)
        put("afterQuotaBps", value.afterQuotaBps)
        put("renewalDay", value.renewalDay)
    }

    private fun decodeRule(value: JSONObject) = NetworkRule(
        allowed = value.bool("allowed"), downloadBps = value.long("downloadBps"),
        uploadBps = value.long("uploadBps"), quotaBytes = value.long("quotaBytes"),
        period = enumValueOf<QuotaPeriod>(value.string("period")),
        quotaAction = enumValueOf<QuotaAction>(value.string("quotaAction")),
        afterQuotaBps = value.long("afterQuotaBps"),
        renewalDay = value.renewalDay(),
    )

    private fun JSONObject.renewalDay(): Int {
        if (!has("renewalDay")) return 1 // Version 1 backups originally renewed on the first.
        return long("renewalDay").also {
            require(it in 1..31) { "Yenilenme günü 1–31 arasında olmalı" }
        }.toInt()
    }

    private fun JSONObject.uid(): Int = long("uid").also {
        require(it in 0..Int.MAX_VALUE.toLong()) { "Uygulama kimliği geçersiz" }
    }.toInt()
    private fun JSONObject.long(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long) { "$key tam sayı olmalı" }
        return (value as Number).toLong()
    }
    private fun JSONObject.string(key: String): String = get(key).let {
        require(it is String) { "$key metin olmalı" }; it
    }
    private fun JSONObject.bool(key: String): Boolean = get(key).let {
        require(it is Boolean) { "$key doğru/yanlış olmalı" }; it
    }
    private fun JSONObject.obj(key: String): JSONObject = get(key).let {
        require(it is JSONObject) { "$key nesne olmalı" }; it
    }
    private fun JSONObject.array(key: String, maximum: Int): JSONArray = get(key).let {
        require(it is JSONArray && it.length() <= maximum) { "$key listesi geçersiz veya çok büyük" }; it
    }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { index ->
        get(index).let { require(it is JSONObject) { "Yedek kaydı geçersiz" }; it }
    }
}
