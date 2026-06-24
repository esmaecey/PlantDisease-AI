package com.ece.plantdiseaseapp

import android.graphics.Canvas
import android.graphics.Paint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PredictionItem(
    val label: String,
    val confidence: Float
)

data class PlantAnalysisResult(
    val speciesLabelEn: String,
    val speciesLabelTr: String,
    val speciesConfidence: Float,
    val speciesStatus: String,
    val speciesSource: String,
    val speciesTop3: List<PredictionItem>,

    val diseaseLabelEn: String,
    val diseaseLabelTr: String,
    val diseaseConfidence: Float,
    val diseaseModelName: String,
    val diseaseTop3: List<PredictionItem>,

    val recommendation: String,
    val warningText: String,
    val focusMapBitmap: Bitmap?,
    val isSpeciesReliable: Boolean,
    val isDiseaseReliable: Boolean
)

class PlantDiseaseAnalyzer(private val context: Context) : AutoCloseable {

    private val inputSize = 300

    private val speciesMainModelName = "species_main.tflite"
    private val speciesRealWorldModelName = "species_realworld.tflite"
    private val tomatoDiseaseModelName = "tomato_disease_clahe.tflite"
    private val generalDiseaseModelName = "general_disease_clahe.tflite"

    private val speciesMainLabelsName = "species_main_labels.txt"
    private val speciesRealWorldLabelsName = "species_realworld_labels.txt"
    private val tomatoDiseaseLabelsName = "tomato_disease_clahe_labels.txt"
    private val generalDiseaseLabelsName = "general_disease_clahe_labels.txt"

    private val interpreterOptions = Interpreter.Options().apply {
        setNumThreads(4)
    }

    private val speciesMainInterpreter =
        Interpreter(loadModelFile(speciesMainModelName), interpreterOptions)

    private val speciesRealWorldInterpreter =
        Interpreter(loadModelFile(speciesRealWorldModelName), interpreterOptions)

    private val tomatoDiseaseInterpreter =
        Interpreter(loadModelFile(tomatoDiseaseModelName), interpreterOptions)

    private val generalDiseaseInterpreter =
        Interpreter(loadModelFile(generalDiseaseModelName), interpreterOptions)

    private val speciesMainLabels = loadLabels(speciesMainLabelsName)
    private val speciesRealWorldLabels = loadLabels(speciesRealWorldLabelsName)
    private val tomatoDiseaseLabels = loadLabels(tomatoDiseaseLabelsName)
    private val generalDiseaseLabels = loadLabels(generalDiseaseLabelsName)

