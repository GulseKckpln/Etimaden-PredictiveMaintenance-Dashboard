package com.example.mainactivity.ui.theme

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mainactivity.RawCsvRepository
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class AnomalyMethod { ZSCORE, MAD, CPMA }

data class AnomalyInfo(
    val index: Int,
    val header: String,
    val value: Float,
    val score: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(rows: List<List<String>>) {
    if (rows.isEmpty()) {
        Text("CSV verisi bulunamadı")
        return
    }

    val headers = rows.first()
    val dataRows = rows.drop(1)

    val numericCols = remember(dataRows) { RawCsvRepository.findNumericColumns(dataRows) }
    var selectedCol by remember { mutableStateOf(numericCols.firstOrNull() ?: 0) }
    var overlayCols by remember { mutableStateOf(setOf<Int>()) }
    var method by remember { mutableStateOf(AnomalyMethod.ZSCORE) }
    var threshold by remember { mutableStateOf(3.0f) }
    var recalcOnZoom by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    var previousAnomalyCount by remember { mutableStateOf(0) }

    val allValues = remember(dataRows) {
        numericCols.associateWith { RawCsvRepository.columnAsFloats(dataRows, it) }
    }
    val values = allValues[selectedCol] ?: emptyList()
    val fullRange = 0 to max(values.size - 1, 0)
    var visibleRange by remember(allValues) { mutableStateOf(fullRange) }

    // Diyalog state’i (anomali tıklama)
    var dialog by remember { mutableStateOf<AnomalyInfo?>(null) }

    fun computeScores(slice: List<Float>, m: AnomalyMethod): List<Float> = when (m) {
        AnomalyMethod.ZSCORE -> RawCsvRepository.zScore(slice)
        AnomalyMethod.MAD    -> RawCsvRepository.madScore(slice)
        AnomalyMethod.CPMA   -> RawCsvRepository.cpmaScore(slice, window = 5)
    }

    // Aktif aralık
    val activeRange = if (recalcOnZoom) visibleRange else fullRange
    val (startIdx, endIdx) = activeRange
    val clampedStart = max(0, min(startIdx, values.lastIndex))
    val clampedEnd   = max(0, min(endIdx, values.lastIndex))
    val slice = if (clampedEnd >= clampedStart) values.subList(clampedStart, clampedEnd + 1) else emptyList()

    // Seçili yöntem skorları
    val activeScores = remember(slice, method) { computeScores(slice, method) }

    // Grafik verileri (global index ile)
    val scoreEntries = activeScores.mapIndexed { k, v -> Entry((clampedStart + k).toFloat(), v) }
    val anomalyEntries = activeScores.mapIndexedNotNull { k, v ->
        if (v >= threshold) Entry((clampedStart + k).toFloat(), v) else null
    }

    // Eşik üstü bölge (boyama için): eşik altını boş (NaN), üstü skor
    val thresholdAreaEntries = activeScores.mapIndexed { k, v ->
        Entry((clampedStart + k).toFloat(), if (v >= threshold) v else Float.NaN)
    }

    // x-index -> (value, score) map (aktif aralık için)
    val xToInfo = remember(values, activeScores, clampedStart) {
        buildMap<Int, Pair<Float, Float>> {
            activeScores.forEachIndexed { k, s ->
                val idx = clampedStart + k
                val v = values.getOrNull(idx) ?: return@forEachIndexed
                put(idx, v to s)
            }
        }
    }

    val colName = headers.getOrNull(selectedCol) ?: "Kolon $selectedCol"
    val totalInRange = activeScores.size
    val anomalyCount = anomalyEntries.size
    val percent = if (totalInRange > 0) (anomalyCount.toFloat() / totalInRange) * 100f else 0f

    // Canlı alarm: yeni anomali artışı olursa snackbar
    LaunchedEffect(anomalyCount) {
        if (anomalyCount > previousAnomalyCount) {
            snackbar.showSnackbar("Yeni anomali tespit edildi (toplam $anomalyCount)")
        }
        previousAnomalyCount = anomalyCount
    }

    // (5) Karşılaştırma: aynı slice ve threshold için tüm yöntemlerde sayaç
    val compare = remember(slice, threshold) {
        val z = computeScores(slice, AnomalyMethod.ZSCORE)
        val m = computeScores(slice, AnomalyMethod.MAD)
        val c = computeScores(slice, AnomalyMethod.CPMA)
        val zc = z.count { it >= threshold }
        val mc = m.count { it >= threshold }
        val cc = c.count { it >= threshold }
        Triple(zc to (if (z.isNotEmpty()) zc * 100f / z.size else 0f),
            mc to (if (m.isNotEmpty()) mc * 100f / m.size else 0f),
            cc to (if (c.isNotEmpty()) cc * 100f / c.size else 0f))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("ETİMADEN – $colName") },
                actions = {
                    if (anomalyCount > 0) {
                        // Basit ve sürüm-dostu alarm göstergesi
                        Text("🔔 $anomalyCount", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Ana kolon seçimi
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ana Kolon: $colName")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    numericCols.forEach { colIndex ->
                        val name = headers.getOrNull(colIndex) ?: "Kolon $colIndex"
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                selectedCol = colIndex
                                // aralığı yeni kolona göre sıfırla
                                val n = allValues[colIndex]?.size ?: 0
                                visibleRange = 0 to max(n - 1, 0)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Overlay kolonlar
            var overlayExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { overlayExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Overlay Kolonlar (${overlayCols.size})")
                }
                DropdownMenu(expanded = overlayExpanded, onDismissRequest = { overlayExpanded = false }) {
                    LazyColumn {
                        items(numericCols.size) { idx ->
                            val colIndex = numericCols[idx]
                            val name = headers.getOrNull(colIndex) ?: "Kolon $colIndex"
                            val checked = overlayCols.contains(colIndex)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = checked, onCheckedChange = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(name)
                                    }
                                },
                                onClick = {
                                    overlayCols = if (checked) overlayCols - colIndex else overlayCols + colIndex
                                }
                            )
                        }
                    }
                }
            }

            // Yöntem seçimi
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MethodChip("Z-Score", method == AnomalyMethod.ZSCORE) { method = AnomalyMethod.ZSCORE }
                MethodChip("MAD",     method == AnomalyMethod.MAD)    { method = AnomalyMethod.MAD }
                MethodChip("CPMA",    method == AnomalyMethod.CPMA)   { method = AnomalyMethod.CPMA }
            }

            // Zoom switch
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zoom ile yeniden hesapla")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = recalcOnZoom,
                    onCheckedChange = {
                        recalcOnZoom = it
                        if (!it) visibleRange = fullRange
                    }
                )
            }

            // Özet kartı
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Özet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    val rangeTxt = if (recalcOnZoom) "[$clampedStart, $clampedEnd]" else "tüm seri"
                    Text("Aralık: $rangeTxt | N: $totalInRange | Anomali: $anomalyCount | %${"%.1f".format(percent)} | Eşik: ${"%.1f".format(threshold)} | ${method.name}")
                }
            }

            // (5) Model Karşılaştırma kartı
            ComparisonCard(compare = compare)

            // Eşik slider
            Column {
                Text("Eşik: ${"%.1f".format(threshold)}")
                Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 0.5f..6.0f)
            }

            // Grafik + listeners
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    buildChartWithOverlay(
                        ctx,
                        allValues,
                        overlayCols,
                        selectedCol,
                        scoreEntries,
                        anomalyEntries,
                        thresholdAreaEntries,  // (2) eşik üstü bölge
                        threshold,
                        onVisibleRangeChanged = { lowX, highX ->
                            val l = floor(lowX).toInt()
                            val h = ceil(highX).toInt()
                            visibleRange = max(0, l) to min((values.lastIndex), h)
                        },
                        onValueSelected = { xIndex, dataSetLabel ->
                            if (dataSetLabel == "Anomaliler") {
                                val info = xToInfo[xIndex] ?: return@buildChartWithOverlay
                                dialog = AnomalyInfo(
                                    index = xIndex,
                                    header = colName,
                                    value = info.first,
                                    score = info.second
                                )
                            }
                        }
                    )
                },
                update = { chart ->
                    val data = makeOverlayLineData(
                        allValues, overlayCols, selectedCol,
                        scoreEntries, anomalyEntries, thresholdAreaEntries
                    )
                    chart.data = data

                    chart.axisRight.removeAllLimitLines()
                    val ll = LimitLine(threshold, "Eşik ${"%.1f".format(threshold)}").apply {
                        lineColor = Color.RED
                        lineWidth = 2f
                        textColor = Color.RED
                    }
                    chart.axisRight.addLimitLine(ll)

                    setValueSelectedListener(chart) { xIndex, label ->
                        if (label == "Anomaliler") {
                            val info = xToInfo[xIndex] ?: return@setValueSelectedListener
                            dialog = AnomalyInfo(xIndex, colName, info.first, info.second)
                        }
                    }

                    chart.invalidate()
                }
            )

            // Detay diyaloğu
            dialog?.let { d ->
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text("Anomali Detayı") },
                    text = {
                        Column {
                            Text("Kolon: ${d.header}")
                            Text("Satır (index): ${d.index}")
                            Text("Değer: ${"%.5f".format(d.value)}")
                            Text("Skor: ${"%.3f".format(d.score)}")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { dialog = null }) { Text("Kapat") }
                    }
                )
            }
        }
    }
}

