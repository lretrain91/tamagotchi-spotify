package fr.lretrain.melopet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Widget d'écran d'accueil : créature animée (2 images qui alternent) + jauges. */
class PetWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PET = "fr.lretrain.melopet.ACTION_PET"
        const val ACTION_CYCLE = "fr.lretrain.melopet.ACTION_CYCLE"

        fun updateAll(context: Context) {
            val ctx = context.applicationContext
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PetWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val now = System.currentTimeMillis()
            val s = PetEngine.load(ctx).also { it.tick(now) }
            val expr = s.expression(now)

            // Trois variantes du widget : l'animation suit le tempo du style écouté.
            val layout = when (PetEngine.tempoMs(s)) {
                in 0..500 -> R.layout.widget_pet_fast
                in 501..700 -> R.layout.widget_pet
                else -> R.layout.widget_pet_slow
            }
            val views = RemoteViews(ctx.packageName, layout)
            views.setImageViewBitmap(R.id.frame0, PetEngine.bitmap(s, 0, now))
            views.setImageViewBitmap(R.id.frame1, PetEngine.bitmap(s, 1, now))
            views.setTextViewText(R.id.w_name, "${s.name} · ${s.stage.label}")
            val occ = s.occupation
            views.setTextViewText(
                R.id.w_mood,
                if (occ != null && s.playing) "${occ.emoji} ${occ.label} · synergie ×${PetState.fmt(s.synergy())}"
                else "${expr.label} · ambiance ${s.currentMood.label.lowercase()}"
            )
            views.setTextViewText(R.id.w_act, if (occ != null) "${occ.emoji} ${occ.label}" else "💤 Repos")
            views.setTextViewText(
                R.id.w_track,
                if (s.playing && s.trackTitle.isNotEmpty()) "♪ ${s.trackTitle} — ${s.trackArtist}"
                else "En pause"
            )
            views.setProgressBar(R.id.w_hunger, 100, s.hunger.toInt(), false)
            views.setProgressBar(R.id.w_energy, 100, s.energy.toInt(), false)
            views.setProgressBar(R.id.w_joy, 100, s.joy.toInt(), false)

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.flipper, open)
            views.setOnClickPendingIntent(R.id.w_info, open)

            val petIntent = Intent(ctx, PetWidgetProvider::class.java).setAction(ACTION_PET)
            val pet = PendingIntent.getBroadcast(
                ctx, 1, petIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.w_pet, pet)

            val cycle = PendingIntent.getBroadcast(
                ctx, 2, Intent(ctx, PetWidgetProvider::class.java).setAction(ACTION_CYCLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.w_act, cycle)

            mgr.updateAppWidget(ids, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PetEngine.edit(context) { it.tick(System.currentTimeMillis()) }
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PET -> {
                PetEngine.edit(context) { it.pet(System.currentTimeMillis()) }
                updateAll(context)
            }
            ACTION_CYCLE -> {
                // Repos → Miner → Étudier → Méditer → Repos
                PetEngine.edit(context) {
                    val order: List<Occupation?> = listOf(null) + Occupation.values().toList()
                    val start = order.indexOf(it.occupation)
                    // On saute les activités refusées (trop fatigué…) ; le repos est toujours accepté.
                    for (step in 1..order.size) {
                        val cand = order[(start + step) % order.size]
                        if (it.setOccupation(cand, System.currentTimeMillis())) break
                    }
                }
                updateAll(context)
            }
        }
    }
}
