package com.bandknife.tension.util

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** 測定セッションのノイズ・打撃波形を端末内に保存する。 */
object WaveformArchive {
    private const val ROOT = "waveforms"
    private val sessionFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun createSessionDir(context: Context): File {
        val name = sessionFormat.format(Date())
        val dir = File(context.filesDir, "$ROOT/$name")
        dir.mkdirs()
        return dir
    }

    fun relativePath(context: Context, file: File): String =
        file.absolutePath.removePrefix(context.filesDir.absolutePath).trimStart('/', '\\')

    fun resolvePath(context: Context, relative: String): File =
        File(context.filesDir, relative.trimStart('/', '\\'))

    fun writeWav(file: File, samples: DoubleArray, sampleRate: Int) {
        file.parentFile?.mkdirs()
        val clipped = DoubleArray(samples.size) { i ->
            max(-1.0, min(1.0, samples[i]))
        }
        val pcm = ByteBuffer.allocate(clipped.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in clipped) {
            pcm.putShort((sample * 32767.0).toInt().toShort())
        }
        val dataSize = pcm.capacity()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataSize)
        file.outputStream().use { out ->
            out.write(header.array())
            out.write(pcm.array())
        }
    }

    fun writeSpectrumCsv(
        file: File,
        spectrum: DoubleArray,
        minBin: Int,
        sampleRate: Int,
        bufferSize: Int
    ) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { writer ->
            writer.appendLine("bin,freq_hz,magnitude")
            for (i in spectrum.indices) {
                val bin = minBin + i
                val freq = bin * sampleRate.toDouble() / bufferSize
                writer.appendLine("$bin,${"%.3f".format(Locale.US, freq)},${spectrum[i]}")
            }
        }
    }
}
