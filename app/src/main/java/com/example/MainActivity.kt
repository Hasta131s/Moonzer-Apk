package com.example

import android.app.Application
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.RatingRepository
import com.example.data.UserRating
import com.example.ui.theme.MoonBlack
import com.example.ui.theme.MoonDarkGray
import com.example.ui.theme.MoonGold
import com.example.ui.theme.MoonLightGray
import com.example.ui.theme.MoonMediumGray
import com.example.ui.theme.MoonTextDim
import com.example.ui.theme.MoonWhite
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        MoonzerApp()
      }
    }
  }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val db = Room.databaseBuilder(
    application,
    AppDatabase::class.java, "moonzer_db"
  ).fallbackToDestructiveMigration().build()

  private val repository = RatingRepository(db.ratingDao())

  // Reactive Rating Flows
  val ratings = repository.allRatings.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val averageRating = repository.averageRating.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = 0f
  )

  // Navigation State
  var currentTab by mutableStateOf("home")

  // WebView Loading and Connection Checks
  var isWebLoading by mutableStateOf(true)
  var webViewError by mutableStateOf(false)

  fun checkOnline(): Boolean {
    val connectivityManager = getApplication<Application>()
      .getSystemService(Application.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  fun insertRating(stars: Int, feedback: String) {
    viewModelScope.launch {
      repository.insert(UserRating(stars = stars, feedback = feedback))
    }
  }
}

@Composable
fun MoonzerApp(viewModel: MainViewModel = viewModel()) {
  val currentTab = viewModel.currentTab
  val ratings by viewModel.ratings.collectAsState()
  val avgRatingOpt by viewModel.averageRating.collectAsState()
  val avgRating = avgRatingOpt ?: 0f

  Scaffold(
    modifier = Modifier.fillMaxSize().background(MoonBlack),
    bottomBar = {
      NavigationBar(
        containerColor = MoonDarkGray,
        tonalElevation = 0.dp,
        modifier = Modifier
          .navigationBarsPadding()
          .height(68.dp)
          .border(width = 1.dp, color = MoonLightGray, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
      ) {
        NavigationBarItem(
          selected = currentTab == "home",
          onClick = { viewModel.currentTab = "home" },
          icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "Tab Moonzer") },
          label = { Text(stringResource(R.string.home_tab), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MoonWhite,
            selectedTextColor = MoonGold,
            indicatorColor = MoonGold.copy(alpha = 0.15f),
            unselectedIconColor = MoonTextDim,
            unselectedTextColor = MoonTextDim
          ),
          modifier = Modifier.testTag("tab_home")
        )

        NavigationBarItem(
          selected = currentTab == "rate",
          onClick = { viewModel.currentTab = "rate" },
          icon = { Icon(Icons.Filled.Star, contentDescription = "Tab Puan Ver") },
          label = { Text(stringResource(R.string.rate_tab), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MoonWhite,
            selectedTextColor = MoonGold,
            indicatorColor = MoonGold.copy(alpha = 0.15f),
            unselectedIconColor = MoonTextDim,
            unselectedTextColor = MoonTextDim
          ),
          modifier = Modifier.testTag("tab_rate")
        )

        NavigationBarItem(
          selected = currentTab == "about",
          onClick = { viewModel.currentTab = "about" },
          icon = { Icon(Icons.Filled.Info, contentDescription = "Tab Hakkında") },
          label = { Text(stringResource(R.string.about_tab), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MoonWhite,
            selectedTextColor = MoonGold,
            indicatorColor = MoonGold.copy(alpha = 0.15f),
            unselectedIconColor = MoonTextDim,
            unselectedTextColor = MoonTextDim
          ),
          modifier = Modifier.testTag("tab_about")
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MoonBlack)
    ) {
      when (currentTab) {
        "home" -> {
          // Verify online connectivity state on load/retry
          val isConnected = viewModel.checkOnline()
          if (!isConnected) {
            viewModel.webViewError = true
            viewModel.isWebLoading = false
          }

          MovieWebViewScreen(
            url = "https://moonzer.bilipbilmeden.com",
            innerPadding = innerPadding,
            viewModel = viewModel
          )
        }
        "rate" -> {
          RatingScreen(
            ratings = ratings,
            average = avgRating,
            innerPadding = innerPadding,
            onSubmit = { stars, text -> viewModel.insertRating(stars, text) }
          )
        }
        "about" -> {
          AboutScreen(innerPadding = innerPadding)
        }
      }
    }
  }
}

@Composable
fun MovieWebViewScreen(
  url: String,
  innerPadding: PaddingValues,
  viewModel: MainViewModel
) {
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }

  // Overwrite back button action to go back in web layout history
  BackHandler(enabled = webViewInstance?.canGoBack() == true) {
    webViewInstance?.goBack()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MoonBlack)
      .padding(innerPadding)
  ) {
    if (viewModel.webViewError) {
      // Elegant customized dark screen representing the offline state specified in "bu site hata verirse kapalı olsun"
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Filled.Warning,
          contentDescription = "Kapalı / Hata",
          tint = MoonGold,
          modifier = Modifier.size(72.dp)
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
          text = "Moonzer Sunucusu Kapalı",
          style = TextStyle(
            color = MoonWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(color = MoonGold.copy(alpha = 0.3f), blurRadius = 8f)
          ),
          textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
          text = "Uygulama şu anda kapalıdır veya sunucu bakım aşamasındadır.\nLütfen internet bağlantınızı kontrol edin ve daha sonra tekrar deneyiniz.",
          style = TextStyle(
            color = MoonTextDim,
            fontSize = 14.sp,
            lineHeight = 20.sp
          ),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(0.9f)
        )
        
        Spacer(modifier = Modifier.height(30.dp))
        
        Button(
          onClick = {
            viewModel.webViewError = false
            viewModel.isWebLoading = true
            if (viewModel.checkOnline()) {
              webViewInstance?.reload() ?: webViewInstance?.loadUrl(url)
            } else {
              viewModel.webViewError = true
              viewModel.isWebLoading = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = MoonGold, contentColor = Color.Black),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.testTag("retry_button")
        ) {
          Icon(Icons.Filled.Refresh, contentDescription = "Retry Connection")
          Spacer(modifier = Modifier.width(8.dp))
          Text("Tekrar Bağlan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      }
    } else {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          WebView(context).apply {
            webViewInstance = this
            layoutParams = android.view.ViewGroup.LayoutParams(
              android.view.ViewGroup.LayoutParams.MATCH_PARENT,
              android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Streaming-friendly customizations
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              databaseEnabled = true
              mediaPlaybackRequiresUserGesture = false
              useWideViewPort = true
              loadWithOverviewMode = true
              javaScriptCanOpenWindowsAutomatically = true
              builtInZoomControls = true
              displayZoomControls = false
              setSupportZoom(true)
              cacheMode = WebSettings.LOAD_DEFAULT
              userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                viewModel.isWebLoading = true
                viewModel.webViewError = false
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                viewModel.isWebLoading = false
              }

              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
              ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                  viewModel.webViewError = true
                  viewModel.isWebLoading = false
                }
              }
            }

            webChromeClient = object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress >= 90) {
                  viewModel.isWebLoading = false
                }
              }
            }

            loadUrl(url)
          }
        },
        update = { webView ->
          webViewInstance = webView
        }
      )

      AnimatedVisibility(
        visible = viewModel.isWebLoading,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MoonBlack.copy(alpha = 0.85f)),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MoonGold, strokeWidth = 3.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Moonzer Yükleniyor...", color = MoonGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
