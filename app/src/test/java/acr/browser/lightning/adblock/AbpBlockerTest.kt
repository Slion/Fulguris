package acr.browser.lightning.adblock

import jp.hazuki.yuzubrowser.adblock.filter.abp.AbpFilterDecoder
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilterSet
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterReader
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*

class AbpBlockerTest {

    @Test
    fun readWriteDecode() {
        val ASSET_BASE_PATH = "../app/src/main/assets/"
        val easylist = File(ASSET_BASE_PATH + "easylist.txt")
        lateinit var set: UnifiedFilterSet
        easylist.inputStream().bufferedReader().use {
            val decoder = AbpFilterDecoder()
            set = decoder.decode(it, null)
        }

        val filterStore = ByteArrayOutputStream()
        filterStore.use {
            val writer = FilterWriter()
            writer.write(it, set.blackList)
        }

        val filters = mutableListOf<UnifiedFilter>()
        filterStore.toByteArray().inputStream().use { stream ->
            val reader = FilterReader(stream)
            if (reader.checkHeader())
                filters.addAll(reader.readAll().map {it.second})
        }

        assertEquals(set.blackList, filters)
    }
}
