package fr.lretrain.melopet

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var petImage: ImageView
    private lateinit var petName: TextView
    private lateinit var petInfo: TextView
    private lateinit var petEvent: TextView
    private lateinit var nowPlaying: TextView
    private lateinit var barHunger: ProgressBar
    private lateinit var barEnergy: ProgressBar
    private lateinit var barJoy: ProgressBar
    private lateinit var setupCard: View
    private lateinit var moodBars: LinearLayout
    private lateinit var historyList: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var frame = 0
    private var frames = arrayOfNulls<Bitmap>(2)
    private var listKey = ""
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)

    private val loop = object : Runnable {
        override fun run() {
            refresh()
            frame = 1 - frame
            frames[frame]?.let { showFrame(it) }
            handler.postDelayed(this, 650)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        petImage = findViewById(R.id.petImage)
        petName = findViewById(R.id.petName)
        petInfo = findViewById(R.id.petInfo)
        petEvent = findViewById(R.id.petEvent)
        nowPlaying = findViewById(R.id.nowPlaying)
        barHunger = findViewById(R.id.barHunger)
        barEnergy = findViewById(R.id.barEnergy)
        barJoy = findViewById(R.id.barJoy)
        setupCard = findViewById(R.id.setupCard)
        moodBars = findViewById(R.id.moodBars)
        historyList = findViewById(R.id.historyList)

        findViewById<Button>(R.id.btnPet).setOnClickListener {
            val ok = PetEngine.edit(this) { it.pet(System.currentTimeMillis()) }
            if (!ok) Toast.makeText(this, "Un câlin toutes les 10 minutes maximum !", Toast.LENGTH_SHORT).show()
            PetWidgetProvider.updateAll(this)
            refresh()
        }
        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btnAppInfo).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.btnWidget).setOnClickListener { pinWidget() }
        petName.setOnClickListener { renameDialog() }
    }

    override fun onResume() {
        super.onResume()
        listKey = ""
        setupCard.visibility = if (hasNotificationAccess()) View.GONE else View.VISIBLE
        handler.removeCallbacks(loop)
        handler.post(loop)
        PetWidgetProvider.updateAll(this)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(loop)
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }

    private fun showFrame(bmp: Bitmap) {
        val d = BitmapDrawable(resources, bmp)
        d.isFilterBitmap = false
        petImage.setImageDrawable(d)
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val s = PetEngine.load(this).also { it.tick(now) }
        frames[0] = PetEngine.bitmap(s, 0, now)
        frames[1] = PetEngine.bitmap(s, 1, now)

        val expr = s.expression(now)
        petName.text = s.name
        val next = s.stage.next()
        val progress = if (next != null) " · ${s.xp}/${next.minXp} XP" else ""
        petInfo.text = "${s.stage.label} · forme ${s.form().label.lowercase()} · ${expr.label.lowercase()}$progress"
        petEvent.text = s.lastEvent
        nowPlaying.text = when {
            s.playing && s.trackTitle.isNotEmpty() ->
                "♪ ${s.trackTitle} — ${s.trackArtist}\nAmbiance : ${s.currentMood.label}"
            s.trackTitle.isNotEmpty() -> "En pause · dernier morceau : ${s.trackTitle}"
            else -> "Lance un morceau sur Spotify !"
        }
        barHunger.progress = s.hunger.toInt()
        barEnergy.progress = s.energy.toInt()
        barJoy.progress = s.joy.toInt()

        val key = s.name + s.totalTracks + s.history.joinToString { it.mood.name }
        if (key != listKey) {
            listKey = key
            buildMoodBars(s)
            buildHistory(s)
        }
    }

    private fun buildMoodBars(s: PetState) {
        moodBars.removeAllViews()
        val max = (s.moodCounts.maxOrNull() ?: 0).coerceAtLeast(1)
        for (m in Mood.values()) {
            val n = s.moodCounts[m.ordinal]
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val label = TextView(this).apply {
                text = "${m.label}  $n"
                setTextColor(0xFFE8E4F5.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                this.max = max
                progress = n
                progressTintList = ColorStateList.valueOf(m.body)
                progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(label)
            row.addView(bar)
            moodBars.addView(row)
        }
    }

    private fun buildHistory(s: PetState) {
        historyList.removeAllViews()
        if (s.history.isEmpty()) {
            historyList.addView(TextView(this).apply {
                text = "Aucun morceau pour l'instant."
                setTextColor(0xFF9A93B8.toInt())
            })
            return
        }
        for (h in s.history) {
            val sb = SpannableStringBuilder()
            val dotStart = sb.length
            sb.append("● ")
            sb.setSpan(ForegroundColorSpan(h.mood.body), dotStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(h.title)
            sb.append("\n")
            val subStart = sb.length
            sb.append("${h.artist} · ${h.mood.label} · ${timeFmt.format(Date(h.ts))}")
            sb.setSpan(ForegroundColorSpan(0xFF9A93B8.toInt()), subStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.85f), subStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val tv = TextView(this).apply {
                text = sb
                setTextColor(0xFFE8E4F5.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener { chooseMood(h.artist) }
            }
            historyList.addView(tv)
        }
    }

    private fun chooseMood(artist: String) {
        val moods = Mood.values()
        AlertDialog.Builder(this)
            .setTitle("Quelle ambiance pour ${MoodResolver.primaryArtist(artist)} ?")
            .setItems(moods.map { it.label }.toTypedArray()) { _, which ->
                val m = moods[which]
                MoodResolver.setOverride(this, artist, m)
                PetEngine.edit(this) { it.reassign(artist, m) }
                PetWidgetProvider.updateAll(this)
                listKey = ""
                refresh()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun renameDialog() {
        val input = EditText(this).apply {
            setText(PetEngine.load(this@MainActivity).name)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Nom de ta créature")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val n = input.text.toString().trim().take(16)
                if (n.isNotEmpty()) {
                    PetEngine.edit(this) { it.name = n }
                    PetWidgetProvider.updateAll(this)
                    refresh()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun pinWidget() {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, PetWidgetProvider::class.java), null, null)
        } else {
            Toast.makeText(
                this,
                "Appui long sur l'écran d'accueil → Widgets → Mélopet",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
