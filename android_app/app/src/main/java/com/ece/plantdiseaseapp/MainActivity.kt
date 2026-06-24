package com.ece.plantdiseaseapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ece.plantdiseaseapp.ui.theme.PlantDiseaseAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PlantDiseaseAppTheme {
                PlantDiseaseScreen()
            }
        }
    }
}

@Composable
fun PlantDiseaseScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedSpecies by remember { mutableStateOf<String?>(null) }

    var analysisResult by remember { mutableStateOf<PlantAnalysisResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val analyzer = remember {
        PlantDiseaseAnalyzer(context.applicationContext)
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                try {
                    selectedBitmap = loadBitmapFromUri(context, uri)
                    analysisResult = null
                    errorMessage = null
                } catch (e: Exception) {
                    errorMessage = "Kamera görüntüsü yüklenemedi: ${e.message}"
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                selectedBitmap = loadBitmapFromUri(context, it)
                analysisResult = null
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Galeri görüntüsü yüklenemedi: ${e.message}"
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color(0xFFF4F8F3)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
            ) {
                HeaderSection(
                    title = if (analysisResult == null) {
                        "Bitki Hastalığı\nKarar Destek Sistemi"
                    } else {
                        "Analiz Sonucu"
                    },
                    subtitle = if (analysisResult == null) {
                        "Yaprak görüntüsünden bitki türü ve hastalık durumu tahmini"
                    } else {
                        "Yüklenen yaprak görüntüsüne ait karar destek sonucu"
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ImageSelectionCard(
                        selectedBitmap = selectedBitmap,
                        onCameraClick = {
                            try {
                                val uri = createCameraImageUri(context)
                                cameraImageUri = uri
                                cameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                errorMessage = "Kamera başlatılamadı: ${e.message}"
                            }
                        },
                        onGalleryClick = {
                            galleryLauncher.launch("image/*")
                        }
                    )

                    SpeciesSelectionCard(
                        selectedSpecies = selectedSpecies,
                        onSpeciesSelected = {
                            selectedSpecies = it
                            analysisResult = null
                            errorMessage = null
                        }
                    )

                    Button(
                        onClick = {
                            val bitmap = selectedBitmap

                            if (bitmap == null) {
                                errorMessage = "Lütfen önce kamera veya galeriden bir yaprak görüntüsü seçin."
                                return@Button
                            }

                            isAnalyzing = true
                            errorMessage = null

                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.Default) {
                                        analyzer.analyze(
                                            bitmap = bitmap,
                                            userSelectedSpecies = selectedSpecies
                                        )
                                    }

                                    analysisResult = result
                                } catch (e: Exception) {
                                    errorMessage = "Analiz sırasında hata oluştu: ${e.message}"
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        enabled = selectedBitmap != null && !isAnalyzing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4E67AD),
                            disabledContainerColor = Color(0xFFB7C0D9)
                        )
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Text("Analiz Ediliyor...")
                        } else {
                            Text(
                                text = "Analiz Et",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    errorMessage?.let {
                        WarningCard(
                            title = "Hata",
                            text = it,
                            titleColor = Color(0xFFD32F2F),
                            backgroundColor = Color(0xFFFFEBEE)
                        )
                    }

                    if (analysisResult == null) {
                        EmptyResultCards()
                    } else {
                        AnalysisResultCards(
                            bitmap = selectedBitmap,
                            result = analysisResult!!,
                            onNewAnalysisClick = {
                                analysisResult = null
                                selectedBitmap = null
                                errorMessage = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSection(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(175.dp)
            .clip(
                RoundedCornerShape(
                    bottomStart = 34.dp,
                    bottomEnd = 34.dp
                )
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B7D2B),
                        Color(0xFF3DBB5F)
                    )
                )
            )
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 29.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ImageSelectionCard(
    selectedBitmap: Bitmap?,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF0F7F0))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF5EC56E),
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selectedBitmap != null) {
                    Image(
                        bitmap = selectedBitmap.asImageBitmap(),
                        contentDescription = "Seçilen yaprak görüntüsü",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(27.dp))
                                .background(Color(0xFFE1F2E2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🌿",
                                fontSize = 28.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Yaprak Görüntüsü",
                            color = Color(0xFF0B6F28),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Kamera veya galeriden görüntü seçiniz",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCameraClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Kamera",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B4F20)
                    )
                }

                OutlinedButton(
                    onClick = onGalleryClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Galeri",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B4F20)
                    )
                }
            }
        }
    }
}

