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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.k1.gastracker.R
import com.k1.gastracker.core.ConvertedCost
import com.k1.gastracker.core.Dataset
import com.k1.gastracker.core.PeriodKind
import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.applyConvertedCost
import com.k1.gastracker.core.buildDataset
import com.k1.gastracker.core.efficiencySeries
import com.k1.gastracker.core.flowValue
import com.k1.gastracker.core.periodSeries
import com.k1.gastracker.core.recentWindow
import com.k1.gastracker.core.windowRatios
import com.k1.gastracker.core.yearlyView
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

private val PERIOD_DAYS = listOf(1.0, 7.0, 28.0, 365.0)
private val CHART_CURRENCIES = listOf("EUR", "USD", "GBP", "CAD", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "TRY")

private enum class ConsumptionType { L_PER_100_KM, MPG, KM_PER_L }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(state: UiState, actions: GasTrackerActions) {
    val today = LocalDate.now()
    val periodLabels = listOf(
        stringResource(R.string.period_daily),
        stringResource(R.string.period_weekly),
        stringResource(R.string.period_monthly),
        stringResource(R.string.period_yearly),
    )
    val consumptionLabels = mapOf(
        ConsumptionType.L_PER_100_KM to stringResource(R.string.unit_l_per_100),
        ConsumptionType.MPG to stringResource(R.string.unit_mpg),
        ConsumptionType.KM_PER_L to stringResource(R.string.unit_km_per_l),
    )

    var chartCurrency by rememberSaveable { mutableStateOf(state.homeCurrency) }
    var chartCosts by remember { mutableStateOf<Map<Refill, ConvertedCost>>(emptyMap()) }

    LaunchedEffect(state.refills, chartCurrency) {
        chartCosts = actions.convertedCosts(chartCurrency)
    }

    val convertedRefills = remember(state.refills, chartCosts, chartCurrency) {
        state.refills.map { applyConvertedCost(it, chartCosts[it], chartCurrency) }
    }
    val dataset = remember(convertedRefills) {
        runCatching { buildDataset(convertedRefills) }.getOrDefault(Dataset(emptyList()))
    }

