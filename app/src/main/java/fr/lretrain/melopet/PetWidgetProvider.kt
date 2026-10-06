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

        fun updateAll(context: Context) {
            val ctx = context.applicationContext
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PetWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val now = System.currentTimeMillis()
            val s = PetEngine.load(ctx).also { it.tick(now) }
            val expr = s.expression(now)

            val views = RemoteViews(ctx.packageName, R.layout.widget_pet)
            views.setImageViewBitmap(R.id.frame0, PetEngine.bitmap(s, 0, now))
            views.setImageViewBitmap(R.id.frame1, PetEngine.bitmap(s, 1, now))
            views.setTextViewText(R.id.w_name, "${s.name} · ${s.stage.label}")
            views.setTextViewText(R.id.w_mood, "${expr.label} · ambiance ${s.currentMood.label.lowercase()}")
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

            mgr.updateAppWidget(ids, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PetEngine.edit(context) { it.tick(System.currentTimeMillis()) }
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_PET) {
            PetEngine.edit(context) { it.pet(System.currentTimeMillis()) }
            updateAll(context)
        }
    }
}