    fun analyze(
        bitmap: Bitmap,
        userSelectedSpecies: String? = null
    ): PlantAnalysisResult {
        val speciesDecision = if (userSelectedSpecies != null) {
            SpeciesDecision(
                label = userSelectedSpecies,
                confidence = 1.0f,
                status = "USER_SELECTED_SPECIES",
                source = "user_selection",
                isReliable = true,
                top3 = listOf(
                    PredictionItem(
                        label = userSelectedSpecies,
                        confidence = 1.0f
                    )
                )
            )
        } else {
            val speciesMainPredictions = predict(
                interpreter = speciesMainInterpreter,
                labels = speciesMainLabels,
                bitmap = bitmap,
                useClahe = false
            )

            val speciesRealWorldPredictions = predict(
                interpreter = speciesRealWorldInterpreter,
                labels = speciesRealWorldLabels,
                bitmap = bitmap,
                useClahe = false
            )

            decideSpecies(
                mainPredictions = speciesMainPredictions,
                realWorldPredictions = speciesRealWorldPredictions
            )
        }

        val selectedSpecies = speciesDecision.label
        val diseaseInterpreter: Interpreter
        val diseaseLabels: List<String>
        val diseaseModelText: String

        if (selectedSpecies == "tomato" && speciesDecision.isReliable) {
            diseaseInterpreter = tomatoDiseaseInterpreter
            diseaseLabels = tomatoDiseaseLabels
            diseaseModelText = "Domates Hastalık Modeli"
        } else {
            diseaseInterpreter = generalDiseaseInterpreter
            diseaseLabels = generalDiseaseLabels

            diseaseModelText = if (selectedSpecies == "tomato" && !speciesDecision.isReliable) {
                "Genel Hastalık Modeli - Tür belirsiz olduğu için kullanıldı"
            } else {
                "Genel Hastalık Modeli"
            }
        }

        val diseasePredictions = predict(
            interpreter = diseaseInterpreter,
            labels = diseaseLabels,
            bitmap = bitmap,
            useClahe = true
        )

        val diseaseTop1 = diseasePredictions[0]
        val diseaseTop2 = diseasePredictions.getOrNull(1)

        val diseaseMargin = if (diseaseTop2 != null) {
            diseaseTop1.confidence - diseaseTop2.confidence
        } else {
            diseaseTop1.confidence
        }

        val isDiseaseReliable =
            diseaseTop1.confidence >= 0.70f && diseaseMargin >= 0.15f
        val targetClassIndex = diseaseLabels.indexOf(diseaseTop1.label).coerceAtLeast(0)

        val focusMapBitmap = createOcclusionFocusMap(
            originalBitmap = bitmap,
            interpreter = diseaseInterpreter,
            labelsSize = diseaseLabels.size,
            targetClassIndex = targetClassIndex,
            baseConfidence = diseaseTop1.confidence,
            useClahe = true,
            gridSize = 6
        )

        val warningText = buildWarningText(
            isSpeciesReliable = speciesDecision.isReliable,
            isDiseaseReliable = isDiseaseReliable,
            diseaseLabel = diseaseTop1.label,
            diseaseConfidence = diseaseTop1.confidence,
            diseaseMargin = diseaseMargin
        )

        return PlantAnalysisResult(
            speciesLabelEn = selectedSpecies,
            speciesLabelTr = if (speciesDecision.isReliable) {
                translateSpecies(selectedSpecies)
            } else {
                "Belirsiz (${translateSpecies(selectedSpecies)} olası)"
            },
            speciesConfidence = speciesDecision.confidence,
            speciesStatus = speciesDecision.status,
            speciesSource = speciesDecision.source,
            speciesTop3 = speciesDecision.top3,

            diseaseLabelEn = diseaseTop1.label,
            diseaseLabelTr = translateDisease(diseaseTop1.label),
            diseaseConfidence = diseaseTop1.confidence,
            diseaseModelName = diseaseModelText,
            diseaseTop3 = diseasePredictions.take(4),

            recommendation = getRecommendation(diseaseTop1.label),
            warningText = warningText,
            focusMapBitmap = focusMapBitmap,
            isSpeciesReliable = speciesDecision.isReliable,
            isDiseaseReliable = isDiseaseReliable
        )
    }

    private data class SpeciesDecision(
        val label: String,
        val confidence: Float,
        val status: String,
        val source: String,
        val isReliable: Boolean,
        val top3: List<PredictionItem>
    )

    private fun decideSpecies(
        mainPredictions: List<PredictionItem>,
        realWorldPredictions: List<PredictionItem>
    ): SpeciesDecision {
        val mainTop1 = mainPredictions[0]
        val realTop1 = realWorldPredictions[0]

        val mainTop2 = mainPredictions.getOrNull(1)
        val realTop2 = realWorldPredictions.getOrNull(1)

        val mainMargin = if (mainTop2 != null) {
            mainTop1.confidence - mainTop2.confidence
        } else {
            mainTop1.confidence
        }

        val realMargin = if (realTop2 != null) {
            realTop1.confidence - realTop2.confidence
        } else {
            realTop1.confidence
        }

        /*
            Güvenilir tür kararı:
            Ana tür modeli ve gerçek veri modeli aynı sınıfı tahmin ederse
            sonuç güvenilir kabul edilir.
        */
        if (mainTop1.label == realTop1.label) {
            val confidence = max(mainTop1.confidence, realTop1.confidence)

            return SpeciesDecision(
                label = mainTop1.label,
                confidence = confidence,
                status = "RELIABLE_SPECIES_AGREEMENT",
                source = "normal+realworld_agreement",
                isReliable = confidence >= 0.70f,
                top3 = mainPredictions.take(3)
            )
        }

        /*
            İki model farklı tür tahmini yaptıysa:
            Sonuç yüksek güvenli görünse bile güvenilir kabul edilmez.
            Ekranda tür "Belirsiz" olarak gösterilecek.
        */
        val selectedPrediction = if (mainTop1.confidence >= realTop1.confidence) {
            mainTop1
        } else {
            realTop1
        }

        val selectedTop3 = if (mainTop1.confidence >= realTop1.confidence) {
            mainPredictions.take(3)
        } else {
            realWorldPredictions.take(3)
        }

        return SpeciesDecision(
            label = selectedPrediction.label,
            confidence = selectedPrediction.confidence,
            status = "UNCERTAIN_SPECIES_MODEL_DISAGREEMENT",
            source = "models_disagreed",
            isReliable = false,
            top3 = selectedTop3
        )
    }

