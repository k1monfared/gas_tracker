package com.k1.gastracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k1.gastracker.R

@Composable
fun GasTrackerApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GasTrackerAppContent(state = state, actions = viewModel)
}

@Composable
fun GasTrackerAppContent(state: UiState, actions: GasTrackerActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_log),
        stringResource(R.string.tab_dashboard),
        stringResource(R.string.tab_history),
        stringResource(R.string.tab_settings),
    )

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) actions.importHistory(uri)
    }

    LaunchedEffect(state.editingRefill) {
        if (state.editingRefill != null) tab = 0
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            if (state.storageError != null) {
                StorageBanner(
                    title = stringResource(R.string.storage_error_title),
                    body = stringResource(R.string.storage_error_body),
                    error = true,
                    onRetry = actions::retryLoad,
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
            } else if (state.recoveredFromBackup) {
                StorageBanner(
                    title = stringResource(R.string.storage_recovered_backup),
                    body = null,
                    error = false,
                    onDismiss = actions::dismissStorageBanner,
                )
            }
            state.importMessage?.let { message ->
                StorageBanner(
                    title = message,
                    body = null,
                    error = !state.historyWritable,
                    onDismiss = actions::dismissStorageBanner,
                )
            }
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(label, fontSize = 13.sp) },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
            when (tab) {
                0 -> LogScreen(state, actions)
                1 -> DashboardScreen(state, actions)
                2 -> HistoryScreen(state, actions)
                else -> SettingsScreen(state, actions)
            }
        }
    }
}

@Composable
private fun StorageBanner(
    title: String,
    body: String?,
    error: Boolean,
    onRetry: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
            )
            body?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onRetry?.let {
                    Button(onClick = it) { Text(stringResource(R.string.retry)) }
                }
                onImport?.let {
                    OutlinedButton(onClick = it) { Text(stringResource(R.string.import_backup)) }
                }
                onDismiss?.let {
                    OutlinedButton(onClick = it) { Text(stringResource(R.string.dismiss)) }
                }
            }
        }
    }
}
