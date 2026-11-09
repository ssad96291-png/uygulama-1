package dev.akiskontrol.core

/**
 * Android 13+ gives an application's SDK sandbox a separate UID. AOSP Process defines
 * app IDs 20,000..29,999 for these sandboxes and maps them to 10,000..19,999 by
 * subtracting 10,000. Keep the 100,000-wide Android user prefix intact.
 *
 * Call this only on Android versions supporting SDK sandboxes. Negative/unknown owners,
 * ordinary applications, system services and unrelated isolated UIDs stay unchanged.
 * The Android public mapping API was added only in API 35, hence this pure helper.
 */
fun appUidForSdkSandbox(uid: Int): Int {
    if (uid < 0) return uid
    val appId = uid % 100_000
    return if (appId in 20_000..29_999) uid - 10_000 else uid
}