    private fun predict(
        interpreter: Interpreter,
        labels: List<String>,
        bitmap: Bitmap,
        useClahe: Boolean
    ): List<PredictionItem> {
        val inputBuffer = bitmapToInputBuffer(
            bitmap = bitmap,
            interpreter = interpreter,
            useClahe = useClahe
        )

        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(inputBuffer, output)

        val probabilities = normalizeModelOutput(output[0])

        return labels.indices
            .map { index ->
                PredictionItem(
                    label = labels[index],
                    confidence = probabilities[index]
                )
            }
            .sortedByDescending { it.confidence }
    }

    private fun bitmapToInputBuffer(
        bitmap: Bitmap,
        interpreter: Interpreter,
        useClahe: Boolean
    ): ByteBuffer {
        val inputTensor = interpreter.getInputTensor(0)
        val inputType = inputTensor.dataType()

        val bytesPerChannel = when (inputType) {
            DataType.FLOAT32 -> 4
            DataType.UINT8 -> 1
            else -> 4
        }

        val byteBuffer = ByteBuffer.allocateDirect(
            1 * inputSize * inputSize * 3 * bytesPerChannel
        )
        byteBuffer.order(ByteOrder.nativeOrder())

        var resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        if (resizedBitmap.config != Bitmap.Config.ARGB_8888) {
            resizedBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        if (useClahe) {
            resizedBitmap = applyClaheLike(resizedBitmap)
        }

        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(
            pixels,
            0,
            inputSize,
            0,
            0,
            inputSize,
            inputSize
        )

        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            when (inputType) {
                DataType.FLOAT32 -> {
                    byteBuffer.putFloat(r.toFloat())
                    byteBuffer.putFloat(g.toFloat())
                    byteBuffer.putFloat(b.toFloat())
                }

                DataType.UINT8 -> {
                    byteBuffer.put(r.toByte())
                    byteBuffer.put(g.toByte())
                    byteBuffer.put(b.toByte())
                }

                else -> {
                    byteBuffer.putFloat(r.toFloat())
                    byteBuffer.putFloat(g.toFloat())
                    byteBuffer.putFloat(b.toFloat())
                }
            }
        }

        byteBuffer.rewind()
        return byteBuffer
    }

    private fun normalizeModelOutput(rawOutput: FloatArray): FloatArray {
        val sum = rawOutput.sum()
        val minValue = rawOutput.minOrNull() ?: 0f
        val maxValue = rawOutput.maxOrNull() ?: 0f

        return if (minValue >= 0f && maxValue <= 1f && sum > 0.90f && sum < 1.10f) {
            rawOutput
        } else {
            softmax(rawOutput)
        }
    }

    private fun softmax(values: FloatArray): FloatArray {
        val maxValue = values.maxOrNull() ?: 0f
        val exps = values.map { exp((it - maxValue).toDouble()) }
        val sum = exps.sum()

        return exps.map { (it / sum).toFloat() }.toFloatArray()
    }

    private fun applyClaheLike(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val tilesX = 8
        val tilesY = 8
        val tileWidth = (width + tilesX - 1) / tilesX
        val tileHeight = (height + tilesY - 1) / tilesY

        for (tileY in 0 until tilesY) {
            for (tileX in 0 until tilesX) {
                val startX = tileX * tileWidth
                val startY = tileY * tileHeight
                val endX = min(startX + tileWidth, width)
                val endY = min(startY + tileHeight, height)

                val hist = IntArray(256)
                var pixelCount = 0

                for (y in startY until endY) {
                    for (x in startX until endX) {
                        val pixel = src.getPixel(x, y)
                        val gray = rgbToGray(
                            Color.red(pixel),
                            Color.green(pixel),
                            Color.blue(pixel)
                        )
                        hist[gray]++
                        pixelCount++
                    }
                }

                val clipLimit = max(1, (pixelCount * 4.0 / 256.0).roundToInt())
                var excess = 0

                for (i in hist.indices) {
                    if (hist[i] > clipLimit) {
                        excess += hist[i] - clipLimit
                        hist[i] = clipLimit
                    }
                }

                val redistribute = excess / 256
                val remainder = excess % 256

                for (i in hist.indices) {
                    hist[i] += redistribute
                    if (i < remainder) hist[i] += 1
                }

                val lut = IntArray(256)
                var cumulative = 0

                for (i in hist.indices) {
                    cumulative += hist[i]
                    lut[i] = ((cumulative.toFloat() / pixelCount.toFloat()) * 255f)
                        .roundToInt()
                        .coerceIn(0, 255)
                }

                for (y in startY until endY) {
                    for (x in startX until endX) {
                        val pixel = src.getPixel(x, y)

                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)

                        val oldGray = rgbToGray(r, g, b)
                        val newGray = lut[oldGray]

                        val factor = if (oldGray == 0) {
                            1.0f
                        } else {
                            newGray.toFloat() / oldGray.toFloat()
                        }

                        val newR = (r * factor).roundToInt().coerceIn(0, 255)
                        val newG = (g * factor).roundToInt().coerceIn(0, 255)
                        val newB = (b * factor).roundToInt().coerceIn(0, 255)

                        output.setPixel(x, y, Color.rgb(newR, newG, newB))
                    }
                }
            }
        }