@Composable
private fun MethodChip(text: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(text) }, leadingIcon = if (selected) ({ Text("✓") }) else null)
}

@Composable
private fun ComparisonCard(
    compare: Triple<Pair<Int, Float>, Pair<Int, Float>, Pair<Int, Float>>
) {
    val (z, m, c) = compare
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Model Karşılaştırma (eşik üstü)", style = MaterialTheme.typography.titleMedium)
            ModelRow("Z-Score", z.first, z.second)
            ModelRow("MAD",     m.first, m.second)
            ModelRow("CPMA",    c.first, c.second)
        }
    }
}

@Composable
private fun ModelRow(name: String, count: Int, pct: Float) {
    Column {
        Text("$name – $count adet (%${"%.1f".format(pct)})")
        LinearProgressIndicator(progress = (pct / 100f).coerceIn(0f, 1f))
    }
}

private fun buildChartWithOverlay(
    context: Context,
    allValues: Map<Int, List<Float>>,
    overlayCols: Set<Int>,
    mainCol: Int,
    scores: List<Entry>,
    anomalies: List<Entry>,
    thresholdArea: List<Entry>,   // (2) eşik üstü alan
    threshold: Float,
    onVisibleRangeChanged: (Float, Float) -> Unit,
    onValueSelected: (xIndex: Int, dataSetLabel: String) -> Unit
): LineChart {
    val chart = LineChart(context)
    val data = makeOverlayLineData(allValues, overlayCols, mainCol, scores, anomalies, thresholdArea)
    chart.data = data

    chart.description.isEnabled = false
    chart.setTouchEnabled(true)
    chart.isDragEnabled = true
    chart.setScaleEnabled(true)
    chart.setPinchZoom(true)

    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    chart.xAxis.setDrawGridLines(false)
    chart.axisLeft.setDrawGridLines(true)
    chart.axisRight.setDrawGridLines(false)

    chart.legend.apply {
        form = Legend.LegendForm.LINE
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
    }

    val ll = LimitLine(threshold, "Eşik ${"%.1f".format(threshold)}").apply {
        lineColor = Color.RED
        lineWidth = 2f
        textColor = Color.RED
    }
    chart.axisRight.addLimitLine(ll)

    // Zoom/pan listener
    chart.setOnChartGestureListener(object : OnChartGestureListener {
        override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
        override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
            onVisibleRangeChanged(chart.lowestVisibleX, chart.highestVisibleX)
        }
        override fun onChartLongPressed(me: MotionEvent?) {}
        override fun onChartDoubleTapped(me: MotionEvent?) {}
        override fun onChartSingleTapped(me: MotionEvent?) {}
        override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
        override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
            onVisibleRangeChanged(chart.lowestVisibleX, chart.highestVisibleX)
        }
        override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
            onVisibleRangeChanged(chart.lowestVisibleX, chart.highestVisibleX)
        }
    })

    // Value selected listener
    setValueSelectedListener(chart, onValueSelected)

    chart.invalidate()
    return chart
}

