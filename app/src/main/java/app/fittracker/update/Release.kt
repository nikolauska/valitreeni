package app.fittracker.update

import org.json.JSONObject

/** A published version, as read from the GitHub "latest release" answer. */
data class Release(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val size: Long,
    /** Lowercase hex SHA-256 that GitHub computed on upload, or null for files uploaded before GitHub kept digests. */
    val sha256: String?,
)

/**
 * Must match the formula in app/build.gradle.kts: the release tag is the only version
 * information GitHub gives us, and comparing codes avoids string-comparing "0.10.0" with "0.9.0".
 */
fun versionCodeOf(version: String): Int? {
    val parts = version.removePrefix("v").split(".").map { it.toIntOrNull() ?: return null }
    // Each part gets two digits in the code, so 0.100.0 would collide with 1.0.0.
    if (parts.size != 3 || parts.any { it !in 0..99 }) return null
    return parts[0] * 10_000 + parts[1] * 100 + parts[2]
}

/**
 * Returns null when the release is unusable (no APK, odd tag), so a bad upload never offers itself as an update.
 * [allowHttp] is only for debug builds talking to a local test server.
 */
fun parseRelease(json: String, allowHttp: Boolean = false): Release? {
    val o = JSONObject(json)
    if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
    val tag = o.optString("tag_name")
    val code = versionCodeOf(tag) ?: return null
    val assets = o.optJSONArray("assets") ?: return null
    val apk = (0 until assets.length()).map(assets::getJSONObject).firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
    val url = apk.optString("browser_download_url")
    // Plain http could be swapped in transit; Android's signature check would still refuse it, but there's no reason to try.
    if (!url.startsWith("https://") && !(allowHttp && url.startsWith("http://"))) return null
    val digest = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
    return Release(tag.removePrefix("v"), code, url, apk.optLong("size"), digest)
}
