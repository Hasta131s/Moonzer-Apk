package com.example

import android.app.Application
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

// Unique branding color accent for Netflix experience style (Moonzer Premium Warm Rust)
val MoonRed = Color(0xFFAE5941)
val DarkBackground = Color(0xFF181818)
val SurfaceCard = Color(0xFF242424)
val BorderColor = Color(0x22FFFFFF)
val TextDim = Color(0xFFB5B5B5)
val GoldColor = Color(0xFFFFD700)

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = androidx.lifecycle.ViewModelProvider(this).get(MainViewModel::class.java)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MoonzerTvNavigationHost(viewModel)
            }
        }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val db = androidx.room.Room.databaseBuilder(
        application,
        AppDatabase::class.java, "moonzer_db"
    ).fallbackToDestructiveMigration().build()

    private val ratingRepository = RatingRepository(db.ratingDao())
    private val userDao = db.userDao()
    private val movieDao = db.movieDao()
    private val watchDao = db.watchDao()
    private val searchDao = db.searchDao()
    private val sessionDao = db.sessionDao()

    // Authentication States
    var currentUser = mutableStateOf<User?>(null)

    // General app navigation
    var currentScreen by mutableStateOf("splash") // splash, login, register, main, detail, admin, legal
    var activeCategoryFilter by mutableStateOf("all")

    // Video Player Streaming States
    var currentStreamingMovie = mutableStateOf<Movie?>(null)
    var isStreamingModeActive by mutableStateOf(false)

    // Current selected movie in details view
    var activeDetailMovie = mutableStateOf<Movie?>(null)

    // Remote OMDB Search state
    var searchQuery by mutableStateOf("")
    var isSearchLoading by mutableStateOf(false)
    var searchResults = mutableStateListOf<Movie>()

    // Local Watch History & Search History flows
    val watchHistoryList = MutableStateFlow<List<Movie>>(emptyList())
    val watchlistList = MutableStateFlow<List<Movie>>(emptyList())
    val hiddenList = MutableStateFlow<List<String>>(emptyList())
    val searchHistoryList = MutableStateFlow<List<SearchHistory>>(emptyList())

    // All Local Movies stored in Room
    val localMovieList = movieDao.getAllMovies().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recommendedMovies = combine(localMovieList, watchHistoryList, hiddenList) { movies, history, hidden ->
        val visibleMovies = movies.filter { movie ->
            movie.imdbID !in hidden && 
            movie.poster.isNotEmpty() && 
            movie.poster != "N/A" && 
            !movie.poster.contains("placeholder") && 
            !movie.poster.contains("unsplash.com")
        }
        
        if (history.isEmpty()) {
            visibleMovies.filter { it.categories.contains("popular") }.shuffled().take(10)
        } else {
            val watchedCategoryCounts = mutableMapOf<String, Int>()
            for (watchedMovie in history) {
                val cats = watchedMovie.categories.split(",").map { it.trim().lowercase() }
                for (cat in cats) {
                    if (cat != "movies" && cat != "series" && cat != "new") {
                        watchedCategoryCounts[cat] = (watchedCategoryCounts[cat] ?: 0) + 1
                    }
                }
            }
            
            val favoriteCategories = watchedCategoryCounts.entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(3)
                
            if (favoriteCategories.isEmpty()) {
                visibleMovies.shuffled().take(10)
            } else {
                val watchedImdbIds = history.map { it.imdbID }.toSet()
                val targetMovies = visibleMovies.filter { movie ->
                    movie.imdbID !in watchedImdbIds && 
                    movie.categories.split(",").map { it.trim().lowercase() }.any { it in favoriteCategories }
                }
                
                if (targetMovies.size >= 4) {
                    targetMovies.sortedByDescending { m ->
                        m.categories.split(",").map { it.trim().lowercase() }.count { it in favoriteCategories }
                    }.take(10)
                } else {
                    (targetMovies + visibleMovies.filter { it.categories.split(",").map { it.trim().lowercase() }.any { it in favoriteCategories } })
                        .distinctBy { it.imdbID }
                        .take(10)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // System Telemetry for Admin Panel
    val allDbSessions = sessionDao.getAllSessions().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allDbUsers = userDao.getAllUsers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val ratingFlow = ratingRepository.allRatings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val averageRating = ratingRepository.averageRating.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0f
    )

    init {
        prepopulateMovies()
        observeAppHistory()
    }

    private fun observeAppHistory() {
        viewModelScope.launch {
            // Keep watch history up to date for currentUser
            snapshotFlow { currentUser.value }.collectLatest { user ->
                if (user != null) {
                    watchDao.getWatchHistoryForUser(user.id).collectLatest { historyList ->
                        val list = mutableListOf<Movie>()
                        for (history in historyList) {
                            movieDao.getMovieByImdbId(history.imdbID)?.let { list.add(it) }
                        }
                        watchHistoryList.value = list
                    }
                } else {
                    watchHistoryList.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            // Keep watchlist up to date for currentUser
            snapshotFlow { currentUser.value }.collectLatest { user ->
                if (user != null) {
                    db.watchlistDao().getWatchlistForUser(user.id).collectLatest { wl ->
                        val list = mutableListOf<Movie>()
                        for (w in wl) {
                            movieDao.getMovieByImdbId(w.imdbID)?.let { list.add(it) }
                        }
                        watchlistList.value = list
                    }
                } else {
                    watchlistList.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            // Keep hidden movies up to date for currentUser
            snapshotFlow { currentUser.value }.collectLatest { user ->
                if (user != null) {
                    db.hiddenMovieDao().getHiddenMoviesForUser(user.id).collectLatest { hl ->
                        hiddenList.value = hl.map { it.imdbID }
                    }
                } else {
                    hiddenList.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            // Keep search history up to date for currentUser
            snapshotFlow { currentUser.value }.collectLatest { user ->
                if (user != null) {
                    searchDao.getSearchHistoryForUser(user.id).collectLatest { searchHistory ->
                        searchHistoryList.value = searchHistory
                    }
                } else {
                    searchHistoryList.value = emptyList()
                }
            }
        }
    }

    fun toggleWatchlist(imdbID: String) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            val exists = db.watchlistDao().isInWatchlist(user.id, imdbID)
            if (exists) {
                db.watchlistDao().deleteWatchlistEntry(user.id, imdbID)
            } else {
                db.watchlistDao().insertWatchlist(WatchlistMovie(userId = user.id, imdbID = imdbID))
            }
        }
    }

    fun isMovieInWatchlistFlow(imdbID: String): Flow<Boolean> {
        return watchlistList.map { list -> list.any { it.imdbID == imdbID } }
    }

    fun hideMovie(imdbID: String) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            db.hiddenMovieDao().insertHiddenMovie(HiddenMovie(userId = user.id, imdbID = imdbID))
        }
    }

    private fun prepopulateMovies() {
        viewModelScope.launch(Dispatchers.IO) {
            // Insert premium seed movies listed in PHP config if not already loaded in DB
            val seeds = listOf(
                Movie(
                    imdbID = "tt0111161",
                    title = "Esaretin Bedeli",
                    year = "1994",
                    type = "movie",
                    categories = "popular,movies,drama",
                    views = 15420,
                    plot = "Genç ve başarılı bir banker olan Andy Dufresne, karısını ve onun sevgilisini öldürmek suçundan ömür boyu hapis cezasına çarptırılır ve Shawshank hapishanesine gönderilir.",
                    rating = "9.3",
                    poster = "https://m.media-amazon.com/images/M/MV5BMDAyYmQxYjItNDkwYS00MDMyLWE3YmMtMDYyODNjMzMxYTYiXkEyXkFqcGc@._V1_SX300.jpg"
                ),
                Movie(
                    imdbID = "tt0468569",
                    title = "Kara Şövalye",
                    year = "2008",
                    type = "movie",
                    categories = "popular,movies,action",
                    views = 13200,
                    plot = "Batman, teğmen Jim Gordon ve bölge savcısı Harvey Dent'in yardımıyla Gotham City'deki organize suç örgütlerini çökertmeye başlar ancak Joker adında yeni bir suç dehası ortaya çıkar.",
                    rating = "9.0",
                    poster = "https://m.media-amazon.com/images/M/MV5BMTMxNTMwODM0NF5BMl5BanBnXkFtZTcwODAyMTk2Mw@@._V1_SX300.jpg"
                ),
                Movie(
                    imdbID = "tt1375666",
                    title = "Başlangıç",
                    year = "2010",
                    type = "movie",
                    categories = "popular,movies,scifi",
                    views = 11000,
                    plot = "Çok yetenekli bir hırsız olan Dom Cobb, uzmanlık alanı, zihnin en savunmasız olduğu rüya görme anında bilinçaltının derinliklerindeki değerli sırları çalmaktır.",
                    rating = "8.8",
                    poster = "https://m.media-amazon.com/images/M/MV5BMjAxMzY3NjcxNF5BMl5BanBnXkFtZTcwNTI5OTM0Mw@@._V1_SX300.jpg"
                ),
                Movie(
                    imdbID = "tt0816692",
                    title = "Yıldızlararası",
                    year = "2014",
                    type = "movie",
                    categories = "popular,movies,scifi,drama",
                    views = 11500,
                    plot = "Eski bir NASA pilotu olan Cooper liderliğindeki astronot ekibi, insanlığın hayatta kalmasını sağlayacak yeni bir solucan deliği geçidini keşfetmek için uzay yolculuğuna fırlar.",
                    rating = "8.7",
                    poster = "https://m.media-amazon.com/images/M/MV5BYzdjMDAxODAtODA2My00NTg2LTgwMzEtODY4YmZhYTRkMWU2XkEyXkFqcGc@._V1_SX300.jpg"
                ),
                Movie(
                    imdbID = "tt15398776",
                    title = "Oppenheimer",
                    year = "2023",
                    type = "movie",
                    categories = "new,movies,drama",
                    views = 12000,
                    plot = "J. Robert Oppenheimer'ın nükleer silahları geliştiren Manhattan Projesindeki rolünü anlatan tarihi bir dram biyografisi.",
                    rating = "8.4",
                    poster = "https://m.media-amazon.com/images/M/MV5BMDBmYTZjNjUtN2M1MS00MTQ2LTk2ODgtNzc2M2QyZGE5NTVjXkEyXkFqcGc@._V1_SX300.jpg"
                ),
                Movie(
                    imdbID = "tt0903747",
                    title = "Breaking Bad",
                    year = "2008",
                    type = "series",
                    categories = "new,series,drama,thriller",
                    views = 20000,
                    plot = "Kimya öğretmeni Walter White, akciğer kanseri teşhisi aldıktan sonra ailesinin finansal geleceğini garanti altına almak için eski bir öğrencisi Jesse ile uyuşturucu üretimine başlar.",
                    rating = "9.5",
                    poster = "https://m.media-amazon.com/images/M/MV5BMjhiMzgxZTctNDc1Ni00OTUxLTkwMTUtMTMyMTk3OWYyNTliXkEyXkFqcGc@._V1_SX300.jpg"
                ),
                Movie(
                    imdbID = "tt4574334",
                    title = "Stranger Things",
                    year = "2016",
                    type = "series",
                    categories = "series,new,scifi",
                    views = 18500,
                    plot = "Küçük bir kasabada bir çocuk kaybolduğunda, gizli askeri deneyler, doğaüstü güçler ve gizemli bir kızın yer aldığı karanlık sırlar açığa çıkar.",
                    rating = "8.7",
                    poster = "https://m.media-amazon.com/images/M/MV5BM2M1MTg1NzMtYzEyMC00MDZmLTkwMDMtODUzMzQ4ZDEyOTI5XkEyXkFqcGc@._V1_SX300.jpg"
                ),
                Movie(
                    imdbID = "tt0944947",
                    title = "Game of Thrones",
                    year = "2011",
                    type = "series",
                    categories = "series,popular,drama",
                    views = 22000,
                    plot = "Dokuz soylu aile, Westeros topraklarının kontrolünü ele geçirmek için çatışırken, kadim düşmanlar binlerce yıldır uykudan uyanıp geri döner.",
                    rating = "9.2",
                    poster = "https://m.media-amazon.com/images/M/MV5BMjE3NTg5NDU0Ml5BMl5BanBnXkFtZTgwNzY2OTQ0MjE@._V1_SX300.jpg"
                ),
                Movie(
                    imdbID = "tt7000160",
                    title = "Diriliş: Ertuğrul",
                    year = "2014",
                    type = "series",
                    categories = "series,turkish,popular",
                    views = 18000,
                    plot = "Kayı obası beyi Süleyman Şah oğlu Ertuğrul Gazi'nin Osmanlı İmparatorluğu'nun temellerini atma mücadelesini konu alan tarihi yapım.",
                    rating = "7.9",
                    poster = "https://m.media-amazon.com/images/M/MV5BMGNjZGFkZDktYTg4OC00YzE2LWFmMDctMTdkNTEwYjFjOWUzXkEyXkFqcGc@._V1_SX300.jpg"
                )
            )

            for (seed in seeds) {
                if (movieDao.getMovieByImdbId(seed.imdbID) == null) {
                    movieDao.insertMovie(seed)
                }
            }

            // Let's create primary Admin in Local User Database if not exists
            if (userDao.getUserByUsername("kayra@gmail.com") == null) {
                userDao.insertUser(
                    User(
                        username = "kayra@gmail.com",
                        email = "kayra@gmail.com",
                        passwordHash = "Kayra31",
                        role = "admin",
                        avatarUrl = "https://i.hizliresim.com/smgvufn.png"
                    )
                )
            }
        }
    }

    // Interactive Remote Search Mechanism using OMDb API
    fun triggerSearch(query: String) {
        if (query.trim().isEmpty()) {
            searchResults.clear()
            return
        }
        searchQuery = query
        isSearchLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            // Save search query into database for current user history logger
            currentUser.value?.id?.let { uid ->
                searchDao.insertSearchHistory(SearchHistory(userId = uid, query = query.trim()))
            }

            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val urlObj = URL("https://www.omdbapi.com/?apikey=4f15090e&s=$encodedQuery")
                val connection = urlObj.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val textBuilder = StringBuilder()
                    var line: String? = reader.readLine()
                    while (line != null) {
                        textBuilder.append(line)
                        line = reader.readLine()
                    }
                    reader.close()

                    val json = JSONObject(textBuilder.toString())
                    if (json.optString("Response") == "True") {
                        val array = json.optJSONArray("Search")
                        val list = mutableListOf<Movie>()
                        if (array != null) {
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                val imdbID = obj.optString("imdbID")
                                val title = obj.optString("Title")
                                val year = obj.optString("Year")
                                val type = obj.optString("Type")
                                val poster = obj.optString("Poster")

                                if (poster != null && poster != "N/A" && poster.isNotEmpty()) {
                                    val movie = Movie(
                                        imdbID = imdbID,
                                        title = title,
                                        year = year,
                                        type = type,
                                        categories = "new,action", // default fallback
                                        poster = poster
                                    )
                                    list.add(movie)
                                    // Save dynamically to Local Database cache so details work instantly!
                                    if (movieDao.getMovieByImdbId(imdbID) == null) {
                                        movieDao.insertMovie(movie)
                                    }
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            searchResults.clear()
                            searchResults.addAll(list)
                            isSearchLoading = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            searchResults.clear()
                            isSearchLoading = false
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isSearchLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSearchLoading = false
                }
            }
        }
    }

    fun loadFullMovieDetails(imdbID: String, onLoaded: (Movie) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // First check local DB
            var movie = movieDao.getMovieByImdbId(imdbID)
            if (movie != null && movie.plot.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    activeDetailMovie.value = movie
                    onLoaded(movie)
                }
                return@launch
            }

            // Fetch from OMDB if local does not have full plot
            try {
                val urlObj = URL("https://www.omdbapi.com/?apikey=4f15090e&i=$imdbID&plot=full")
                val connection = urlObj.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val textBuilder = StringBuilder()
                var line: String? = reader.readLine()
                while (line != null) {
                    textBuilder.append(line)
                    line = reader.readLine()
                }
                reader.close()

                val json = JSONObject(textBuilder.toString())
                if (json.optString("Response") == "True") {
                    var title = json.optString("Title")
                    val year = json.optString("Year")
                    val type = json.optString("Type")
                    val poster = json.optString("Poster")
                    var plot = json.optString("Plot")
                    val rating = json.optString("imdbRating")

                    // Simple translation to Turkish using direct translations of words if possible
                    // Or keep original. Let's do a short localized translation check
                    if (title.contains("The Shawshank Redemption")) title = "Esaretin Bedeli"

                    val updatedMovie = Movie(
                        imdbID = imdbID,
                        title = title,
                        year = year,
                        type = type,
                        categories = "popular,movies",
                        plot = plot ?: "Film / Dizi detayları bilgisi alınamadı.",
                        rating = rating ?: "N/A",
                        poster = poster ?: ""
                    )
                    movieDao.insertMovie(updatedMovie)
                    withContext(Dispatchers.Main) {
                        activeDetailMovie.value = updatedMovie
                        onLoaded(updatedMovie)
                    }
                }
            } catch (e: Exception) {
                // Return local movie fallback if connection fails
                withContext(Dispatchers.Main) {
                    if (movie != null) {
                        activeDetailMovie.value = movie
                        onLoaded(movie)
                    }
                }
            }
        }
    }

    // Register Locally
    fun registerNewUser(uname: String, email: String, pword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmedName = uname.trim()
            val trimmedEmail = email.trim()
            if (trimmedName.length < 3) {
                withContext(Dispatchers.Main) { onError("Kullanıcı adı en az 3 karakter olmalıdır.") }
                return@launch
            }
            if (trimmedName.contains(" ")) {
                withContext(Dispatchers.Main) { onError("Kullanıcı adı boşluk içeremez.") }
                return@launch
            }
            if (userDao.getUserByUsername(trimmedName) != null) {
                withContext(Dispatchers.Main) { onError("Bu kullanıcı adı zaten alınmış.") }
                return@launch
            }

            val newUser = User(username = trimmedName, email = trimmedEmail, passwordHash = pword)
            val insertedId = userDao.insertUser(newUser)
            val loggedInUser = newUser.copy(id = insertedId.toInt())

            withContext(Dispatchers.Main) {
                currentUser.value = loggedInUser
                createSessionForUser(loggedInUser)
                onSuccess()
            }
        }
    }

    // Login Locally
    fun loginExistingUser(uname: String, pword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = userDao.getUserByUsername(uname.trim())
            if (user == null || user.passwordHash != pword) {
                withContext(Dispatchers.Main) { onError("Kullanıcı adı veya şifre hatalı.") }
                return@launch
            }
            withContext(Dispatchers.Main) {
                currentUser.value = user
                createSessionForUser(user)
                onSuccess()
            }
        }
    }

    private fun createSessionForUser(user: User) {
        viewModelScope.launch(Dispatchers.IO) {
            val device = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
            val randomIp = "192.168.1." + Random.nextInt(10, 254)
            sessionDao.insertSession(
                Session(
                    userId = user.id,
                    username = user.username,
                    deviceName = device,
                    ipAddress = randomIp
                )
            )
        }
    }

    // Track recently watched content inside watch Dao
    fun addToWatchHistory(imdbID: String) {
        viewModelScope.launch(Dispatchers.IO) {
            currentUser.value?.id?.let { uid ->
                watchDao.insertWatchHistory(
                    WatchHistory(
                        userId = uid,
                        imdbID = imdbID
                    )
                )
                movieDao.incrementViews(imdbID)

                // Dynamic category assignment on watch
                movieDao.getMovieByImdbId(imdbID)?.let { movie ->
                    val normTitle = movie.title.lowercase()
                    val addedCats = mutableListOf<String>()
                    if (normTitle.contains("ivedik") || normTitle.contains("komedi") || normTitle.contains("şaban") || normTitle.contains("recep")) {
                        addedCats.add("komedi")
                        addedCats.add("yerli")
                    }
                    if (normTitle.contains("dram") || normTitle.contains("drama") || normTitle.contains("bedeli")) {
                        addedCats.add("drama")
                    }
                    if (normTitle.contains("diriliş") || normTitle.contains("osman") || normTitle.contains("türk") || normTitle.contains("yerli")) {
                        addedCats.add("yerli")
                        addedCats.add("action")
                    }
                    
                    if (addedCats.isNotEmpty()) {
                        val currentCats = movie.categories.split(",").map { it.trim().lowercase() }.toMutableSet()
                        var modified = false
                        for (cat in addedCats) {
                            if (!currentCats.contains(cat)) {
                                currentCats.add(cat)
                                modified = true
                            }
                        }
                        if (modified) {
                            val updatedMovie = movie.copy(categories = currentCats.joinToString(","))
                            movieDao.insertMovie(updatedMovie)
                        }
                    }
                }
            }
        }
    }

    fun removeMovieAdmin(imdbID: String) {
        viewModelScope.launch(Dispatchers.IO) {
            movieDao.deleteMovie(imdbID)
        }
    }

    fun addRatingNative(stars: Int, feedback: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val username = currentUser.value?.username ?: "Anonim Üye"
            ratingRepository.insert(UserRating(userName = username, stars = stars, feedback = feedback))
        }
    }

    fun addCustomMovieAdmin(imdbID: String, title: String, year: String, type: String, categories: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val defaultPoster = "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?q=80&w=300&auto=format&fit=crop"
            movieDao.insertMovie(
                Movie(
                    imdbID = imdbID,
                    title = title,
                    year = year,
                    type = type,
                    categories = categories,
                    views = 0,
                    plot = "Yönetici tarafından eklenen özel içerik.",
                    rating = "8.0",
                    poster = defaultPoster
                )
            )
        }
    }

    fun checkOnline(): Boolean {
        val connectivityManager = getApplication<Application>()
            .getSystemService(Application.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            currentUser.value?.id?.let { uid ->
                searchDao.clearSearchHistory(uid)
            }
        }
    }
}

