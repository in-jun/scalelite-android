package dev.injun.scalelite

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.injun.scalelite.ui.adddevice.AddDeviceRoute
import dev.injun.scalelite.ui.diagnostics.DiagnosticsRoute
import dev.injun.scalelite.ui.home.HomeRoute

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Home)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Home> {
                HomeRoute(
                    viewModel = hiltViewModel(),
                    onAddDevice = { backStack.add(AddDevice) },
                    onDiagnostics = { backStack.add(Diagnostics) },
                )
            }
            entry<AddDevice> {
                AddDeviceRoute(viewModel = hiltViewModel(), onDone = { backStack.removeLastOrNull() })
            }
            entry<Diagnostics> {
                DiagnosticsRoute(viewModel = hiltViewModel(), onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
