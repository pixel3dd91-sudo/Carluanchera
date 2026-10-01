package com.carlauncher.player

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private var svc: PlayerService? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var shownArtId = -2L

    private lateinit var bg: ImageView
    private lateinit var art: ImageView
    private lateinit var clock: TextView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var info: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var bars: BarsView
    private lateinit var progress: ProgressLine
    private lateinit var slotsRow: LinearLayout

    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName, b: IBinder) {
            val s = (b as PlayerService.LocalBinder).service
            svc = s
            s.listener = { runOnUiThread { refreshTrack() } }
            if (granted(audioPerm()) && s.tracks.isEmpty()) s.loadTracks()
            attachVisualizer()
            refreshTrack()
        }
        override fun onServiceDisconnected(n: ComponentName) { svc = null }
    }

    private val ticker = object : Runnable {
        override fun run() {
            clock.text = timeFmt.format(Date())
            svc?.let { s ->
                val playing = s.isPlaying
                bars.setPlaying(playing)
                btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                val dur = s.duration
                val pos = s.position
                if (!progress.dragging) progress.fraction = if (dur > 0) pos.toFloat() / dur else 0f
                val t = s.currentTrack
                if (t != null) {
                    info.text = "${s.index + 1}/${s.tracks.size}   •   ${formatTime(pos)} / ${formatTime(dur)}"
                }
            }
            handler.postDelayed(this, 400)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        bg = findViewById(R.id.bg)
        art = findViewById(R.id.art)
        clock = findViewById(R.id.clock)
        title = findViewById(R.id.title)
        artist = findViewById(R.id.artist)
        info = findViewById(R.id.info)
        btnPlay = findViewById(R.id.btnPlay)
        bars = findViewById(R.id.bars)
        progress = findViewById(R.id.progress)
        slotsRow = findViewById(R.id.slots)
        title.isSelected = true

        btnPlay.setOnClickListener { svc?.toggle() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { svc?.next() }
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { svc?.prev() }
        findViewById<ImageButton>(R.id.btnApps).setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        progress.onSeek = { f -> svc?.let { it.seekTo((f * it.duration).toInt()) } }

        buildSlots()

        val i = Intent(this, PlayerService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        bindService(i, conn, BIND_AUTO_CREATE)

        requestPerms()
    }

    override fun onResume() {
        super.onResume()
        buildSlots()
        svc?.let { if (it.tracks.isEmpty() && granted(audioPerm())) it.loadTracks() }
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        try { unbindService(conn) } catch (_: Exception) {}
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { /* لانچر نباید بسته شود */ }

    // ---------- Permissions ----------
    private fun audioPerm() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun granted(p: String) =
        Build.VERSION.SDK_INT < 23 || checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun requestPerms() {
        if (Build.VERSION.SDK_INT < 23) return
        val need = listOf(audioPerm(), Manifest.permission.RECORD_AUDIO).filter { !granted(it) }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        svc?.let { if (granted(audioPerm())) it.loadTracks() }
        attachVisualizer()
    }

    private fun attachVisualizer() {
        if (granted(Manifest.permission.RECORD_AUDIO)) svc?.let { bars.attach(it.audioSessionId) }
    }

    // ---------- Track UI ----------
    private fun refreshTrack() {
        val s = svc ?: return
        val t = s.currentTrack
        if (t == null) {
            title.text = "موزیکی پیدا نشد"
            artist.text = "مجوز دسترسی را بدهید یا فلش USB را وصل کنید"
            info.text = ""
            shownArtId = -1
            setArt(null)
            return
        }
        title.text = t.title
        artist.text = t.artist
        if (t.id != shownArtId) {
            shownArtId = t.id
            Thread {
                val bmp = ArtLoader.load(this, t)
                runOnUiThread { if (shownArtId == t.id) setArt(bmp) }
            }.start()
        }
    }

    private fun setArt(b: Bitmap?) {
        if (b == null) {
            art.setImageResource(R.drawable.art_placeholder)
            bg.setImageDrawable(null)
        } else {
            art.setImageBitmap(b)
            bg.setImageBitmap(ArtLoader.blur(b))
        }
    }

    // ---------- 5 shortcut slots ----------
    private fun buildSlots() {
        slotsRow.removeAllViews()
        val pm = packageManager
        for (i in 0 until 5) {
            var pkg: String? = prefs.getString("slot$i", null)
            val icon = ImageView(this)
            val label = TextView(this).apply {
                setTextColor(Color.WHITE); textSize = 12f; maxLines = 1; gravity = Gravity.CENTER
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            if (pkg != null) {
                try {
                    icon.setImageDrawable(pm.getApplicationIcon(pkg))
                    label.text = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
                } catch (e: PackageManager.NameNotFoundException) {
                    pkg = null
                }
            }
            if (pkg == null) {
                icon.setImageResource(R.drawable.ic_add)
                label.text = ""
            }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.slot_bg)
                isClickable = true
                isFocusable = true
                addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
                addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val valid = pkg
            cell.setOnClickListener {
                if (valid != null) pm.getLaunchIntentForPackage(valid)?.let { startActivity(it) } else pickApp(i)
            }
            cell.setOnLongClickListener { slotMenu(i); true }
            slotsRow.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(6), 0, dp(6), 0)
            })
        }
    }

    private fun pickApp(slot: Int) {
        startActivityForResult(Intent(this, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_PICK, true), 100 + slot)
    }

    private fun slotMenu(slot: Int) {
        AlertDialog.Builder(this)
            .setItems(arrayOf("تغییر برنامه", "حذف میانبر")) { _, which ->
                if (which == 0) pickApp(slot)
                else { prefs.edit().remove("slot$slot").apply(); buildSlots() }
            }.show()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req in 100..104 && res == RESULT_OK) {
            data?.getStringExtra(AppsActivity.EXTRA_PKG)?.let {
                prefs.edit().putString("slot${req - 100}", it).apply()
                buildSlots()
            }
        }
    }
}