// -------------------------------------------------------------
// MAIN APP COMPOSABLE PORTALS
// -------------------------------------------------------------

@Composable
fun MoonzerTvNavigationHost(viewModel: MainViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (viewModel.currentScreen) {
            "splash" -> SplashScreen {
                if (viewModel.currentUser.value != null) {
                    viewModel.currentScreen = "main"
                } else {
                    viewModel.currentScreen = "login"
                }
            }
            "login" -> LoginScreen(
                viewModel = viewModel,
                onNavigateToRegister = { viewModel.currentScreen = "register" },
                onNavigateToMain = { viewModel.currentScreen = "main" }
            )
            "register" -> RegisterScreen(
                viewModel = viewModel,
                onNavigateToLogin = { viewModel.currentScreen = "login" },
                onNavigateToMain = { viewModel.currentScreen = "main" }
            )
            "main" -> MainAppScaffold(viewModel)
            "admin" -> AdminPanelScreen(viewModel) { viewModel.currentScreen = "main" }
            "legal" -> LegalAboutScreen { viewModel.currentScreen = "main" }
        }

        // Render full screen streaming video player using PlayIMDb dynamically!
        if (viewModel.isStreamingModeActive && viewModel.currentStreamingMovie.value != null) {
            VideoStreamingScreen(
                movie = viewModel.currentStreamingMovie.value!!,
                onDismiss = {
                    viewModel.isStreamingModeActive = false
                    viewModel.currentStreamingMovie.value = null
                }
            )
        }
    }
}

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2200)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = android.R.drawable.ic_menu_slideshow), // default fallback
                contentDescription = null,
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, MoonRed, RoundedCornerShape(12.dp))
                    .testTag("app_logo")
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "MOONZER TV",
                fontSize = 32.sp,
                color = MoonRed,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Premium Sinema & Dizi Deneyimi",
                fontSize = 13.sp,
                color = TextDim,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(color = MoonRed, strokeWidth = 3.dp)
        }
    }
}

