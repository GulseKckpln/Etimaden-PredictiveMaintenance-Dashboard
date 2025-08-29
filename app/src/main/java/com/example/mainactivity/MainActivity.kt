package com.example.mainactivity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.mainactivity.ui.theme.ChartsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rows = RawCsvRepository.load(
            context = this,
            rawResName = "ims_features_sample",         // res/raw/ims_features_sample.csv
            assetsFileName = "ims_features_sample.csv"  // assets fallback
        )

        setContent {
            Surface(color = MaterialTheme.colorScheme.background) {
                ChartsScreen(rows = rows)
            }
        }
    }
}
