package com.k1.gastracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.k1.gastracker.core.Dataset
import com.k1.gastracker.core.PeriodKind
import com.k1.gastracker.core.buildDataset
import com.k1.gastracker.core.efficiencySeries
import com.k1.gastracker.core.flowValue
import com.k1.gastracker.core.periodSeries
import com.k1.gastracker.core.recentWindow
import com.k1.gastracker.core.windowRatios
import com.k1.gastracker.core.yearlyView
import com.k1.gastracker.ui.LineChart
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

private val PERIOD_LABELS = listOf("Daily", "Weekly", "Monthly", "Yearly")
private val PERIOD_DAYS = listOf(1.0, 7.0, 28.0, 365.0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(state: UiState) {
    val today = LocalDate.now()
    val convertedRefills = remember(state.refills, state.homeCurrency, state.convertedCosts) {
        state.refills.map { refill ->
            val converted = state.convertedCosts[refill]
            if (refill.currency == state.homeCurrency || converted == null) {
                refill.copy(currency = state.homeCurrency)
            } else {
                refill.copy(cost = converted, currency = state.homeCurrency)
            }
        }
    }
    val dataset = remember(convertedRefills) {
        runCatching { buildDataset(convertedRefills) }.getOrDefault(Dataset(emptyList()))
    }

    var periodIndex by rememberSaveable { mutableIntStateOf(2) }
    val periodLabel = PERIOD_LABELS[periodIndex]
    val periodDays = PERIOD_DAYS[periodIndex]

    val window = remember(dataset, today) { recentWindow(dataset.samples, today) }
    val ratioSamples = remember(dataset, today, periodIndex) {
        if (periodIndex == 3) {
            val cutoff = today.minusDays(364)
            dataset.samples.filter { it.date >= cutoff }
        } else {
            dataset.samples.filter { it.date >= (window.start ?: today) }
        }
    }
    val ratios = remember(ratioSamples) { windowRatios(ratioSamples) }
    val yearly = remember(dataset, today) { yearlyView(dataset.samples, today) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.fxError != null) {
            Text(
                "FX: ${state.fxError}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PERIOD_LABELS.forEachIndexed { i, label ->
                FilterChip(
                    selected = periodIndex == i,
                    onClick = { periodIndex = i },
                    label = { Text(label) },
                )
            }
        }

        Text(
            "Based on ${windowCaption(window, today)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (periodIndex == 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    "Yearly average spend",
                    money(yearly.extrapolatedCost, state.homeCurrency),
                    Modifier.weight(1f),
                )
                StatCard(
                    "Past year spend",
                    money(yearly.actualCost, state.homeCurrency),
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    "Yearly average distance",
                    km(yearly.extrapolatedDistanceKm),
                    Modifier.weight(1f),
                )
                StatCard(
                    "Past year distance",
                    km(yearly.actualDistanceKm),
                    Modifier.weight(1f),
                )
            }
        } else {
            val spend = flowValue(window.costPerDay, periodDays)
            val distance = flowValue(window.distancePerDay, periodDays)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("$periodLabel spend", money(spend, state.homeCurrency), Modifier.weight(1f))
                StatCard("$periodLabel distance", km(distance), Modifier.weight(1f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Consumption", unit(ratios.lPer100Km, "L/100km"), Modifier.weight(1f))
            StatCard("Efficiency", unit(ratios.mpg, "mpg"), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Cost per km", money(ratios.costPerKm, state.homeCurrency), Modifier.weight(1f))
            StatCard("Avg price", money(ratios.avgPricePerLiter, state.homeCurrency) + "/L", Modifier.weight(1f))
        }

        ChartSection(title = "Cost per month") {
            val points = periodSeries(dataset, PeriodKind.MONTH).takeLast(12)
            LineChart(
                values = points.map { it.cost ?: 0.0 },
                labels = points.map { it.key.takeLast(2) },
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
        }
        ChartSection(title = "Consumption per refill (L/100km)") {
            val points = efficiencySeries(dataset).takeLast(12)
            LineChart(
                values = points.map { it.lPer100Km },
                labels = points.map { "${it.date.monthValue}/${it.date.dayOfMonth}" },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                padStart = 28.dp,
                emptyLabel = "No distance data",
            )
        }
    }
}

@Composable
private fun ChartSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun windowCaption(window: com.k1.gastracker.core.WindowResult, today: LocalDate): String {
    val start = window.start ?: return "no data"
    val days = ChronoUnit.DAYS.between(start, today).toInt() + 1
    val refills = if (window.nRefills == 1) "1 refill" else "${window.nRefills} refills"
    return when {
        days <= 28 -> "last $days days · $refills"
        days <= 90 -> "last 3 months · $refills"
        else -> "last year · $refills"
    }
}

private fun trim(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

private fun money(v: Double?, currency: String): String {
    if (v == null) return "-"
    return "${trim(v)} ${currency.trim()}"
}

private fun km(v: Double?): String = if (v == null) "-" else "${trim(v)} km"

private fun unit(v: Double?, suffix: String): String = if (v == null) "-" else "${trim(v)} $suffix"
