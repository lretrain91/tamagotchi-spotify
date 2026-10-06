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
    private lateinit var actStatus: TextView
    private lateinit var actButtons: Map<Occupation?, Button>
    private val skillLabels = mutableMapOf<Stat, TextView>()
    private val skillBars = mutableMapOf<Stat, ProgressBar>()
    private var tempo = 650

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
            handler.postDelayed(this, tempo.toLong())
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
        actStatus = findViewById(R.id.actStatus)
        actButtons = mapOf(
            null to findViewById<Button>(R.id.actRest),
            Occupation.MINER to findViewById<Button>(R.id.actMine),
            Occupation.ETUDIER to findViewById<Button>(R.id.actStudy),
            Occupation.MEDITER to findViewById<Button>(R.id.actMeditate)
        )
        for ((occ, btn) in actButtons) {
            btn.setOnClickListener {
                val ok = PetEngine.edit(this) { it.setOccupation(occ, System.currentTimeMillis()) }
                if (!ok) Toast.makeText(this, PetEngine.load(this).lastEvent, Toast.LENGTH_SHORT).show()
                PetWidgetProvider.updateAll(this)
                refresh()
            }
        }
        buildSkillRows()

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
        tempo = PetEngine.tempoMs(s)
        updateActivity(s)
        updateSkills(s)

        val key = s.name + s.totalTracks + s.history.joinToString { it.mood.name }
        if (key != listKey) {
            listKey = key
            buildMoodBars(s)
            buildHistory(s)
        }
    }

    private fun updateActivity(s: PetState) {
        val occ = s.occupation
        for ((o, btn) in actButtons) {
            val selected = o == occ
            btn.backgroundTintList = ColorStateList.valueOf(if (selected) 0xFFFFD166.toInt() else 0xFF1F1C30.toInt())
            btn.setTextColor(if (selected) 0xFF14121F.toInt() else 0xFFE8E4F5.toInt())
        }
        actStatus.text = if (occ == null) {
            buildString {
                append("💤 ${s.name} se repose. Choisis une activité : elle progresse à chaque minute de musique.\n")
                for (o in Occupation.values()) {
                    append("\n${o.emoji} ${o.label} → ${gainsText(o)}\n    idéal : ${o.bestMusic}")
                }
            }
        } else {
            val m = s.synergy()
            buildString {
                append("${occ.emoji} ${occ.label}")
                if (s.playing && s.trackTitle.isNotEmpty()) {
                    append(" au rythme de « ${s.trackTitle} »\n")
                    append("Synergie : ×${PetState.fmt(m)} (${Occupation.synergyLabel(m)})")
                } else {
                    append(" · en attente de musique")
                }
                append("\nGains : ${gainsText(occ)}")
                append("\nMusique idéale : ${occ.bestMusic}")
                append("\nTemps d'activité total : ${s.activityMinutes.toInt()} min")
            }
        }
    }

    private fun gainsText(o: Occupation): String =
        Stat.values().filter { o.weight(it) > 0f }
            .joinToString(", ") { "${it.emoji} ${it.label}" }

    private fun buildSkillRows() {
        val list = findViewById<LinearLayout>(R.id.skillList)
        for (st in Stat.values()) {
            val label = TextView(this).apply {
                setTextColor(0xFFE8E4F5.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(6), 0, dp(2))
            }
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progressTintList = ColorStateList.valueOf(0xFFFFD166.toInt())
                progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
            }
            list.addView(label)
            list.addView(bar)
            skillLabels[st] = label
            skillBars[st] = bar
        }
    }

    private fun updateSkills(s: PetState) {
        for (st in Stat.values()) {
            val pts = s.stats[st.ordinal]
            val lvl = Stat.level(pts)
            val from = Stat.pointsForLevel(lvl)
            val to = Stat.pointsForLevel(lvl + 1)
            skillLabels[st]?.text = "${st.emoji} ${st.label} · niv. $lvl  —  ${PetState.fmt(pts)} pts"
            skillBars[st]?.progress = (((pts - from) / (to - from)) * 100f).toInt().coerceIn(0, 100)
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
