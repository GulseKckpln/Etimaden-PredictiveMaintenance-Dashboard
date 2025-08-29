package com.example.mainactivity

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.min

object SafeCsvLoader {
    private const val TAG = "SafeCsvLoader"

    /** raw/ → assets/ sırayla dener. maxRows (header hariç) kadar satır okur. */
    fun load(
        context: Context,
        rawResName: String = "ims_features_sample",
        assetsFileName: String = "ims_features_sample.csv",
        maxRows: Int = 20_000,
        onError: (String) -> Unit = {}
    ): List<List<String>> {
        // 1) res/raw
        runCatching {
            val resId = context.resources.getIdentifier(rawResName, "raw", context.packageName)
            if (resId != 0) {
                context.resources.openRawResource(resId).use { input ->
                    return readBuffered(BufferedReader(InputStreamReader(input, Charsets.UTF_8)), maxRows)
                }
            }
        }.onFailure { Log.w(TAG, "raw read failed: ${it.message}") }

        // 2) assets
        runCatching {
            context.assets.open(assetsFileName).use { input ->
                return readBuffered(BufferedReader(InputStreamReader(input, Charsets.UTF_8)), maxRows)
            }
        }.onFailure {
            val msg = "CSV bulunamadı/okunamadı: ${it.message}"
            Log.e(TAG, msg, it); onError(msg)
        }
        return emptyList()
    }

    private fun readBuffered(br: BufferedReader, maxRows: Int): List<List<String>> {
        val rows = ArrayList<List<String>>(min(maxRows + 1, 25_000))
        var first = true
        var dataCount = 0
        br.use { reader ->
            while (true) {
                val raw = reader.readLine() ?: break
                var ln = raw
                if (first && ln.isNotEmpty() && ln[0] == '\uFEFF') ln = ln.substring(1) // BOM
                if (ln.endsWith("\r")) ln = ln.dropLast(1)
                if (ln.isBlank()) { first = false; continue }

                rows.add(parseCsvLine(ln))

                if (!first) {
                    dataCount++
                    if (dataCount >= maxRows) break
                }
                first = false
            }
        }
        return rows
    }

    /** Tırnaklı alanları ve virgülü doğru ayrıştırır. */
    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when (c) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ }
                    else inQuotes = !inQuotes
                }
                ',' -> if (inQuotes) sb.append(c) else { out.add(sb.toString().trim()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }
}