@Composable
fun SpeciesSelectionCard(
    selectedSpecies: String?,
    onSpeciesSelected: (String?) -> Unit
) {
    val speciesOptions: List<Pair<String?, String>> = listOf(
        null to "Otomatik",
        "tomato" to "Domates",
        "potato" to "Patates",
        "pepper" to "Biber",
        "cucumber" to "Salatalık",
        "corn" to "Mısır",
        "eggplant" to "Patlıcan",
        "strawberry" to "Çilek"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = "Bitki Türü Seçimi",
                color = Color(0xFF0B6F28),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bitki türünü biliyorsanız seçebilirsiniz. Otomatik seçilirse tür model tarafından tahmin edilir.",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            speciesOptions.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { option ->
                        val value = option.first
                        val label = option.second
                        val isSelected = selectedSpecies == value

                        OutlinedButton(
                            onClick = {
                                onSpeciesSelected(value)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) {
                                    Color(0xFFE0F3E5)
                                } else {
                                    Color.White
                                },
                                contentColor = if (isSelected) {
                                    Color(0xFF0B6F28)
                                } else {
                                    Color(0xFF333333)
                                }
                            )
                        ) {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun EmptyResultCards() {
    SmallResultCard(
        number = "1",
        title = "Bitki Türü",
        value = "Henüz analiz yapılmadı",
        confidence = "Tür seçimi veya otomatik tahmin bekleniyor.",
        badgeText = null,
        badgeColor = Color(0xFFE2F3E6),
        titleColor = Color(0xFF0B6F28)
    )

    SmallResultCard(
        number = "2",
        title = "Hastalık Durumu",
        value = "Henüz analiz yapılmadı",
        confidence = "Güven skoru: -",
        badgeText = null,
        badgeColor = Color(0xFFFFE2CC),
        titleColor = Color(0xFFFF7A00)
    )

    WarningCard(
        title = "Uyarı",
        text = "Bu sistem kesin tanı amacıyla değil, karar destek amacıyla geliştirilmiştir. Düşük güven skorlarında daha net ve yakın çekim bir yaprak görüntüsüyle tekrar analiz yapılmalıdır.",
        titleColor = Color(0xFFFF7A00),
        backgroundColor = Color(0xFFFFF3D6)
    )
}

@Composable
fun AnalysisResultCards(
    bitmap: Bitmap?,
    result: PlantAnalysisResult,
    onNewAnalysisClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = "Görüntü ve Bölgesel Önem Haritası",
                color = Color(0xFF0B6F28),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF0F7F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Orijinal görüntü",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text("🌿", fontSize = 34.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Orijinal",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF0F4F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (result.focusMapBitmap != null) {
                            Image(
                                bitmap = result.focusMapBitmap.asImageBitmap(),
                                contentDescription = "Bölgesel önem haritası",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = "Harita yok",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Önem Haritası",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Bu harita, görüntü bölgeleri sırayla maskelendiğinde hastalık güven skorundaki değişime göre oluşturulmuştur. Daha belirgin bölgeler model kararını daha fazla etkilemiştir.",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }

    SmallResultCard(
        number = "1",
        title = "Bitki Türü",
        value = result.speciesLabelTr,
        confidence = when {
            result.speciesSource == "user_selection" -> {
                "Bitki türü kullanıcı tarafından seçilmiştir."
            }

            result.isSpeciesReliable -> {
                "Tür tahmini iki modelin uyumlu sonucu ile güvenilir seviyededir."
            }

            else -> {
                "Tür modelleri arasında kararsızlık olabilir. Sonuç kesin kabul edilmemelidir."
            }
        },
        badgeText = if (result.speciesSource == "user_selection") {
            "Seçildi"
        } else {
            "%${formatPercent(result.speciesConfidence)}"
        },
        badgeColor = if (result.isSpeciesReliable) {
            Color(0xFFDDF3E3)
        } else {
            Color(0xFFFFE8C7)
        },
        titleColor = Color(0xFF0B6F28)
    )

    SmallResultCard(
        number = "2",
        title = "Hastalık Durumu",
        value = result.diseaseLabelTr,
        confidence = if (result.isDiseaseReliable) {
            "Hastalık sonucu güvenilir seviyededir."
        } else {
            "Hastalık sonucu orta/düşük güven düzeyindedir. Kesin tanı olarak değerlendirilmemelidir."
        },
        badgeText = "%${formatPercent(result.diseaseConfidence)}",
        badgeColor = if (result.isDiseaseReliable) {
            Color(0xFFDDF3E3)
        } else {
            Color(0xFFFFE8C7)
        },
        titleColor = Color(0xFFFF7A00)
    )

    DiseaseProbabilitiesCard(
        predictions = result.diseaseTop3
    )

    WarningCard(
        title = "Öneri",
        text = result.recommendation,
        titleColor = Color(0xFF0B6F28),
        backgroundColor = Color.White
    )

    WarningCard(
        title = "Karar Destek Uyarısı",
        text = result.warningText,
        titleColor = Color(0xFFFF7A00),
        backgroundColor = Color(0xFFFFF3D6)
    )

    Button(
        onClick = onNewAnalysisClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0B7D2B)
        )
    ) {
        Text(
            text = "Yeni Görüntü Analiz Et",
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SmallResultCard(
    number: String,
    title: String,
    value: String,
    confidence: String,
    badgeText: String?,
    badgeColor: Color,
    titleColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    color = titleColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = value,
                    color = Color(0xFF222222),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = confidence,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(badgeColor)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeText,
                        color = Color(0xFF0B6F28),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun WarningCard(
    title: String,
    text: String,
    titleColor: Color,
    backgroundColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = title,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = text,
                color = Color(0xFF303030),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}
@Composable
fun DiseaseProbabilitiesCard(
    predictions: List<PredictionItem>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = "Hastalık Sınıf Olasılıkları",
                color = Color(0xFFFF7A00),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            predictions.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = diseaseLabelToTurkish(item.label),
                        color = Color(0xFF303030),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "%${formatPercent(item.confidence)}",
                        color = Color(0xFF0B6F28),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun diseaseLabelToTurkish(label: String): String {
    return when (label) {
        "Healthy" -> "Sağlıklı"
        "Fungal_Disease" -> "Mantar Hastalığı"
        "Bacterial_Disease" -> "Bakteriyel Hastalık"
        "Viral_Disease" -> "Viral Hastalık"
        else -> label
    }
}

private fun createCameraImageUri(context: Context): Uri {
    val imageFile = File.createTempFile(
        "plant_camera_",
        ".jpg",
        context.cacheDir
    )

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }

    return if (bitmap.config == Bitmap.Config.ARGB_8888) {
        bitmap
    } else {
        bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }
}

private fun formatPercent(value: Float): String {
    return String.format(Locale.US, "%.2f", value * 100f)
}