fun RatingScreen(
    ratings: List<UserRating>,
    average: Float,
    innerPadding: PaddingValues,
    onSubmit: (Int, String) -> Unit
) {
  var selectedStars by remember { mutableIntStateOf(5) }
  var feedbackText by remember { mutableStateOf("") }
  var hasSubmitted by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(innerPadding)
      .padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Header section
    item {
      Spacer(modifier = Modifier.height(20.dp))
      Text(
        text = " Moonzer'i Puanla",
        style = TextStyle(
          color = MoonWhite,
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.SansSerif,
          shadow = Shadow(color = MoonGold.copy(alpha = 0.25f), blurRadius = 10f)
        )
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Fikirleriniz bizim için çok değerli. Uygulamamıza yıldız vererek kayrasql'a destek olabilirsiniz.",
        style = TextStyle(color = MoonTextDim, fontSize = 13.sp, lineHeight = 18.sp),
        modifier = Modifier.padding(horizontal = 4.dp)
      )
    }

    // Stats indicator
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MoonDarkGray),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, MoonLightGray),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = String.format(Locale.US, "%.1f", average),
              style = TextStyle(color = MoonGold, fontSize = 36.sp, fontWeight = FontWeight.Black)
            )
            Text(text = "Ortalama Skoru", color = MoonTextDim, fontSize = 11.sp)
          }
          
          Spacer(modifier = Modifier.width(24.dp))
          
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              repeat(5) { index ->
                Icon(
                  imageVector = Icons.Filled.Star,
                  contentDescription = null,
                  tint = if (index < average.toInt()) MoonGold else MoonGold.copy(alpha = 0.25f),
                  modifier = Modifier.size(16.dp)
                )
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "${ratings.size} toplam yerel oylama",
              color = MoonWhite,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }
    }

    // Input form
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MoonDarkGray),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          if (!hasSubmitted) {
            Text(
              text = "Puanınız ve Görüşleriniz",
              color = MoonWhite,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Rating Star Selector
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center
            ) {
              repeat(5) { index ->
                val starValue = index + 1
                Icon(
                  imageVector = Icons.Filled.Star,
                  contentDescription = "Yıldız $starValue",
                  tint = if (starValue <= selectedStars) MoonGold else MoonGold.copy(alpha = 0.25f),
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { selectedStars = starValue }
                    .padding(4.dp)
                    .testTag("star_$starValue")
                )
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
              value = feedbackText,
              onValueChange = { feedbackText = it },
              placeholder = { Text("Moonzer hakkındaki düşünceleriniz...", color = MoonTextDim, fontSize = 14.sp) },
              modifier = Modifier.fillMaxWidth().testTag("feedback_input"),
              textStyle = TextStyle(color = MoonWhite, fontSize = 14.sp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MoonWhite,
                unfocusedTextColor = MoonWhite,
                focusedContainerColor = MoonMediumGray,
                unfocusedContainerColor = MoonMediumGray,
                focusedBorderColor = MoonGold,
                unfocusedBorderColor = MoonLightGray
              ),
              shape = RoundedCornerShape(12.dp),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
              maxLines = 4,
              minLines = 2
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
              onClick = {
                onSubmit(selectedStars, feedbackText)
                hasSubmitted = true
              },
              colors = ButtonDefaults.buttonColors(containerColor = MoonGold, contentColor = Color.Black),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("submit_button")
            ) {
              Text("Değerlendirmeyi Gönder", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
          } else {
            Column(
              modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(Icons.Filled.Star, contentDescription = "Teşekkürler", tint = MoonGold, modifier = Modifier.size(42.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text("Değerlendirmeniz Alındı!", color = MoonWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text("Geri bildiriminiz başarıyla kaydedildi.", color = MoonTextDim, fontSize = 13.sp)
              Spacer(modifier = Modifier.height(14.dp))
              Text(
                text = "Yeni Puan Ver",
                color = MoonGold,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                  .clickable {
                    hasSubmitted = false
                    feedbackText = ""
                  }
                  .padding(8.dp)
              )
            }
          }
        }
      }
    }

    // Historical ratings lists
    if (ratings.isNotEmpty()) {
      item {
        Text(
          text = "Son Yapılan Yorumlar",
          color = MoonWhite,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
      }

      items(ratings) { rating ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MoonDarkGray.copy(alpha = 0.5f)),
          shape = RoundedCornerShape(12.dp),
          border = androidx.compose.foundation.BorderStroke(0.6.dp, MoonLightGray),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row {
                repeat(5) { idx ->
                  Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (idx < rating.stars) MoonGold else MoonGold.copy(alpha = 0.25f),
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
              val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
              Text(
                text = df.format(Date(rating.timestamp)),
                color = MoonTextDim,
                fontSize = 11.sp
              )
            }
            if (rating.feedback.isNotBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = rating.feedback,
                color = MoonWhite,
                fontSize = 13.sp,
                lineHeight = 18.sp
              )
            }
          }
        }
      }
    } else {
      item {
        Box(
          modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
          contentAlignment = Alignment.Center
        ) {
          Text("Henüz bir puan verilmedi. İlk puanı siz verin!", color = MoonTextDim, fontSize = 12.sp)
        }
      }
    }
    
    item {
      Spacer(modifier = Modifier.height(30.dp))
    }
  }
}

