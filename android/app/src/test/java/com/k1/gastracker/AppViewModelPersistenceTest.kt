package com.k1.gastracker

import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.data.FxRepository
import com.k1.gastracker.data.PhotoCache
import com.k1.gastracker.data.RateFetcher
import com.k1.gastracker.data.RefillStore
import com.k1.gastracker.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppViewModelPersistenceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun settingsChangeDoesNotOverwriteFailedHistory() {
        val dir = tmp.root
        File(dir, "refills.json").writeText("{not json")
        val app = RuntimeEnvironment.getApplication()
        val store = RefillStore(dir)
        val fx = FxRepository(File(dir, "fx.json"), RateFetcher { _, _, _ -> Result.success(1.0) })
        val vm = AppViewModel(app, store, fx, PhotoCache(app))
        assertFalse(vm.state.value.historyWritable)
        vm.setHomeCurrency("USD")
        vm.setDistanceUnit(DistanceUnit.MILE)
        assertEquals("{not json", File(dir, "refills.json").readText())
        assertEquals("USD", vm.state.value.homeCurrency)
    }
}
