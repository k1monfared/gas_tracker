package com.k1.gastracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GasTrackerApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Log", "Dashboard", "History", "Settings")

    LaunchedEffect(state.editingRefill) {
        if (state.editingRefill != null) tab = 0
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(label, fontSize = 13.sp) },
                    )
                }
            }
            when (tab) {
                0 -> LogScreen(state, viewModel)
                1 -> DashboardScreen(state)
                2 -> HistoryScreen(state, viewModel)
                else -> SettingsScreen(state, viewModel)
            }
        }
    }
}
