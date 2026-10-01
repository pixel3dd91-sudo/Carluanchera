package com.carlauncher.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore

data class Track(val id: Long, val title: String, val artist: String, val uri: Uri, val albumId: Long)

class PlayerService : Service() {

    inner class LocalBinder : Binder() {
        val service: PlayerService get() = this@PlayerService
    }

    private val binder = LocalBinder()
    private val mp = MediaPlayer()
    private var prepared = false
    private var audioManager: AudioManager? = null
    private var resumeOnFocus = false

    var tracks: List<Track> = emptyList(); private set
    var index = 0; private set
    var listener: (() -> Unit)? = null

    val currentTrack: Track? get() = tracks.getOrNull(index)
    val isPlaying: Boolean get() = try { prepared && mp.isPlaying } catch (e: Exception) { false }
    val position: Int get() = if (prepared) try { mp.currentPosition } catch (e: Exception) { 0 } else 0
    val duration: Int get() = if (prepared) try { mp.duration } catch (e: Exception) { 0 } else 0
    val audioSessionId: Int get() = mp.audioSessionId

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        try {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocus = false; if (isPlaying) mp.pause() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { if (isPlaying) { resumeOnFocus = true; mp.pause() } }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mp.setVolume(0.3f, 0.3f)
                AudioManager.AUDIOFOCUS_GAIN -> {
                    mp.setVolume(1f, 1f)
                    if (resumeOnFocus) { mp.start(); resumeOnFocus = false }
                }
            }
        } catch (_: Exception) {}
        listener?.invoke()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        )
        mp.setOnCompletionListener { next() }
        mp.setOnErrorListener { _, _, _ -> prepared = false; listener?.invoke(); true }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun showForeground() {
        val channel = "player"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channel, "Player", NotificationManager.IMPORTANCE_LOW))
        }
        val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else Notification.Builder(this)
        b.setSmallIcon(R.drawable.ic_play)
            .setContentTitle(currentTrack?.title ?: "Car Launcher")
            .setContentText(currentTrack?.artist ?: "")
            .setContentIntent(pi)
        startForeground(1, b.build())
    }

    fun loadTracks() {
        val list = ArrayList<Track>()
        try {
            val proj = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID
            )
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val artist = c.getString(2)?.takeIf { it != "<unknown>" } ?: ""
                    list.add(
                        Track(id, c.getString(1) ?: "Unknown", artist,
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                            c.getLong(3))
                    )
                }
            }
        } catch (_: Exception) {}
        tracks = list
        if (index >= list.size) index = 0
        listener?.invoke()
    }

    @Suppress("DEPRECATION")
    private fun requestFocus() {
        audioManager?.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    fun play(i: Int) {
        if (tracks.isEmpty()) return
        index = ((i % tracks.size) + tracks.size) % tracks.size
        try {
            mp.reset()
            mp.setDataSource(this, tracks[index].uri)
            mp.prepare()
            prepared = true
            requestFocus()
            mp.start()
        } catch (e: Exception) {
            prepared = false
        }
        showForeground()
        listener?.invoke()
    }

    fun toggle() {
        if (tracks.isEmpty()) return
        if (!prepared) { play(index); return }
        try {
            if (mp.isPlaying) mp.pause() else { requestFocus(); mp.start() }
        } catch (_: Exception) {}
        listener?.invoke()
    }

    fun next() = play(index + 1)

    fun prev() { if (position > 3000) seekTo(0) else play(index - 1) }

    fun seekTo(ms: Int) { if (prepared) try { mp.seekTo(ms) } catch (_: Exception) {} }

    override fun onDestroy() {
        try { mp.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
