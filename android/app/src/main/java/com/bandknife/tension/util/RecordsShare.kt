package com.bandknife.tension.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object RecordsShare {
    fun shareCsv(context: Context, csv: String) {
        val file = File(context.cacheDir, "measurement_records.csv")
        file.writeText(csv, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "CSVを共有"))
    }
}