        return output
    }

    private fun rgbToGray(r: Int, g: Int, b: Int): Int {
        return (0.299 * r + 0.587 * g + 0.114 * b)
            .roundToInt()
            .coerceIn(0, 255)
    }

    private fun createOcclusionFocusMap(
        originalBitmap: Bitmap,
        interpreter: Interpreter,
        labelsSize: Int,
        targetClassIndex: Int,
        baseConfidence: Float,
        useClahe: Boolean,
        gridSize: Int = 6
    ): Bitmap {
        var resizedBitmap = Bitmap.createScaledBitmap(
            originalBitmap,
            inputSize,
            inputSize,
            true
        )

        if (resizedBitmap.config != Bitmap.Config.ARGB_8888) {
            resizedBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val cellWidth = inputSize / gridSize
        val cellHeight = inputSize / gridSize

        val importanceMap = Array(gridSize) { FloatArray(gridSize) }

        val averageColor = calculateAverageColor(resizedBitmap)

        val maskPaint = Paint().apply {
            color = averageColor
            style = Paint.Style.FILL
        }

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val left = col * cellWidth
                val top = row * cellHeight
                val right = if (col == gridSize - 1) inputSize else left + cellWidth
                val bottom = if (row == gridSize - 1) inputSize else top + cellHeight

                val maskedBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(maskedBitmap)

                canvas.drawRect(
                    left.toFloat(),
                    top.toFloat(),
                    right.toFloat(),
                    bottom.toFloat(),
                    maskPaint
                )

                val maskedConfidence = predictClassConfidence(
                    interpreter = interpreter,
                    labelsSize = labelsSize,
                    bitmap = maskedBitmap,
                    useClahe = useClahe,
                    targetClassIndex = targetClassIndex
                )

                val importance = (baseConfidence - maskedConfidence).coerceAtLeast(0f)
                importanceMap[row][col] = importance
            }
        }

        val maxImportance = importanceMap
            .flatMap { it.toList() }
            .maxOrNull()
            ?: 0f

        val outputBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val outputCanvas = Canvas(outputBitmap)

        if (maxImportance <= 0f) {
            return outputBitmap
        }

        val heatPaint = Paint().apply {
            style = Paint.Style.FILL
        }

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val strength = (importanceMap[row][col] / maxImportance).coerceIn(0f, 1f)

                if (strength > 0.05f) {
                    val left = col * cellWidth
                    val top = row * cellHeight
                    val right = if (col == gridSize - 1) inputSize else left + cellWidth
                    val bottom = if (row == gridSize - 1) inputSize else top + cellHeight

                    heatPaint.color = heatColor(strength)

                    outputCanvas.drawRect(
                        left.toFloat(),
                        top.toFloat(),
                        right.toFloat(),
                        bottom.toFloat(),
                        heatPaint
                    )
                }
            }
        }

        return outputBitmap
    }

    private fun predictClassConfidence(
        interpreter: Interpreter,
        labelsSize: Int,
        bitmap: Bitmap,
        useClahe: Boolean,
        targetClassIndex: Int
    ): Float {
        val inputBuffer = bitmapToInputBuffer(
            bitmap = bitmap,
            interpreter = interpreter,
            useClahe = useClahe
        )

        val output = Array(1) { FloatArray(labelsSize) }
        interpreter.run(inputBuffer, output)

        val probabilities = normalizeModelOutput(output[0])

        return probabilities[targetClassIndex]
    }

    private fun calculateAverageColor(bitmap: Bitmap): Int {
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var count = 0L

        val step = 6

        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)

                redSum += Color.red(pixel)
                greenSum += Color.green(pixel)
                blueSum += Color.blue(pixel)
                count++
            }
        }

        val r = (redSum / count).toInt().coerceIn(0, 255)
        val g = (greenSum / count).toInt().coerceIn(0, 255)
        val b = (blueSum / count).toInt().coerceIn(0, 255)

        return Color.rgb(r, g, b)
    }

    private fun heatColor(strength: Float): Int {
        val s = strength.coerceIn(0f, 1f)

        val alpha = (70 + 150 * s).roundToInt().coerceIn(70, 220)
        val red = (255 * s).roundToInt().coerceIn(0, 255)
        val green = (255 * (1f - kotlin.math.abs(s - 0.5f) * 2f)).roundToInt().coerceIn(0, 255)
        val blue = (255 * (1f - s)).roundToInt().coerceIn(0, 255)

        return Color.argb(alpha, red, green, blue)
    }
    private fun buildWarningText(
        isSpeciesReliable: Boolean,
        isDiseaseReliable: Boolean,
        diseaseLabel: String,
        diseaseConfidence: Float,
        diseaseMargin: Float
    ): String {
        val warnings = mutableListOf<String>()

        if (!isSpeciesReliable) {
            warnings.add("Tür modelleri arasında yeterli uyum oluşmadığı için tür tahmini belirsiz kabul edilmiştir. Daha net, yakın çekim ve tek yaprak içeren bir görüntü ile tekrar analiz yapılmalıdır.")
        }

        if (!isDiseaseReliable) {
            warnings.add("Hastalık güven skoru düşük/orta düzeydedir. Sonuç kesin tanı olarak değerlendirilmemelidir.")
        }

        if (diseaseMargin < 0.15f) {
            warnings.add("Hastalık tahmininde ilk iki sınıf birbirine yakın olduğu için model kararsız olabilir.")
        }

        if (diseaseLabel == "Healthy" && diseaseConfidence < 0.75f) {
            warnings.add("Sağlıklı tahmini düşük/orta güvenle üretildiği için kesin sağlıklı kabul edilmemelidir.")
        }

        warnings.add("Bu sistem kesin tanı amacıyla değil, karar destek amacıyla geliştirilmiştir.")

        return warnings.joinToString(separator = "\n\n")
    }

    private fun getRecommendation(diseaseLabel: String): String {
        return when (diseaseLabel) {
            "Healthy" -> {
                "Yaprak sağlıklı sınıfına yakın bulunmuştur. Ancak güven skoru düşükse sonuç kesin kabul edilmemeli, bitki düzenli olarak gözlemlenmelidir."
            }

            "Fungal_Disease" -> {
                "Yaprakta mantar hastalığına benzer belirtiler tespit edilmiştir. Fazla nemden kaçınılmalı, hava sirkülasyonu artırılmalı ve gerekli durumda uzman görüşü alınmalıdır."
            }

            "Bacterial_Disease" -> {
                "Yaprakta bakteriyel hastalığa benzer belirtiler tespit edilmiştir. Etkilenen yapraklar takip edilmeli, sulama sırasında yaprakların ıslatılmamasına dikkat edilmeli ve uzman desteği alınmalıdır."
            }

            "Viral_Disease" -> {
                "Yaprakta viral hastalığa benzer belirtiler tespit edilmiştir. Bitki diğer sağlıklı bitkilerden gözlemlenerek ayrılmalı, zararlı böcek kontrolü yapılmalı ve uzman görüşü alınmalıdır."
            }

            else -> {
                "Sonuç dikkatli değerlendirilmelidir. Daha net bir görüntü ile tekrar analiz yapılması önerilir."
            }
        }
    }

    private fun translateSpecies(label: String): String {
        return when (label) {
            "corn" -> "Mısır"
            "cucumber" -> "Salatalık"
            "eggplant" -> "Patlıcan"
            "pepper" -> "Biber"
            "potato" -> "Patates"
            "strawberry" -> "Çilek"
            "tomato" -> "Domates"
            else -> label
        }
    }

    private fun translateDisease(label: String): String {
        return when (label) {
            "Healthy" -> "Sağlıklı"
            "Fungal_Disease" -> "Mantar Hastalığı"
            "Bacterial_Disease" -> "Bakteriyel Hastalık"
            "Viral_Disease" -> "Viral Hastalık"
            else -> label
        }
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel

        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun loadLabels(fileName: String): List<String> {
        return context.assets.open(fileName)
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val parts = line.split(" ")
                if (parts.size > 1 && parts[0].all { char -> char.isDigit() }) {
                    parts.drop(1).joinToString(" ")
                } else {
                    line
                }
            }
    }

    override fun close() {
        speciesMainInterpreter.close()
        speciesRealWorldInterpreter.close()
        tomatoDiseaseInterpreter.close()
        generalDiseaseInterpreter.close()
    }
}