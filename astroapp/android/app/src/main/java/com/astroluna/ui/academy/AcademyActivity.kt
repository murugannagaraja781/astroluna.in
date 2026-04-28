package com.astroluna.ui.academy

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.astroluna.data.api.ApiClient
import com.astroluna.ui.theme.CosmicAppTheme
import kotlinx.coroutines.launch
import org.json.JSONObject

class AcademyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                AcademyScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademyScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var playingVideo by remember { mutableStateOf<VideoItem?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val response = ApiClient.api.getAcademyVideos()
                if (response.isSuccessful && response.body() != null) {
                    val root = JSONObject(response.body().toString())
                    val arr = root.optJSONArray("videos")
                    if (arr != null && arr.length() > 0) {
                        val list = mutableListOf<VideoItem>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(VideoItem(
                                title = obj.optString("title", "Video"),
                                url = obj.optString("youtubeUrl", ""),
                                category = obj.optString("category", "General")
                            ))
                        }
                        videos = list
                    } else {
                        videos = getFallbackVideos()
                    }
                } else {
                    videos = getFallbackVideos()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                videos = getFallbackVideos()
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (playingVideo != null) "Now Playing" else "Astro Academy", 
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (playingVideo != null) {
                            playingVideo = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E2E))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF1E1E2E), Color(0xFF12121A))))
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFD4AF37))
            } else if (playingVideo != null) {
                VideoPlayerScreen(video = playingVideo!!, allVideos = videos, onVideoSelect = { playingVideo = it })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Explore Cosmic Wisdom",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFD4AF37),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(videos) { video ->
                        PremiumVideoCard(video = video, onClick = { playingVideo = video })
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerScreen(video: VideoItem, allVideos: List<VideoItem>, onVideoSelect: (VideoItem) -> Unit) {
    val videoId = extractYoutubeId(video.url)
    
    Column(modifier = Modifier.fillMaxSize()) {
        // YouTube Player embedded in WebView
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            if (videoId.isNotEmpty()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false // Allow autoplay
                            webChromeClient = WebChromeClient()
                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                    <style>
                                        body { margin: 0; padding: 0; background-color: black; }
                                        .video-container { position: relative; padding-bottom: 56.25%; height: 0; overflow: hidden; }
                                        .video-container iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: none; }
                                    </style>
                                </head>
                                <body>
                                    <div class="video-container">
                                        <iframe src="https://www.youtube.com/embed/$videoId?autoplay=1&modestbranding=1&rel=0" 
                                                allow="autoplay; encrypted-media; picture-in-picture" 
                                                allowfullscreen></iframe>
                                    </div>
                                </body>
                                </html>
                            """.trimIndent()
                            loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("Invalid Video URL", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }

        // Details and Up Next
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFFD4AF37).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4AF37))
                    ) {
                        Text(
                            text = video.category,
                            color = Color(0xFFD4AF37),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.LightGray
                )
            }
            
            val otherVideos = allVideos.filter { it != video }
            items(otherVideos) { otherVideo ->
                PremiumVideoCard(video = otherVideo, onClick = { onVideoSelect(otherVideo) })
            }
        }
    }
}

@Composable
fun PremiumVideoCard(video: VideoItem, onClick: () -> Unit) {
    val videoId = extractYoutubeId(video.url)
    val thumbnailUrl = if (videoId.isNotEmpty()) "https://img.youtube.com/vi/$videoId/hqdefault.jpg" else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3D)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                if (thumbnailUrl.isNotEmpty()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
                }
                
                // Play Button Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(50.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow, 
                        contentDescription = "Play", 
                        tint = Color.White, 
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = video.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD4AF37)
                )
            }
        }
    }
}

fun extractYoutubeId(url: String): String {
    val pattern = ".*(?:(?:youtu\\.be\\/|v\\/|vi\\/|u\\/\\w\\/|embed\\/|shorts\\/|live\\/)|(?:(?:watch)?\\?v(?:i)?=|\\&v(?:i)?=))([^#\\&\\?]*).*"
    val regex = Regex(pattern)
    val matchResult = regex.find(url)
    return matchResult?.groups?.get(1)?.value ?: ""
}

fun getFallbackVideos(): List<VideoItem> {
    return listOf(
        VideoItem("Astrology For Beginners", "https://www.youtube.com/watch?v=1sJvEUXte9w", "Basics"),
        VideoItem("The 12 Zodiac Signs", "https://www.youtube.com/watch?v=3VbwugaE-WQ", "Zodiac")
    )
}

data class VideoItem(val title: String, val url: String, val category: String)
