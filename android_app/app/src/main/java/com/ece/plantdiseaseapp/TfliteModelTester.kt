package com.ece.plantdiseaseapp

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object TfliteModelTester {

    private const val TAG = "TFLITE_TEST"

    private val modelFiles = listOf(
        "species_main.tflite",
        "species_realworld.tflite",
        "tomato_disease_clahe.tflite",
        "general_disease_clahe.tflite"
    )

    private val labelFiles = listOf(
        "species_main_labels.txt",
        "species_realworld_labels.txt",
        "tomato_disease_clahe_labels.txt",
        "general_disease_clahe_labels.txt"
    )

    fun runTest(context: Context) {
        Log.d(TAG, "------------------------------")
        Log.d(TAG, "TFLite model ve label testi başladı")
        Log.d(TAG, "------------------------------")

        testAssetList(context)
        testLabels(context)
        testModels(context)

        Log.d(TAG, "------------------------------")
        Log.d(TAG, "TFLite test tamamlandı")
        Log.d(TAG, "------------------------------")
    }

    private fun testAssetList(context: Context) {
        try {
            val assetList = context.assets.list("")?.toList().orEmpty()
            Log.d(TAG, "Assets klasörü içeriği:")

            assetList.forEach {
                Log.d(TAG, "- $it")
            }

            val missingModels = modelFiles.filter { it !in assetList }
            val missingLabels = labelFiles.filter { it !in assetList }

            if (missingModels.isEmpty() && missingLabels.isEmpty()) {
                Log.d(TAG, "Tüm gerekli model ve label dosyaları assets içinde bulundu.")
            } else {
                if (missingModels.isNotEmpty()) {
                    Log.e(TAG, "Eksik model dosyaları: $missingModels")
                }
                if (missingLabels.isNotEmpty()) {
                    Log.e(TAG, "Eksik label dosyaları: $missingLabels")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Assets klasörü okunurken hata oluştu: ${e.message}", e)
        }
    }

    private fun testLabels(context: Context) {
        labelFiles.forEach { labelFile ->
            try {
                val labels = loadLabels(context, labelFile)

                Log.d(TAG, "$labelFile başarıyla okundu.")
                Log.d(TAG, "$labelFile sınıf sayısı: ${labels.size}")

                labels.forEachIndexed { index, label ->
                    Log.d(TAG, "$labelFile -> $index: $label")
                }

            } catch (e: Exception) {
                Log.e(TAG, "$labelFile okunamadı: ${e.message}", e)
            }
        }
    }

    private fun testModels(context: Context) {
        modelFiles.forEach { modelFile ->
            var interpreter: Interpreter? = null

            try {
                val modelBuffer = loadModelFile(context, modelFile)

                val options = Interpreter.Options()
                options.setNumThreads(4)

                interpreter = Interpreter(modelBuffer, options)

                val inputTensor = interpreter.getInputTensor(0)
                val outputTensor = interpreter.getOutputTensor(0)

                Log.d(TAG, "$modelFile başarıyla yüklendi.")
                Log.d(TAG, "$modelFile input shape: ${inputTensor.shape().contentToString()}")
                Log.d(TAG, "$modelFile input type: ${inputTensor.dataType()}")
                Log.d(TAG, "$modelFile output shape: ${outputTensor.shape().contentToString()}")
                Log.d(TAG, "$modelFile output type: ${outputTensor.dataType()}")

            } catch (e: Exception) {
                Log.e(TAG, "$modelFile yüklenemedi: ${e.message}", e)
            } finally {
                interpreter?.close()
            }
        }
    }

    private fun loadModelFile(context: Context, assetName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(assetName)

        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel

        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }

    private fun loadLabels(context: Context, assetName: String): List<String> {
        val labels = mutableListOf<String>()

        context.assets.open(assetName).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?

                while (true) {
                    line = reader.readLine()
                    if (line == null) break

                    val cleanLine = line!!.trim()
                    if (cleanLine.isNotEmpty()) {
                        labels.add(cleanLine)
                    }
                }
            }
        }

        return labels
    }
}