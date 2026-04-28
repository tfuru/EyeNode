package com.example.eyenode

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.eyenode.ui.main.MainScreen
import com.example.eyenode.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  // 全画面で共有するリポジトリのインスタンス
  val dataRepository = androidx.compose.runtime.remember { com.example.eyenode.data.DefaultDataRepository() }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
              onItemClick = { navKey -> backStack.add(navKey) }, 
              viewModel = androidx.lifecycle.viewmodel.compose.viewModel { com.example.eyenode.ui.main.MainScreenViewModel(dataRepository) },
              modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<Settings> {
          SettingsScreen(
              onBack = { backStack.removeLastOrNull() },
              viewModel = androidx.lifecycle.viewmodel.compose.viewModel { com.example.eyenode.ui.settings.SettingsViewModel(dataRepository) }
          )
        }
      },
  )
}
