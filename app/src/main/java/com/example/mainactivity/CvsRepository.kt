package com.example.mainactivity

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object CsvRepository {

    private const val TAG = "CsvRepository"

    /**
     * Assets içinden CSV okur ve her satırı String listesi olarak döner.
     * - UTF-8 BOM temizlenir
     * - CRLF/CR normalleştirilir
     * - Tırnaklı alanlar desteklenir ("a,b","c") → virgül korunur
     * - Boş/whitespace satırlar atılır
     */
    fun loadCsvFromAssets(context: Context, fileName: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        try {
            context.assets.open(fileName).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { br ->
                    // Tüm dosyayı tek seferde okumak yerine satır satır ilerleyelim
                    var line: String?
                    var first = true
                    while (true) {
                        line = br.readLine() ?: break
                        var ln = line!!

                        // UTF-8 BOM temizle (ilk satır)
                        if (first && ln.isNotEmpty() && ln[0] == '\uFEFF') {
                            ln = ln.substring(1)
                        }
                        first = false

                        // CR karakterleri varsa temizle
                        if (ln.endsWith("\r")) ln = ln.substring(0, ln.length - 1)
                        if (ln.isBlank()) continue

                        val cols = parseCsvLine(ln)
                        rows.add(cols)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CSV okuma hatası: ${e.message}", e)
            throw e
        }

        Log.d(TAG, "CSV okundu: satır=${rows.size}  ilkSatır=${rows.firstOrNull()}")
        return rows
    }

    /**
     * Basit CSV ayrıştırıcı:
     * - Virgül ayraç (',')
     * - Çift tırnak içinde virgül/çift tırnak kaçışı ("") desteklenir
     */
    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when (c) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // kaçışlı çift tırnak ("") → tek tırnak olarak ekle
                        sb.append('"')
                        i++ // bir karakter atla
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> {
                    if (inQuotes) {
                        sb.append(c)
                    } else {
                        out.add(sb.toString().trim())
                        sb.setLength(0)
                    }
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }
}
