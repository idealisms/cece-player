package com.cece.player

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.MotionEvent
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
import androidx.core.view.isVisible
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.media.RemoteMediaClient
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
    private lateinit var batteryView: BatteryView
    private lateinit var headphonesView: HeadphonesView
    private lateinit var btnCast: MediaRouteButton

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    private var btConnected = false
    private val BT_VOLUME_CAP = 0.5f

    private var castSettings: CastSettings? = null
    private var castContext: CastContext? = null
    private var httpServer: LocalHttpServer? = null
    private var isCasting = false

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
            val status = client.mediaStatus ?: return
            if (status.playerState == MediaStatus.PLAYER_STATE_IDLE &&
                status.idleReason == MediaStatus.IDLE_REASON_FINISHED) {
                runOnUiThread { playNext() }
            }
        }
    }

    private val hideSystemUiRunnable = Runnable { hideSystemUI() }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (lvl >= 0 && scale > 0) batteryView.level = lvl * 100 / scale
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            checkBluetoothHeadphones()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            checkBluetoothHeadphones()
        }
    }

    // Receives battery level updates from Bluetooth devices (API 33+)
    private val btBatteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
            if (level >= 0) headphonesView.batteryLevel = level
        }
    }

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateCastButton()
        }
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateCastButton()
        }
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateCastButton()
        }
    }

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            isCasting = true
            val savedPosition = mediaPlayer?.currentPosition ?: 0
            mediaPlayer?.pause()
            httpServer = LocalHttpServer(tracks).also { it.start() }
            session.remoteMediaClient?.registerCallback(remoteMediaClientCallback)
            loadTrackOnCast(currentIndex, savedPosition)
            updateCastButton()
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            session.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
            isCasting = false
            httpServer?.stop()
            httpServer = null
            startPlayback()
            updateCastButton()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            isCasting = true
            val savedPosition = mediaPlayer?.currentPosition ?: 0
            mediaPlayer?.pause()
            httpServer = LocalHttpServer(tracks).also { it.start() }
            session.remoteMediaClient?.registerCallback(remoteMediaClientCallback)
            loadTrackOnCast(currentIndex, savedPosition)
            updateCastButton()
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            isCasting = false
            updateCastButton()
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            isCasting = false
            updateCastButton()
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            isCasting = false
            updateCastButton()
        }

        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
    }

    private fun checkBluetoothHeadphones() {
        btConnected = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        updateCastButton()
        val vol = if (btConnected) BT_VOLUME_CAP else 1f
        mediaPlayer?.setVolume(vol, vol)
        if (!btConnected) {
            headphonesView.batteryLevel = -1
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            fetchBtBattery()
        }
    }

    private fun updateCastButton() {
        val settings = castSettings
        if (settings == null) {
            headphonesView.isVisible = btConnected
            return
        }
        val routes = MediaRouter.getInstance(this).routes
        val targetVisible = routes.any { it.name.contains(settings.name, ignoreCase = true) }
        val showCast = (targetVisible && !btConnected) || isCasting
        btnCast.isVisible = showCast
        headphonesView.isVisible = btConnected && !isCasting
    }

    @SuppressLint("MissingPermission")
    private fun fetchBtBattery() {
        @Suppress("DEPRECATION")
        BluetoothAdapter.getDefaultAdapter()?.getProfileProxy(
            this,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    @Suppress("UNCHECKED_CAST")
                    val devices = proxy.connectedDevices as List<BluetoothDevice>
                    val getBattery = try { BluetoothDevice::class.java.getMethod("getBatteryLevel") } catch (_: Exception) { null }
                    val level = getBattery?.let { m ->
                        devices.mapNotNull { m.invoke(it) as? Int }.firstOrNull { it >= 0 }
                    } ?: -1
                    runOnUiThread { headphonesView.batteryLevel = level }
                    @Suppress("DEPRECATION")
                    BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                }
                override fun onServiceDisconnected(profile: Int) {}
            },
            BluetoothProfile.A2DP
        )
    }

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
        batteryView = findViewById(R.id.batteryView)
        headphonesView = findViewById(R.id.headphonesView)
        btnCast = findViewById(R.id.btnCast)

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

        castSettings = loadCastSettings(this)
        if (castSettings != null) {
            castContext = try { CastContext.getSharedInstance(this) } catch (e: Exception) { null }
            castContext?.let { ctx ->
                val selector = MediaRouteSelector.Builder()
                    .addControlCategory(CastMediaControlIntent.categoryForCast(
                        CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                    .build()
                btnCast.routeSelector = selector
                // Intercept touches to auto-connect instead of showing route picker dialog
                btnCast.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> { v.isPressed = true; true }
                        MotionEvent.ACTION_UP -> { v.isPressed = false; handleCastButtonClick(); true }
                        MotionEvent.ACTION_CANCEL -> { v.isPressed = false; true }
                        else -> false
                    }
                }
                ctx.sessionManager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
                MediaRouter.getInstance(this).addCallback(selector, mediaRouterCallback,
                    MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            }
        }

        requestPermissionsAndLoad()
    }

    private fun requestPermissionsAndLoad() {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.BLUETOOTH_CONNECT)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.BLUETOOTH_CONNECT)
            else ->
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
        if (requestCode == 1) {
            val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, audioPerm) == PackageManager.PERMISSION_GRANTED) {
                loadTracks()
            } else {
                trackInfo.text = "Storage permission needed"
            }
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
                val title = TrackUtils.titleFromFilename(displayName)
                val album = TrackUtils.albumFromPath(fullPath)

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
        trackInfo.text = t.title
    }

    private fun startPlayback() {
        if (tracks.isEmpty()) return
        mediaPlayer?.release()
        val vol = if (btConnected) BT_VOLUME_CAP else 1f
        mediaPlayer = MediaPlayer().apply {
            setDataSource(applicationContext, tracks[currentIndex].uri)
            prepare()
            setVolume(vol, vol)
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
        if (isCasting) {
            val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
            client.togglePlayback()
            return
        }
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
        currentIndex = TrackUtils.nextIndex(currentIndex, tracks.size)
        updateTrackInfo()
        if (isCasting) {
            updateAlbumArt(isPlaying = true)
            loadTrackOnCast(currentIndex, 0)
        } else {
            startPlayback()
        }
    }

    private fun playPrev() {
        if (tracks.isEmpty()) return
        if (isCasting) {
            currentIndex = TrackUtils.prevIndex(currentIndex, tracks.size)
            updateTrackInfo()
            updateAlbumArt(isPlaying = true)
            loadTrackOnCast(currentIndex, 0)
            return
        }
        // If more than 3s into track, restart it; otherwise go to previous
        val mp = mediaPlayer
        if (mp != null && mp.currentPosition > 3000) {
            mp.seekTo(0)
        } else {
            currentIndex = TrackUtils.prevIndex(currentIndex, tracks.size)
            updateTrackInfo()
            startPlayback()
        }
    }

    private fun handleCastButtonClick() {
        if (isCasting) {
            castContext?.sessionManager?.endCurrentSession(true)
        } else {
            val settings = castSettings ?: return
            val route = MediaRouter.getInstance(this).routes
                .firstOrNull { it.name.contains(settings.name, ignoreCase = true) }
            if (route != null) MediaRouter.getInstance(this).selectRoute(route)
        }
    }

    private fun loadTrackOnCast(index: Int, positionMs: Int) {
        val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        val track = tracks.getOrNull(index) ?: return
        val ip = getLocalIpAddress()
        val mediaInfo = MediaInfo.Builder("http://$ip:8765/track/$index")
            .setContentType("audio/mpeg")
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(MediaMetadata.KEY_TITLE, track.title)
            }).build()
        client.load(MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime(positionMs.toLong())
            .build())
    }

    @Suppress("DEPRECATION")
    private fun getLocalIpAddress(): String {
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
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
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
        checkBluetoothHeadphones()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(btBatteryReceiver,
                IntentFilter("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"))
        }
        // Re-hide after any transient reveal
        window.decorView.setOnSystemUiVisibilityChangeListener {
            handler.removeCallbacks(hideSystemUiRunnable)
            handler.postDelayed(hideSystemUiRunnable, 2000)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(hideSystemUiRunnable)
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try { unregisterReceiver(btBatteryReceiver) } catch (_: Exception) {}
        }
        if (!isCasting) {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    btnPlay.text = "▶"
                    if (albumArt.visibility == View.VISIBLE) pauseOverlay.visibility = View.VISIBLE
                }
            }
        }
    }

    // Block back button completely
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing - intentionally trap back button
    }

    override fun onDestroy() {
        super.onDestroy()
        castContext?.sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        if (castSettings != null) {
            MediaRouter.getInstance(this).removeCallback(mediaRouterCallback)
        }
        httpServer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        handler.removeCallbacksAndMessages(null)
    }
}
