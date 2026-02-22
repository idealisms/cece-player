package com.cece.player

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

data class Track(val uri: Uri, val title: String, val album: String, val dataPath: String)

class MainActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var tracks: List<Track> = emptyList()
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var trackInfo: TextView
    private lateinit var centerContainer: FrameLayout
    private lateinit var btnPlay: Button
    private lateinit var albumArt: ImageView
    private lateinit var pauseOverlay: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    private val hideSystemUiRunnable = Runnable { hideSystemUI() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        trackInfo = findViewById(R.id.trackInfo)
        centerContainer = findViewById(R.id.centerContainer)
        btnPlay = findViewById(R.id.btnPlay)
        albumArt = findViewById(R.id.albumArt)
        pauseOverlay = findViewById(R.id.pauseOverlay)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)

        trackInfo.isSelected = true  // enables marquee scrolling

        btnPlay.setOnClickListener { togglePlayPause() }
        centerContainer.setOnClickListener { togglePlayPause() }
        btnPrev.setOnClickListener { playPrev() }
        btnNext.setOnClickListener { playNext() }

        // Make prev/next buttons true circles (constrain height = width after layout)
        btnPrev.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                btnPrev.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val size = btnPrev.width
                if (size > 0) {
                    val reduced = (size * 0.7).toInt()
                    for (btn in listOf(btnPrev, btnNext)) {
                        val p = btn.layoutParams as LinearLayout.LayoutParams
                        p.width = reduced
                        p.height = reduced
                        p.weight = 0f
                        btn.layoutParams = p
                    }
                }
            }
        })

        requestPermissionsAndLoad()
    }

    private fun requestPermissionsAndLoad() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            loadTracks()
        } else {
            ActivityCompat.requestPermissions(this, permissions, 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadTracks()
        } else {
            trackInfo.text = "Storage permission needed"
        }
    }

    private fun loadTracks() {
        val found = mutableListOf<Track>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATA
        )
        // Match any audio file whose path contains a folder named "Cece"
        val selection = "${MediaStore.Audio.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("%/Cece/%")

        contentResolver.query(collection, projection, selection, selectionArgs, "${MediaStore.Audio.Media.RELATIVE_PATH}, ${MediaStore.Audio.Media.DISPLAY_NAME}")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol)
                val fullPath = cursor.getString(pathCol) ?: ""

                val uri = Uri.withAppendedPath(collection, id.toString())
                val title = displayName.substringBeforeLast(".")

                // Album = the folder immediately inside "Cece", or "Cece" if directly inside
                val ceceSegment = fullPath.indexOf("/Cece/")
                val album = if (ceceSegment >= 0) {
                    val afterCece = fullPath.substring(ceceSegment + 6) // skip "/Cece/"
                    val nextSlash = afterCece.indexOf('/')
                    if (nextSlash > 0) afterCece.substring(0, nextSlash) else "Cece"
                } else "Cece"

                found.add(Track(uri, title, album, fullPath))
            }
        }

        tracks = found
        if (tracks.isNotEmpty()) {
            currentIndex = 0
            updateTrackInfo()
            startPlayback()
        } else {
            trackInfo.text = "No audio found in Cece folder"
        }
    }

    private fun updateTrackInfo() {
        if (tracks.isEmpty()) return
        val t = tracks[currentIndex]
        trackInfo.text = "${t.title}  —  ${t.album}"
    }

    private fun startPlayback() {
        if (tracks.isEmpty()) return
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(applicationContext, tracks[currentIndex].uri)
            prepare()
            start()
            setOnCompletionListener { playNext() }
        }
        btnPlay.text = "⏸"
        updateAlbumArt(isPlaying = true)
    }

    private fun updateAlbumArt(isPlaying: Boolean) {
        if (tracks.isEmpty()) return
        val pngPath = tracks[currentIndex].dataPath.substringBeforeLast(".") + ".png"
        val bitmap = if (File(pngPath).exists()) BitmapFactory.decodeFile(pngPath) else null
        if (bitmap != null) {
            albumArt.visibility = View.VISIBLE
            btnPlay.visibility = View.GONE
            pauseOverlay.visibility = if (isPlaying) View.GONE else View.VISIBLE
            albumArt.post {
                val scale = minOf(
                    albumArt.width / bitmap.width,
                    albumArt.height / bitmap.height
                ).coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(
                    bitmap, bitmap.width * scale, bitmap.height * scale, false
                )
                val drawable = BitmapDrawable(resources, scaled).also { it.setFilterBitmap(false) }
                albumArt.scaleType = ImageView.ScaleType.CENTER
                albumArt.setImageDrawable(drawable)
            }
        } else {
            albumArt.visibility = View.GONE
            pauseOverlay.visibility = View.GONE
            btnPlay.visibility = View.VISIBLE
        }
    }

    private fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            btnPlay.text = "▶"
            if (albumArt.visibility == View.VISIBLE) pauseOverlay.visibility = View.VISIBLE
        } else {
            mp.start()
            btnPlay.text = "⏸"
            pauseOverlay.visibility = View.GONE
        }
    }

    private fun playNext() {
        if (tracks.isEmpty()) return
        currentIndex = (currentIndex + 1) % tracks.size
        updateTrackInfo()
        startPlayback()
    }

    private fun playPrev() {
        if (tracks.isEmpty()) return
        // If more than 3s into track, restart it; otherwise go to previous
        val mp = mediaPlayer
        if (mp != null && mp.currentPosition > 3000) {
            mp.seekTo(0)
        } else {
            currentIndex = (currentIndex - 1 + tracks.size) % tracks.size
            updateTrackInfo()
            startPlayback()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        try { startLockTask() } catch (_: Exception) {}
        // Re-hide after any transient reveal
        window.decorView.setOnSystemUiVisibilityChangeListener {
            handler.removeCallbacks(hideSystemUiRunnable)
            handler.postDelayed(hideSystemUiRunnable, 2000)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(hideSystemUiRunnable)
    }

    // Block back button completely
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing - intentionally trap back button
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        handler.removeCallbacksAndMessages(null)
    }
}