private fun setValueSelectedListener(
    chart: LineChart,
    onValueSelected: (xIndex: Int, dataSetLabel: String) -> Unit
) {
    chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
        override fun onValueSelected(e: Entry?, h: Highlight?) {
            if (e == null || h == null) return
            val dsIndex = h.dataSetIndex
            val label = chart.data.getDataSetByIndex(dsIndex)?.label ?: ""
            onValueSelected(e.x.toInt(), label)
        }
        override fun onNothingSelected() {}
    })
}

private fun makeOverlayLineData(
    allValues: Map<Int, List<Float>>,
    overlayCols: Set<Int>,
    mainCol: Int,
    scores: List<Entry>,
    anomalies: List<Entry>,
    thresholdArea: List<Entry> // yeni
): LineData {
    val sets = mutableListOf<ILineDataSet>()

    // Overlay kolonlar
    var colorIdx = 0
    val palette = listOf(Color.CYAN, Color.MAGENTA, Color.GREEN, Color.GRAY, Color.BLACK)
    for (col in overlayCols) {
        val vals = allValues[col] ?: continue
        val entries = vals.mapIndexed { i, v -> Entry(i.toFloat(), v) }
        val set = LineDataSet(entries, "Overlay $col").apply {
            color = palette[colorIdx % palette.size]
            lineWidth = 1.2f
            setDrawCircles(false)
            mode = LineDataSet.Mode.LINEAR
        }
        sets.add(set)
        colorIdx++
    }

    // Ana kolon (orijinal seri)
    allValues[mainCol]?.let { vals ->
        val entries = vals.mapIndexed { i, v -> Entry(i.toFloat(), v) }
        val set = LineDataSet(entries, "Seri (ana)").apply {
            color = Color.CYAN
            lineWidth = 1.8f
            setDrawCircles(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        sets.add(set)
    }

    // (2) Eşik üstü bölge boyama (Skor üstü yarı saydam alan)
    val areaSet = LineDataSet(thresholdArea, "Eşik Üstü Bölge").apply {
        color = Color.RED
        setDrawFilled(true)
        fillColor = Color.RED
        fillAlpha = 60   // yarı saydam
        lineWidth = 0f   // çizgi görünmesin, sadece dolgu
        setDrawCircles(false)
        axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
    }
    sets.add(areaSet)

    // Skor ve Anomaliler (sağ eksen)
    val scoreSet = LineDataSet(scores, "Skor").apply {
        color = Color.BLUE
        lineWidth = 1.2f
        setDrawCircles(false)
        axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
    }
    val anomalySet = LineDataSet(anomalies, "Anomaliler").apply {
        color = Color.RED
        setCircleColor(Color.RED)
        lineWidth = 0f
        setDrawCircles(true)
        circleRadius = 4f
        setDrawValues(false)
        axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
    }

    sets.add(scoreSet)
    sets.add(anomalySet)

    return LineData(sets)
}