@Composable
fun AboutScreen(innerPadding: PaddingValues) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(innerPadding)
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Top
  ) {
    Spacer(modifier = Modifier.height(32.dp))

    // Sleek premium upper tracking tag
    Text(
      text = "ANDROID PREMIUM SYSTEM",
      style = TextStyle(
        color = MoonGold,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 4.sp,
        textAlign = TextAlign.Center
      )
    )

    Spacer(modifier = Modifier.height(18.dp))

    // Craft custom glowing Crescent Moon & Owl Eyes graphics instead of unstyled icons
    Box(
      modifier = Modifier
        .size(150.dp)
        .background(MoonDarkGray, CircleShape)
        .border(1.dp, MoonGold.copy(alpha = 0.25f), CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.size(100.dp)) {
        // Draw elegant crescent moon using updated Royal Indigo color
        drawArc(
          color = MoonGold,
          startAngle = -45f,
          sweepAngle = 180f,
          useCenter = false,
          style = Stroke(width = 8f),
          topLeft = Offset(10f, 10f),
          size = size * 0.8f
        )
        
        // Draw stylized owl face circles (eyes)
        drawCircle(
          color = MoonWhite,
          radius = 11f,
          center = Offset(size.width * 0.4f, size.height * 0.55f),
          style = Stroke(width = 3.5f)
        )
        drawCircle(
          color = MoonWhite,
          radius = 11f,
          center = Offset(size.width * 0.65f, size.height * 0.55f),
          style = Stroke(width = 3.5f)
        )
        // Pupils
        drawCircle(
          color = MoonGold,
          radius = 4.5f,
          center = Offset(size.width * 0.4f, size.height * 0.55f)
        )
        drawCircle(
          color = MoonGold,
          radius = 4.5f,
          center = Offset(size.width * 0.65f, size.height * 0.55f)
        )
        
        // Soft beak triangle
        drawLine(
          color = MoonGold,
          start = Offset(size.width * 0.525f, size.height * 0.61f),
          end = Offset(size.width * 0.525f, size.height * 0.68f),
          strokeWidth = 3.5f
        )
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
      text = "Moonzer",
      style = TextStyle(
        color = MoonWhite,
        fontSize = 34.sp,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.SansSerif,
        textAlign = TextAlign.Center,
        shadow = Shadow(color = MoonGold.copy(alpha = 0.35f), blurRadius = 14f)
      )
    )

    Text(
      text = "Film & Dizi İzleme Uygulaması",
      color = MoonGold,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      letterSpacing = 1.5.sp,
      modifier = Modifier.padding(top = 2.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Yapımcı & Versiyon metadata
    Card(
      colors = CardDefaults.cardColors(containerColor = MoonDarkGray),
      shape = RoundedCornerShape(24.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, MoonLightGray),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Yapımcı", color = MoonTextDim, fontSize = 13.sp)
          Text(
            text = "kayrasql",
            color = MoonGold,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("app_creator")
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Uygulama Versiyonu", color = MoonTextDim, fontSize = 13.sp)
          Text(
            text = "1.0 (Versiyon 1)",
            color = MoonWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("app_version")
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Tema", color = MoonTextDim, fontSize = 13.sp)
          Text("Siyah Premium Indigo OLED", color = MoonWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Altyapı", color = MoonTextDim, fontSize = 13.sp)
          Text("moonzer.bilipbilmeden.com", color = MoonWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        // Divider
        Spacer(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MoonLightGray)
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "APK_ID: MOON_01_PRO",
            color = MoonTextDim.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
          
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier
                .size(6.dp)
                .background(Color(0xFF22C55E), CircleShape)
            )
            Text(
              text = "SİSTEM ÇEVRİMİÇİ",
              color = Color(0xFF22C55E),
              fontSize = 10.sp,
              fontWeight = FontWeight.Black
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Card(
      colors = CardDefaults.cardColors(containerColor = MoonDarkGray.copy(alpha = 0.4f)),
      shape = RoundedCornerShape(16.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, MoonLightGray.copy(alpha = 0.5f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(
        text = "Yepyeni bir film ve dizi izleme deneyimi sunan Moonzer, en sevdiğiniz film ve dizi serilerine tek bir dokunuşla ulaşmanızı sağlar. Siyah temalı şık arayüzü göz sağlığınızı korurken kesintisiz bir deneyim sunmaktadır.",
        color = MoonTextDim,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(14.dp)
      )
    }
  }
}
