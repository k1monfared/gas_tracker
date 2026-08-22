package com.k1.gastracker

import com.k1.gastracker.core.Refill
import com.k1.gastracker.data.RefillLoadResult
import com.k1.gastracker.data.RefillStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class RefillStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(): RefillStore = RefillStore(tmp.root)

    private fun refill() = Refill(
        date = LocalDate.parse("2026-01-01"),
        volume = 40.0,
        distance = 500.0,
        cost = 80.0,
        currency = "EUR",
    )

    @Test
    fun missingFileIsEmptyHistory() {
        val result = store().load()
        assertTrue(result is RefillLoadResult.Missing)
    }

    @Test
    fun roundTripPreservesRefills() {
        val store = store()
        store.load()
        val saved = listOf(refill())
        assertTrue(store.save(saved))
        val loaded = store.load() as RefillLoadResult.Loaded
        assertEquals(saved, loaded.refills)
        assertTrue(tmp.root.resolve("refills.json").readText().contains("\"version\":"))
    }

    @Test
    fun truncatedJsonDoesNotBecomeEmptyWritableHistory() {
        val store = store()
        store.load()
        store.save(listOf(refill()))
        tmp.root.resolve("refills.json").writeText("{ \"refills\": [")
        val result = store.load()
        assertTrue(result is RefillLoadResult.Failed)
        assertFalse(store.canSave())
        assertFalse(store.save(emptyList()))
        assertTrue(tmp.root.resolve("refills.json").readText().startsWith("{ \"refills\": ["))
        assertTrue(tmp.root.resolve("refills.json.corrupt").exists())
    }

    @Test
    fun invalidRecordFailsLoadWithoutOverwrite() {
        val store = store()
        tmp.root.resolve("refills.json").writeText(
            """{"version":1,"refills":[{"date":"2026-01-01","volume":0.0}]}"""
        )
        val result = store.load()
        assertTrue(result is RefillLoadResult.Failed)
        assertFalse(store.save(emptyList()))
        assertTrue(tmp.root.resolve("refills.json").readText().contains("\"volume\":0.0"))
    }

    @Test
    fun interruptedTempWriteKeepsPrimary() {
        val store = store()
        store.load()
        store.save(listOf(refill()))
        tmp.root.resolve("refills.json.tmp").writeText("partial")
        val loaded = store.load() as RefillLoadResult.Loaded
        assertEquals(1, loaded.refills.size)
    }

    @Test
    fun recoversFromBackup() {
        val store = store()
        store.load()
        store.save(listOf(refill()))
        store.save(
            listOf(refill(), refill().copy(date = LocalDate.parse("2026-02-01"), volume = 30.0)),
        )
        tmp.root.resolve("refills.json").writeText("not-json")
        val loaded = store.load() as RefillLoadResult.Loaded
        assertTrue(loaded.recoveredFromBackup)
        assertEquals(1, loaded.refills.size)
    }

    @Test
    fun settingsStyleSaveAfterFailedLoadDoesNotWipeFile() {
        tmp.root.resolve("refills.json").writeText("{not json")
        val store = store()
        assertTrue(store.load() is RefillLoadResult.Failed)
        assertFalse(store.save(emptyList()))
        assertEquals("{not json", tmp.root.resolve("refills.json").readText())
    }

    @Test
    fun importRestoresAfterFailedLoad() {
        tmp.root.resolve("refills.json").writeText("{not json")
        val store = store()
        store.load()
        val exported = store.exportJson(listOf(refill()))
        val restored = store.restoreFromImport(exported) as RefillLoadResult.Loaded
        assertEquals(1, restored.refills.size)
        assertTrue(store.canSave())
        assertEquals(1, (store.load() as RefillLoadResult.Loaded).refills.size)
    }

    @Test
    fun oldFormatWithoutVersionStillLoads() {
        tmp.root.resolve("refills.json").writeText(
            """{"refills":[{"date":"2026-01-01","volume":40.0,"distance":500.0,"cost":80.0,"currency":"EUR"}]}"""
        )
        val loaded = store().load() as RefillLoadResult.Loaded
        assertEquals(1, loaded.refills.size)
        assertEquals(40.0, loaded.refills[0].volume, 1e-9)
    }
}