@Composable
fun LoginScreen(viewModel: MainViewModel, onNavigateToRegister: () -> Unit, onNavigateToMain: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Moonzer Tv",
                color = MoonRed,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "Giriş yaparak kaldığınız yerden devam edin",
                color = TextDim,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (errorText.isNotEmpty()) {
                Surface(
                    color = MoonRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MoonRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorText,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Kullanıcı Adı") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_username"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoonRed,
                    focusedLabelColor = MoonRed,
                    cursorColor = MoonRed
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifre") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoonRed,
                    focusedLabelColor = MoonRed,
                    cursorColor = MoonRed
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        errorText = "Lütfen tüm alanları doldurun."
                        return@Button
                    }
                    isLoading = true
                    errorText = ""
                    viewModel.loginExistingUser(username, password, {
                        isLoading = false
                        onNavigateToMain()
                    }, { err ->
                        isLoading = false
                        errorText = err
                    })
                },
                colors = ButtonDefaults.buttonColors(containerColor = MoonRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("login_btn"),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Oturum Aç", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onNavigateToRegister) {
                Text(
                    text = "Henüz üye değil misiniz? Şimdi ücretsiz Kaydolun.",
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
fun RegisterScreen(viewModel: MainViewModel, onNavigateToLogin: () -> Unit, onNavigateToMain: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Kayıt Ol",
                color = MoonRed,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "Moonzer TV dünyasına katılarak izlemeye başlayın",
                color = TextDim,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (errorText.isNotEmpty()) {
                Surface(
                    color = MoonRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MoonRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorText,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Kullanıcı Adı") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_username"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoonRed,
                    focusedLabelColor = MoonRed,
                    cursorColor = MoonRed
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-posta Adresi") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_email"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoonRed,
                    focusedLabelColor = MoonRed,
                    cursorColor = MoonRed
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifre") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_password"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoonRed,
                    focusedLabelColor = MoonRed,
                    cursorColor = MoonRed
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (username.isBlank() || email.isBlank() || password.isBlank()) {
                        errorText = "Lütfen tüm alanları doldurun."
                        return@Button
                    }
                    isLoading = true
                    errorText = ""
                    viewModel.registerNewUser(username, email, password, {
                        isLoading = false
                        onNavigateToMain()
                    }, { err ->
                        isLoading = false
                        errorText = err
                    })
                },
                colors = ButtonDefaults.buttonColors(containerColor = MoonRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("register_btn"),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Kaydol", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onNavigateToLogin) {
                Text(
                    text = "Zaten bir hesabınız var mı? Buradan Giriş Yapın.",
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

// -------------------------------------------------------------
// NATIVE HOUSING & DRAWER SCAFFOLD
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: MainViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var currentTabSelector by remember { mutableStateOf("movies") } // "movies", "search", "history", "rating", "watchlist"

    val localMovies by viewModel.localMovieList.collectAsState()
    val history by viewModel.watchHistoryList.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkBackground,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        Text(
                            text = "Moonzer Tv",
                            color = MoonRed,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Filled.Tv,
                            contentDescription = null,
                            tint = MoonRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "KATEGORİLER",
                        fontSize = 12.sp,
                        color = TextDim,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val categories = listOf(
                        "Tümü" to "all",
                        "Popüler filmler" to "popular",
                        "Yeni Yapımlar" to "new",
                        "Yerli Diziler / Yapımlar" to "turkish",
                        "Bilim Kurgu" to "scifi",
                        "Drama" to "drama",
                        "Aksiyon" to "action"
                    )

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(categories) { pair ->
                            NavigationDrawerItem(
                                label = { Text(pair.first, color = Color.White) },
                                selected = viewModel.activeCategoryFilter == pair.second,
                                onClick = {
                                    viewModel.activeCategoryFilter = pair.second
                                    scope.launch { drawerState.close() }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MoonRed.copy(alpha = 0.2f),
                                    unselectedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.padding(vertical = 4.dp).testTag("cat_${pair.second}")
                            )
                        }
                    }

                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "HESABIM (" + (viewModel.currentUser.value?.username ?: "Üye") + ")",
                        fontSize = 11.sp,
                        color = TextDim,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (viewModel.currentUser.value?.role == "admin") {
                        NavigationDrawerItem(
                            label = { Text("Yönetim Paneli", color = GoldColor) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                viewModel.currentScreen = "admin"
                            },
                            icon = { Icon(Icons.Filled.Shield, contentDescription = null, tint = GoldColor) },
                            modifier = Modifier.padding(vertical = 4.dp).testTag("admin_panel_button")
                        )
                    }

                    NavigationDrawerItem(
                        label = { Text("İzleme Listem", color = Color.White) },
                        selected = currentTabSelector == "watchlist",
                        onClick = {
                            currentTabSelector = "watchlist"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.Bookmark, contentDescription = null, tint = MoonRed) },
                        modifier = Modifier.padding(vertical = 4.dp).testTag("sidebar_watchlist_button")
                    )

                    NavigationDrawerItem(
                        label = { Text("Puan Ver & Geri Bildirim", color = Color.White) },
                        selected = currentTabSelector == "rating",
                        onClick = {
                            currentTabSelector = "rating"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.Star, contentDescription = null, tint = MoonRed) },
                        modifier = Modifier.padding(vertical = 4.dp).testTag("sidebar_rating_button")
                    )

                    NavigationDrawerItem(
                        label = { Text("Uygulama Sözleşmeleri", color = Color.White) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.currentScreen = "legal"
                        },
                        icon = { Icon(Icons.Filled.Info, contentDescription = null, tint = Color.LightGray) },
                        modifier = Modifier.padding(vertical = 4.dp).testTag("legal_button")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.currentUser.value = null
                            viewModel.currentScreen = "login"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Çıkış Yap", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White
                    ),
                    title = {
                        Text(
                            text = "Moonzer Tv",
                            fontWeight = FontWeight.Black,
                            color = MoonRed,
                            fontSize = 30.sp,
                            modifier = Modifier.testTag("app_bar_title")
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Menu Drawer",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp).testTag("menu_hamburger")
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { currentTabSelector = "search" }) {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = "Search", tint = Color.White)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color.Black,
                    modifier = Modifier.height(72.dp)
                ) {
                    NavigationBarItem(
                        selected = currentTabSelector == "movies",
                        onClick = { currentTabSelector = "movies" },
                        icon = { Icon(Icons.Filled.Movie, contentDescription = "İçerikler") },
                        label = { Text("Lobiler") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MoonRed,
                            selectedTextColor = MoonRed,
                            indicatorColor = MoonRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextDim
                        )
                    )
                    NavigationBarItem(
                        selected = currentTabSelector == "search",
                        onClick = { currentTabSelector = "search" },
                        icon = { Icon(Icons.Filled.Search, contentDescription = "Arama") },
                        label = { Text("Arama") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MoonRed,
                            selectedTextColor = MoonRed,
                            indicatorColor = MoonRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextDim
                        )
                    )
                    NavigationBarItem(
                        selected = currentTabSelector == "history",
                        onClick = { currentTabSelector = "history" },
                        icon = { Icon(Icons.Filled.History, contentDescription = "Geçmiş") },
                        label = { Text("İzlenenler") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MoonRed,
                            selectedTextColor = MoonRed,
                            indicatorColor = MoonRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextDim
                        )
                    )
                    NavigationBarItem(
                        selected = currentTabSelector == "rating",
                        onClick = { currentTabSelector = "rating" },
                        icon = { Icon(Icons.Filled.Star, contentDescription = "Puanlama") },
                        label = { Text("Puan Ver") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MoonRed,
                            selectedTextColor = MoonRed,
                            indicatorColor = MoonRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextDim,
                            unselectedTextColor = TextDim
                        )
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(padding)
            ) {
                when (currentTabSelector) {
                    "movies" -> LobbyScreen(viewModel = viewModel, localMovies = localMovies, watchHistory = history)
                    "search" -> InteractiveSearchScreen(viewModel = viewModel)
                    "history" -> UsersWatchHistoryScreen(viewModel = viewModel, historyList = history)
                    "rating" -> RatingCenterScreen(viewModel = viewModel)
                    "watchlist" -> WatchlistScreen(viewModel = viewModel)
                }

                // Render dynamic item details popup as bottom overlay
                if (viewModel.activeDetailMovie.value != null) {
                    DetailOverlayPopup(
                        movie = viewModel.activeDetailMovie.value!!,
                        viewModel = viewModel,
                        onDismiss = { viewModel.activeDetailMovie.value = null },
                        onPlay = { item ->
                            viewModel.currentUser.value?.let { _ ->
                                viewModel.addToWatchHistory(item.imdbID)
                                viewModel.currentStreamingMovie.value = item
                                viewModel.isStreamingModeActive = true
                            }
                        }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// NATIVE SCREEN Lobiler (NETFLIX DESIGN INTERFACE)
// -------------------------------------------------------------

@Composable
fun LobbyScreen(viewModel: MainViewModel, localMovies: List<Movie>, watchHistory: List<Movie>) {
    val context = LocalContext.current
    val hiddenList by viewModel.hiddenList.collectAsState()
    val recommendedList by viewModel.recommendedMovies.collectAsState()
    val watchlistList by viewModel.watchlistList.collectAsState()

    // Filter valid movies block (No missing posters, and not hidden)
    val validMovies = remember(localMovies, hiddenList) {
        localMovies.filter { movie ->
            movie.imdbID !in hiddenList &&
            movie.poster.isNotEmpty() &&
            movie.poster != "N/A" &&
            !movie.poster.contains("placeholder") &&
            !movie.poster.contains("unsplash.com")
        }
    }

    // Categorized lists
    val popularList = remember(validMovies) {
        validMovies.filter { it.categories.contains("popular") }
    }
    val newList = remember(validMovies) {
        validMovies.filter { it.categories.contains("new") }
    }
    val filteredMovies = remember(validMovies, viewModel.activeCategoryFilter) {
        if (viewModel.activeCategoryFilter == "all") validMovies
        else validMovies.filter { it.categories.contains(viewModel.activeCategoryFilter) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // High Quality Hero Image Banner representation at top if there are movies
        if (validMovies.isNotEmpty()) {
            val featured = validMovies.firstOrNull()
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .clickable {
                            featured?.let {
                                viewModel.loadFullMovieDetails(it.imdbID) {}
                            }
                        }
                ) {
                    AsyncImage(
                        model = featured?.poster ?: "",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.5f),
                                        Color.Black
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = featured?.title ?: "",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = (featured?.year ?: "") + " • IMDB " + (featured?.rating?.ifEmpty { "8.0" } ?: "8.0"),
                            color = GoldColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = {
                                    featured?.let { f ->
                                        viewModel.addToWatchHistory(f.imdbID)
                                        viewModel.currentStreamingMovie.value = f
                                        viewModel.isStreamingModeActive = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MoonRed),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Şimdi İzle", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    featured?.let { f ->
                                        viewModel.loadFullMovieDetails(f.imdbID) {}
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bilgi", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 3-dots Menu top right of Hero banner
                    var showHeroMenu by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        IconButton(
                            onClick = { showHeroMenu = true },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                                .size(36.dp)
                                .testTag("hero_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Daha Fazla",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showHeroMenu,
                            onDismissRequest = { showHeroMenu = false },
                            modifier = Modifier.background(SurfaceCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Bunu Önerme / Gizle", color = Color.White) },
                                leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = MoonRed) },
                                onClick = {
                                    showHeroMenu = false
                                    featured?.let { f ->
                                        viewModel.hideMovie(f.imdbID)
                                        Toast.makeText(context, "${f.title} gizlendi.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (viewModel.activeCategoryFilter == "all") {
            // Personalized dynamic recommendation row
            if (recommendedList.isNotEmpty()) {
                item {
                    Text(
                        text = "Size Özel Önerilenler",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recommendedList) { movie ->
                            MoviePosterCard(movie = movie) {
                                viewModel.loadFullMovieDetails(movie.imdbID) {}
                            }
                        }
                    }
                }
            }

            // Watchlist row
            if (watchlistList.isNotEmpty()) {
                item {
                    Text(
                        text = "İzleme Listem (Sonradan İzle)",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(watchlistList) { movie ->
                            MoviePosterCard(movie = movie) {
                                viewModel.loadFullMovieDetails(movie.imdbID) {}
                            }
                        }
                    }
                }
            }

            // General Netflix Category rows representation
            if (watchHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "İzlemeye Devam Et",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(watchHistory) { movie ->
                            MoviePosterCard(movie = movie) {
                                viewModel.loadFullMovieDetails(movie.imdbID) {}
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "En Çok İzlenen Popüler İçerikler",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(popularList) { movie ->
                        MoviePosterCard(movie = movie) {
                            viewModel.loadFullMovieDetails(movie.imdbID) {}
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Yeni Çıkanlar & Eklenenler",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(newList) { movie ->
                        MoviePosterCard(movie = movie) {
                            viewModel.loadFullMovieDetails(movie.imdbID) {}
                        }
                    }
                }
            }
        } else {
            // Filtered results representation standard layout
            item {
                Text(
                    text = "Kategori Sonuçları",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp).padding(horizontal = 16.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredMovies) { movie ->
                            MoviePosterCard(movie = movie, showTitle = true) {
                                viewModel.loadFullMovieDetails(movie.imdbID) {}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoviePosterCard(movie: Movie, showTitle: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable { onClick() }
            .testTag("movie_card_" + movie.imdbID),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                .background(SurfaceCard)
        ) {
            AsyncImage(
                model = movie.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Visual Overlay rating details badge
            if (movie.rating.isNotEmpty() && movie.rating != "N/A") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = GoldColor, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(movie.rating, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        if (showTitle) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = movie.title,
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// -------------------------------------------------------------
// NATIVE SCREEN Arama (INTERACTIVE OMDB BACKEND SEARCH)
// -------------------------------------------------------------

@Composable
fun InteractiveSearchScreen(viewModel: MainViewModel) {
    var searchInputText by remember { mutableStateOf("") }
    val history by viewModel.searchHistoryList.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchInputText,
            onValueChange = {
                searchInputText = it
                viewModel.triggerSearch(it)
            },
            placeholder = { Text("Film, dizi, oyuncu ara...", color = TextDim) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color.LightGray) },
            trailingIcon = {
                if (searchInputText.isNotEmpty()) {
                    IconButton(onClick = {
                        searchInputText = ""
                        viewModel.triggerSearch("")
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.LightGray)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_text_input"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                viewModel.triggerSearch(searchInputText)
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MoonRed,
                unfocusedBorderColor = Color.DarkGray
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (searchInputText.isEmpty()) {
            // Display past search history queries
            if (history.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Son Aramalarınız", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Text("Temizle", color = MoonRed, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(history) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchInputText = entry.query
                                    viewModel.triggerSearch(entry.query)
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = TextDim, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(entry.query, color = Color.White, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = Color.DarkGray)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Moonzer TV ile her şeyi keşfedin", color = TextDim, fontSize = 14.sp)
                    }
                }
            }
        } else {
            if (viewModel.isSearchLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MoonRed)
                }
            } else {
                if (viewModel.searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Aradığınız kriterlere uygun sonuç bulunamadı.", color = TextDim, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(viewModel.searchResults) { item ->
                            MoviePosterCard(movie = item, showTitle = true) {
                                viewModel.loadFullMovieDetails(item.imdbID) {}
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// NATIVE SCREEN Son İzlenenler (WATCH HISTORY VISUALIZER)
// -------------------------------------------------------------

@Composable
fun UsersWatchHistoryScreen(viewModel: MainViewModel, historyList: List<Movie>) {
    if (historyList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = MoonRed, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Henüz Hiçbir İçerik İzlemediniz", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Yeni filmler ve diziler izleyerek geçmişinizi doldurabilirsiniz.", color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Son İzledikleriniz • Geçmiş",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(historyList) { movie ->
                    MoviePosterCard(movie = movie, showTitle = true) {
                        viewModel.loadFullMovieDetails(movie.imdbID) {}
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// NATIVE SCREEN Puan Ber (RATING SYSTEM CENTER)
// -------------------------------------------------------------

@Composable
fun RatingCenterScreen(viewModel: MainViewModel) {
    val ratings by viewModel.ratingFlow.collectAsState()
    val average by viewModel.averageRating.collectAsState()
    val avg = average ?: 0f

    var selectedStars by remember { mutableIntStateOf(5) }
    var feedbackText by remember { mutableStateOf("") }
    var successText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Moonzer TV'ye Puan Verin", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Fikirlerinizi bizimle paylaşın, uygulamayı sizler için daha harika yapalım.", color = TextDim, fontSize = 14.sp)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.1f", avg),
                            color = MoonRed,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text("Ortalama", color = TextDim, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                    Column {
                        Row {
                            repeat(5) { index ->
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = if (index < avg.toInt()) GoldColor else Color.DarkGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("${ratings.size} yerel kullanıcı değerlendirmesi", color = Color.LightGray, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Değerlendirmeniz", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(5) { index ->
                            val star = index + 1
                            IconButton(onClick = { selectedStars = star }) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = if (star <= selectedStars) GoldColor else Color.DarkGray,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        label = { Text("Geri Bildirim / Yorum") },
                        modifier = Modifier.fillMaxWidth().testTag("native_feedback_msg"),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MoonRed,
                            unfocusedBorderColor = Color.DarkGray
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (successText.isNotEmpty()) {
                        Text(successText, color = Color.Green, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }

                    Button(
                        onClick = {
                            viewModel.addRatingNative(selectedStars, feedbackText)
                            feedbackText = ""
                            successText = "Değerlendirmeniz başarıyla gönderildi, teşekkür ederiz!"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MoonRed),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Gönder", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("Son Geri Bildirimler", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        items(ratings.take(15)) { rating ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(rating.userName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row {
                            repeat(5) { idx ->
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = if (idx < rating.stars) GoldColor else Color.DarkGray,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    if (rating.feedback.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(rating.feedback, color = Color.LightGray, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DETAIL OVERLAY POPUP COMPOSABLE
// -------------------------------------------------------------

@Composable
fun DetailOverlayPopup(movie: Movie, viewModel: MainViewModel, onDismiss: () -> Unit, onPlay: (Movie) -> Unit) {
    val isInWatchlist by viewModel.isMovieInWatchlistFlow(movie.imdbID).collectAsState(initial = false)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkBackground),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .clickable(enabled = false) {}
                .testTag("movie_details_popup"),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = movie.title,
                            fontSize = 26.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = GoldColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = movie.rating.ifEmpty { "8.0" } + " | " + movie.year + " | " + if (movie.type == "series") "Dizi" else "Film",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_details")) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(width = 110.dp, height = 160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = movie.poster,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Button(
                            onClick = { onPlay(movie) },
                            colors = ButtonDefaults.buttonColors(containerColor = MoonRed),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("popup_play_btn"),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Şimdi Full İzle", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.toggleWatchlist(movie.imdbID) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isInWatchlist) Color.Gray else MoonRed
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("popup_watchlist_btn"),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(
                                if (isInWatchlist) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isInWatchlist) "Sözleşmeli Listenden Çıkar" else "İzleme Listeme Ekle",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Kategoriler: " + movie.categories.split(",").joinToString(" | ") { it.replaceFirstChar { c->c.uppercase() } },
                            color = MoonRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Toplam İzlenme: " + movie.views + " kez",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Özet & Detaylar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = movie.plot.ifEmpty { "Bu film / dizi hakkında henüz detaylı açıklama girilmemiştir." },
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// PREMIUM NATIVE EMBEDDED WEB PLAYER (playimdb API integration)
// -------------------------------------------------------------

@Composable
fun VideoStreamingScreen(movie: Movie, onDismiss: () -> Unit) {
    val playUrl = "https://www.playimdb.com/title/${movie.imdbID}/"
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Toast.makeText(ctx, "Kaliteli yayın yükleniyor...", Toast.LENGTH_SHORT).show()
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                        }
                    }

                    loadUrl(playUrl)
                }
            }
        )

        // Overlay Exit button to leave full screen player
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .align(Alignment.TopEnd)
                .testTag("exit_player_button")
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Close Player", tint = Color.White)
        }
    }
}

// -------------------------------------------------------------
// ADMIN PANEL SCREEN (DETAILED DASHBOARD & DEVICE / IP MONITORING)
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val sessions by viewModel.allDbSessions.collectAsState()
    val users by viewModel.allDbUsers.collectAsState()
    val localMovies by viewModel.localMovieList.collectAsState()

    var customImdbID by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }
    var customYear by remember { mutableStateOf("") }
    var customType by remember { mutableStateOf("movie") }
    var customCategories by remember { mutableStateOf("movies") }
    var adminMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                title = { Text("Yönetim Paneli", fontWeight = FontWeight.Black, color = MoonRed) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Sistem İstatistikleri", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(users.size.toString(), color = MoonRed, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("Toplam Üye", color = TextDim, fontSize = 11.sp)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(localMovies.size.toString(), color = MoonRed, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("Toplam İçerik", color = TextDim, fontSize = 11.sp)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val totalViews = localMovies.sumOf { it.views }
                            Text(totalViews.toString(), color = MoonRed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Toplam İzlenme", color = TextDim, fontSize = 11.sp)
                        }
                    }
                }
            }

            // GİRİŞ YAPAN CİHAZLAR VE IP'LER listelenmelidir
            item {
                Text("Giriş Yapan Cihazlar & IP Logları", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }

            items(sessions.take(20)) { session ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Kullanıcı: " + session.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("IP: " + session.ipAddress, color = MoonRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Cihaz: " + session.deviceName, color = Color.LightGray, fontSize = 13.sp)
                            Text("Oturum Açma: " + java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault()).format(java.util.Date(session.timestamp)), color = TextDim, fontSize = 11.sp)
                        }
                    }
                }
            }

            item {
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Yönetici Araçları - Yeni İçerik Ekle", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = customImdbID,
                            onValueChange = { customImdbID = it },
                            label = { Text("IMDb ID (Örn: tt123456)") },
                            modifier = Modifier.fillMaxWidth().testTag("admin_content_imdb"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MoonRed, focusedLabelColor = MoonRed)
                        )
                        OutlinedTextField(
                            value = customTitle,
                            onValueChange = { customTitle = it },
                            label = { Text("İçerik Başlığı") },
                            modifier = Modifier.fillMaxWidth().testTag("admin_content_title"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MoonRed, focusedLabelColor = MoonRed)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customYear,
                                onValueChange = { customYear = it },
                                label = { Text("Yıl") },
                                modifier = Modifier.weight(1f).testTag("admin_content_year"),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MoonRed, focusedLabelColor = MoonRed)
                            )
                            OutlinedTextField(
                                value = customCategories,
                                onValueChange = { customCategories = it },
                                label = { Text("Kategoriler (örn: popular,movies)") },
                                modifier = Modifier.weight(1f).testTag("admin_content_cats"),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MoonRed, focusedLabelColor = MoonRed)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Tür: ", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(selected = customType == "movie", onClick = { customType = "movie" })
                            Text("Film", color = Color.LightGray)
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(selected = customType == "series", onClick = { customType = "series" })
                            Text("Dizi", color = Color.LightGray)
                        }

                        if (adminMessage.isNotEmpty()) {
                            Text(adminMessage, color = Color.Green, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (customImdbID.isBlank() || customTitle.isBlank() || customYear.isBlank()) {
                                    adminMessage = "Lütfen gerekli tüm alanları doldurun."
                                    return@Button
                                }
                                viewModel.addCustomMovieAdmin(
                                    imdbID = customImdbID,
                                    title = customTitle,
                                    year = customYear,
                                    type = customType,
                                    categories = customCategories
                                )
                                adminMessage = "İçerik başarıyla eklendi."
                                customImdbID = ""
                                customTitle = ""
                                customYear = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MoonRed),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Kaydet & Yayınla", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Text("Yayındaki Tüm İçerikler (" + localMovies.size + ")", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }

            items(localMovies) { movie ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("IMDb: " + movie.imdbID + " | İzlenme: " + movie.views, color = TextDim, fontSize = 12.sp)
                        }
                        IconButton(onClick = { viewModel.removeMovieAdmin(movie.imdbID) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Tetikle Sil", tint = MoonRed)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// LEGAL, ABOUT THE APP, TERMS OF SERVICES & DISCLAIMERS
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalAboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                title = { Text("Sözleşmeler & Bilgi", fontWeight = FontWeight.Black, color = MoonRed) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Hakkımızda",
                color = MoonRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Moonzer TV, kullanıcılarının en sevdiği film ve dizileri en yüksek performansla, entegre ve modern arayüzle izlemesine olanak sağlayan yenilikçi bir platformdur. IMDb ve OMDB API protokollerini kullanır.",
                color = Color.LightGray,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            HorizontalDivider(color = BorderColor)

            Text(
                text = "Telif Hakkı & Sorumluluk Reddi Beyanı",
                color = MoonRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Uygulama sunucularında hiçbir video içeriği barındırılmaz. Tüm video bağlantıları bağımsız ve herkese açık üçüncü taraf streaming kaynakları ('playimdb.com' vb.) aracılığıyla web frame'leri olarak yüklenmektedir. Moonzer TV, telif hakkı ihlallerinden doğrudan veya dolaylı olarak sorumlu tutulamaz. Telif haklarına aykırı olduğunu düşündüğünüz herhangi bir bağlantı veya içeriği kaldırmak için ilgili video sağlayıcı hizmetleri ile iletişime geçiniz.",
                color = Color.LightGray,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            HorizontalDivider(color = BorderColor)

            Text(
                text = "Kullanım Sözleşmesi & Gizlilik Politikası",
                color = MoonRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Bu uygulamayı kullanarak tüm kullanım sözleşmesini ve gizlilik şartlarını peşinen kabul etmiş olursunuz. Moonzer TV, üye şifrelerinizi ve kullanıcı verilerinizi yerel ve şifrelenmiş veri tabanında güvenle saklamaktadır. Aramalarınız ve geçmişiniz sadece sizin kullanıcı deneyiminizi kolaylaştırmak için yerel cihazınızda depolanmaktadır. Verileriniz hiçbir üçüncü taraf reklam şirketiyle veya kurumla kesinlikle paylaşılmaz.",
                color = Color.LightGray,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Sözleşmeleri Kabul Ediyorum", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AboutScreen(innerPadding: PaddingValues) {
    LegalAboutScreen {}
}

@Composable
fun WatchlistScreen(viewModel: MainViewModel) {
    val watchlistList by viewModel.watchlistList.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "İzleme Listeniz",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${watchlistList.size} İçerik",
                color = TextDim,
                fontSize = 14.sp
            )
        }
        
        if (watchlistList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "İzleme listeniz henüz boş.",
                        color = Color.LightGray,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Detay ekranından içerikleri listenize ekleyebilirsiniz.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(watchlistList) { movie ->
                    MoviePosterCard(movie = movie, showTitle = true) {
                        viewModel.loadFullMovieDetails(movie.imdbID) {}
                    }
                }
            }
        }
    }
}

