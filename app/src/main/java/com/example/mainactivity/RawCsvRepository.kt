package com.example.mainactivity

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object RawCsvRepository {
    fun load(
        context: Context,
        rawResName: String = "ims_features_sample",
        assetsFileName: String = "ims_features_sample.csv"
    ): List<List<String>> {
        val resId = context.resources.getIdentifier(rawResName, "raw", context.packageName)
        if (resId != 0) {
            context.resources.openRawResource(resId).use { input -> return readCsvStream(input) }
        }
        return context.assets.open(assetsFileName).use { input -> readCsvStream(input) }
    }

    private fun readCsvStream(input: java.io.InputStream): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        BufferedReader(InputStreamReader(input)).useLines { lines ->
            lines.forEach { line ->
                val parts = if (line.contains(';')) line.split(';') else line.split(',')
                rows.add(parts.map { it.trim() })
            }
        }
        return rows
    }

    fun findNumericColumns(rows: List<List<String>>, minHits: Int = 5): List<Int> {
        if (rows.isEmpty()) return emptyList()
        val maxCols = rows.maxOf { it.size }
        val result = mutableListOf<Int>()
        for (col in 0 until maxCols) {
            var hits = 0
            for (r in rows) {
                val s = r.getOrNull(col)?.replace(',', '.')
                if (s?.toFloatOrNull() != null) hits++
                if (hits >= minHits) { result.add(col); break }
            }
        }
        return result
    }

    fun columnAsFloats(rows: List<List<String>>, colIndex: Int): List<Float> {
        val out = ArrayList<Float>(rows.size)
        for (r in rows) {
            val s = r.getOrNull(colIndex)?.replace(',', '.')
            s?.toFloatOrNull()?.let { out.add(it) }
        }
        return out
    }

    private fun mean(xs: List<Float>) = if (xs.isEmpty()) 0f else (xs.sum() / xs.size)
    private fun stddev(xs: List<Float>, mu: Float = mean(xs)): Float {
        if (xs.size <= 1) return 0f
        var s2 = 0.0
        for (v in xs) s2 += (v - mu).toDouble().pow(2.0)
        return sqrt(s2 / (xs.size - 1)).toFloat()
    }
    private fun median(xs: List<Float>): Float {
        if (xs.isEmpty()) return 0f
        val s = xs.sorted(); val n = s.size
        return if (n % 2 == 1) s[n/2] else ((s[n/2 - 1] + s[n/2]) / 2f)
    }

    fun zScore(values: List<Float>): List<Float> {
        if (values.isEmpty()) return emptyList()
        val mu = mean(values)
        val sd = stddev(values, mu).let { if (it == 0f) 1f else it }
        return values.map { kotlin.math.abs((it - mu) / sd) }
    }
    fun madScore(values: List<Float>): List<Float> {
        if (values.isEmpty()) return emptyList()
        val med = median(values)
        val absDev = values.map { kotlin.math.abs(it - med) }
        val mad = max(median(absDev) * 1.4826f, 1e-6f)
        return absDev.map { it / mad }
    }
    fun cpmaScore(values: List<Float>, window: Int = 5): List<Float> {
        if (values.isEmpty()) return emptyList()
        val n = values.size
        if (n < 2) return List(n) { 0f }
        val ma = FloatArray(n)
        var sum = 0f
        for (i in 0 until n) {
            sum += values[i]
            if (i >= window) sum -= values[i - window]
            val w = minOf(i + 1, window)
            ma[i] = sum / w
        }
        val residuals = (0 until n).map { values[it] - ma[it] }
        val medAbs = median(residuals.map { kotlin.math.abs(it) }) * 1.4826f
        val denom = max(medAbs, 1e-6f)
        return residuals.map { kotlin.math.abs(it) / denom }
    }
}
