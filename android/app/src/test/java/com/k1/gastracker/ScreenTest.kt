package com.k1.gastracker

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.k1.gastracker.ui.DashboardScreen
import com.k1.gastracker.ui.GasTrackerAppContent
import com.k1.gastracker.ui.HistoryScreen
import com.k1.gastracker.ui.LogScreen
import com.k1.gastracker.ui.NoOpGasTrackerActions
import com.k1.gastracker.ui.SettingsScreen
import com.k1.gastracker.ui.UiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = NoOpGasTrackerActions()

    @Test
    fun historyShowsEmptyState() {
        rule.setContent { MaterialTheme { HistoryScreen(UiState(), actions) } }
        rule.onNodeWithText("No refills logged yet.").assertIsDisplayed()
    }

    @Test
    fun historyShowsRecoveryCopyWhenLoadFailed() {
        rule.setContent {
            MaterialTheme {
                HistoryScreen(UiState(storageError = "corrupt", historyWritable = false), actions)
            }
        }
        rule.onNodeWithText("Refill history is unavailable until storage is repaired or a backup is imported.")
            .assertIsDisplayed()
    }

    @Test
    fun dashboardShowsCurrencyAndHistoryWarning() {
        rule.setContent { MaterialTheme { DashboardScreen(UiState(), actions) } }
        rule.onNodeWithText("Chart currency").assertIsDisplayed()
        rule.onNodeWithText("Spend in view").assertIsDisplayed()
    }

    @Test
    fun settingsExposesBackupAndRestore() {
        rule.setContent { MaterialTheme { SettingsScreen(UiState(), actions) } }
        rule.onNodeWithText("Backup and restore").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Export history").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Import history").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun logSaveButtonIsShown() {
        rule.setContent { MaterialTheme { LogScreen(UiState(), actions) } }
        rule.onNodeWithText("Fuel filled").assertIsDisplayed()
        rule.onNodeWithText("Save refill").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun recoveryBannerIsShownForStorageFailure() {
        rule.setContent {
            MaterialTheme {
                GasTrackerAppContent(
                    state = UiState(storageError = "corrupt", historyWritable = false),
                    actions = actions,
                )
            }
        }
        rule.onNodeWithText("Could not read refill history").assertIsDisplayed()
        rule.onNodeWithText("Retry").assertIsDisplayed()
        rule.onNodeWithText("Import backup").assertIsDisplayed()
    }
}
