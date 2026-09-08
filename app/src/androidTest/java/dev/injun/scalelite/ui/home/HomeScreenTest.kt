package dev.injun.scalelite.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.injun.scalelite.core.protocol.Dialect
import dev.injun.scalelite.data.db.DeviceEntity
import dev.injun.scalelite.data.db.MeasurementEntity
import dev.injun.scalelite.data.health.HealthConnectStatus
import dev.injun.scalelite.theme.ScaleLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val device = DeviceEntity("F8:F2:F0:00:00:01", "T6_AF502", Dialect.CHIPSEA_AC20, 0L)

    @Test
    fun emptyStateExplainsHowToStart() {
        composeTestRule.setContent { ScaleLiteTheme { HomeScreen(state = HomeUiState(healthConnectGranted = true)) } }

        composeTestRule.onNodeWithText("No weigh-in yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("No scale yet. Add one, then step on it whenever you want a reading.").assertIsDisplayed()
    }

    @Test
    fun backgroundSwitchIsDisabledWithoutAScale() {
        composeTestRule.setContent { ScaleLiteTheme { HomeScreen(state = HomeUiState(healthConnectGranted = true)) } }

        composeTestRule.onNodeWithText("Record in the background").assertIsDisplayed()
        composeTestRule.onNode(androidx.compose.ui.test.isToggleable()).assertIsNotEnabled()
    }

    @Test
    fun measureButtonReportsTheDevice() {
        var measured: DeviceEntity? = null
        composeTestRule.setContent {
            ScaleLiteTheme {
                HomeScreen(
                    state = HomeUiState(
                        devices = listOf(device),
                        latest = MeasurementEntity(1, device.address, 0L, 69_100, "", "hc-1"),
                        healthConnectGranted = true,
                    ),
                    onMeasureNow = { measured = it },
                )
            }
        }

        composeTestRule.onNodeWithText("69.10 kg").assertIsDisplayed()
        composeTestRule.onNodeWithText("Measure").performClick()
        assertEquals(device, measured)
    }

    @Test
    fun missingHealthConnectPermissionShowsABanner() {
        composeTestRule.setContent {
            ScaleLiteTheme { HomeScreen(state = HomeUiState(healthConnect = HealthConnectStatus.AVAILABLE, healthConnectGranted = false)) }
        }

        composeTestRule.onNodeWithText("Allow ScaleLite to write weight to Health Connect.").assertIsDisplayed()
    }
}

@androidx.compose.runtime.Composable
private fun HomeScreen(state: HomeUiState, onMeasureNow: (DeviceEntity) -> Unit = {}) = HomeScreen(
    state = state,
    bluetoothGranted = true,
    notificationsEnabled = true,
    onRequestBluetooth = {},
    onRequestNotifications = {},
    onRequestHealthConnect = {},
    onMeasureNow = onMeasureNow,
    onBackgroundToggle = {},
    onRemoveDevice = {},
    onDeleteMeasurement = {},
    onAddDevice = {},
    onDiagnostics = {},
)