    var periodIndex by rememberSaveable { mutableIntStateOf(2) }
    val periodLabel = periodLabels[periodIndex]
    val periodDays = PERIOD_DAYS[periodIndex]
    var consumptionType by rememberSaveable { mutableStateOf(ConsumptionType.L_PER_100_KM) }

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
    val oneRefill = stringResource(R.string.one_refill)
    val nRefills = stringResource(R.string.n_refills, window.nRefills)
    val refillLabel = if (window.nRefills == 1) oneRefill else nRefills
    val windowCaption = windowCaptionText(window, today, refillLabel)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.fxError?.let { error ->
            Text(
                stringResource(R.string.fx_error, error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.fxUnavailableCount > 0) {
            Text(
                stringResource(R.string.fx_unavailable, state.fxUnavailableCount),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = actions::retryFx) {
                Text(stringResource(R.string.fx_retry))
            }
        }

        Text(stringResource(R.string.chart_currency), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CHART_CURRENCIES.forEach { currency ->
                FilterChip(
                    selected = chartCurrency == currency,
                    onClick = { chartCurrency = currency },
                    label = { Text(currency) },
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            periodLabels.forEachIndexed { i, label ->
                FilterChip(
                    selected = periodIndex == i,
                    onClick = { periodIndex = i },
                    label = { Text(label) },
                )
            }
        }

        Text(
            stringResource(R.string.based_on, windowCaption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (periodIndex == 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    stringResource(R.string.yearly_average_spend),
                    money(yearly.extrapolatedCost, chartCurrency),
                    Modifier.weight(1f),
                    estimated = yearly.extrapolatedCost != null,
                )
                StatCard(
                    stringResource(R.string.past_year_spend),
                    money(yearly.actualCost, chartCurrency),
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    stringResource(R.string.yearly_average_distance),
                    km(yearly.extrapolatedDistanceKm),
                    Modifier.weight(1f),
                    estimated = yearly.extrapolatedDistanceKm != null,
                )
                StatCard(
                    stringResource(R.string.past_year_distance),
                    km(yearly.actualDistanceKm),
                    Modifier.weight(1f),
                )
            }
            if (yearly.extrapolatedCost == null && yearly.nRefills > 0) {
                Text(
                    stringResource(R.string.too_little_history, periodLabel.lowercase(Locale.US)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (window.canExtrapolate) {
            val spend = flowValue(window.costPerDay, periodDays)
            val distance = flowValue(window.distancePerDay, periodDays)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.period_spend, periodLabel), money(spend, chartCurrency), Modifier.weight(1f), estimated = true)
                StatCard(stringResource(R.string.period_distance, periodLabel), km(distance), Modifier.weight(1f), estimated = true)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.spend_in_view), money(window.totalCost, chartCurrency), Modifier.weight(1f))
                StatCard(stringResource(R.string.distance_in_view), km(window.totalDistanceKm), Modifier.weight(1f))
            }
            if (window.nRefills > 0) {
                Text(
                    stringResource(R.string.too_little_history, periodLabel.lowercase(Locale.US)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.consumption), unit(ratios.lPer100Km, "L/100km"), Modifier.weight(1f))
            StatCard(stringResource(R.string.efficiency), unit(ratios.mpg, "mpg"), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.cost_per_km), money(ratios.costPerKm, chartCurrency), Modifier.weight(1f))
            StatCard(stringResource(R.string.avg_price), money(ratios.avgPricePerLiter, chartCurrency) + "/L", Modifier.weight(1f))
        }

        val monthly = periodSeries(dataset, PeriodKind.MONTH).takeLast(12)
        ChartSection(title = stringResource(R.string.cost_per_month_chart)) {
            LineChart(
                values = monthly.map { it.cost ?: 0.0 },
                labels = monthly.map { it.key.takeLast(2) },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentDescription = stringResource(
                    R.string.chart_semantics,
                    stringResource(R.string.cost_per_month_chart),
                    monthly.size,
                    monthly.lastOrNull()?.cost?.let { money(it, chartCurrency) } ?: "-",
                ),
            )
        }

        ChartSection(title = stringResource(R.string.consumption_per_refill)) {
            Text(
                stringResource(R.string.show_as),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConsumptionType.entries.forEach { type ->
                    FilterChip(
                        selected = consumptionType == type,
                        onClick = { consumptionType = type },
                        label = { Text(consumptionLabels.getValue(type)) },
                    )
                }
            }
            val points = efficiencySeries(dataset).takeLast(12)
            val values = points.map {
                when (consumptionType) {
                    ConsumptionType.L_PER_100_KM -> it.lPer100Km
                    ConsumptionType.MPG -> it.mpg
                    ConsumptionType.KM_PER_L -> it.kmPerL
                }
            }
            LineChart(
                values = values,
                labels = points.map { "${it.date.monthValue}/${it.date.dayOfMonth}" },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                padStart = 28.dp,
                emptyLabel = stringResource(R.string.no_distance_data),
                contentDescription = stringResource(
                    R.string.chart_semantics,
                    stringResource(R.string.consumption_per_refill),
                    points.size,
                    values.lastOrNull()?.let { trim(it) } ?: "-",
                ),
            )
        }

        ChartSection(title = stringResource(R.string.price_per_liter_chart, chartCurrency)) {
            val points = efficiencySeries(dataset).takeLast(12).filter { it.pricePerVolume != null }
            LineChart(
                values = points.map { it.pricePerVolume ?: 0.0 },
                labels = points.map { "${it.date.monthValue}/${it.date.dayOfMonth}" },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                padStart = 28.dp,
                emptyLabel = stringResource(R.string.no_cost_data),
                contentDescription = stringResource(
                    R.string.chart_semantics,
                    stringResource(R.string.price_per_liter_chart, chartCurrency),
                    points.size,
                    points.lastOrNull()?.pricePerVolume?.let { trim(it) } ?: "-",
                ),
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
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    estimated: Boolean = false,
) {
    val description = buildString {
        append(label)
        append(": ")
        append(value)
        if (estimated) append(". estimated")
        if (value == "-") append(". unavailable")
    }
    Card(modifier.semantics { contentDescription = description }) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
            if (estimated && value != "-") {
                Text(
                    stringResource(R.string.metric_estimated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (value == "-") {
                Text(
                    stringResource(R.string.metric_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun windowCaptionText(
    window: com.k1.gastracker.core.WindowResult,
    today: LocalDate,
    refillLabel: String,
): String {
    val start = window.start ?: return "no data"
    val days = ChronoUnit.DAYS.between(start, today).toInt() + 1
    return when {
        days <= 28 -> "last $days days · $refillLabel"
        days <= 90 -> "last 3 months · $refillLabel"
        else -> "last year · $refillLabel"
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
