package app.fittracker.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseTest {
    private fun release(tag: String = "v0.3.0", assets: String = APK, extra: String = "") =
        """{"tag_name":"$tag","draft":false,"prerelease":false,$extra"assets":[$assets]}"""

    @Test
    fun versionCodeMatchesTheGradleFormula() {
        assertEquals(200, versionCodeOf("0.2.0"))
        assertEquals(10_203, versionCodeOf("v1.2.3"))
        // The reason for codes: as strings, 0.10.0 sorts before 0.9.0.
        assert(versionCodeOf("0.10.0")!! > versionCodeOf("0.9.0")!!)
    }

    @Test
    fun oddVersionsAreRejected() {
        assertNull(versionCodeOf("0.2"))
        assertNull(versionCodeOf("0.100.0"))
        assertNull(versionCodeOf("v0.2.0-beta"))
        assertNull(versionCodeOf("latest"))
    }

    @Test
    fun readsTheApkAndItsChecksum() {
        val r = parseRelease(release())!!
        assertEquals("0.3.0", r.versionName)
        assertEquals(300, r.versionCode)
        assertEquals("https://github.com/nikolauska/valitreeni/releases/download/v0.3.0/valitreeni-0.3.0.apk", r.apkUrl)
        assertEquals(3_234_069L, r.size)
        assertEquals("ab12", r.sha256)
    }

    @Test
    fun picksTheApkAmongOtherFiles() {
        val r = parseRelease(release(assets = """{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},$APK"""))!!
        assert(r.apkUrl.endsWith(".apk"))
    }

    @Test
    fun releaseWithoutApkOrWithPlainHttpIsIgnored() {
        assertNull(parseRelease(release(assets = "")))
        assertNull(parseRelease(release(assets = APK.replace("https://", "http://"))))
    }

    @Test
    fun prereleaseIsIgnored() {
        assertNull(parseRelease("""{"tag_name":"v0.3.0","prerelease":true,"assets":[$APK]}"""))
    }

    @Test
    fun missingDigestStillGivesARelease() {
        assertNull(parseRelease(release(assets = APK.replace(""","digest":"sha256:AB12"""", "")))!!.sha256)
    }

    private companion object {
        const val APK = """{"name":"valitreeni-0.3.0.apk","size":3234069,"digest":"sha256:AB12",""" +
            """"browser_download_url":"https://github.com/nikolauska/valitreeni/releases/download/v0.3.0/valitreeni-0.3.0.apk"}"""
    }
}
