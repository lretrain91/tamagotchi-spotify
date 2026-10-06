package fr.lretrain.melopet

import android.content.Context
import android.graphics.Bitmap
import java.util.concurrent.Executors

/** Point d'entrée unique pour lire et modifier l'état de la créature. */
object PetEngine {
    private const val PREFS = "pet"
    private const val KEY_STATE = "state"
    private val lock = Any()
    private val executor = Executors.newSingleThreadExecutor()

    fun load(ctx: Context): PetState =
        PetState.fromJson(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATE, null))

    fun <T> edit(ctx: Context, block: (PetState) -> T): T = synchronized(lock) {
        val s = load(ctx)
        val result = block(s)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STATE, s.toJson()).apply()
        result
    }

    /**
     * Appelé par l'écoute des sessions média à chaque changement
     * (morceau ou lecture/pause). Le genre est cherché en arrière-plan.
     */
    fun onMedia(context: Context, title: String, artist: String, playing: Boolean) {
        val ctx = context.applicationContext
        executor.execute {
            val now = System.currentTimeMillis()
            val key = "$title|$artist"
            val current = load(ctx)
            if (playing && key != current.lastKey) {
                val mood = MoodResolver.resolve(ctx, artist)
                edit(ctx) {
                    it.onTrack(title, artist, mood, System.currentTimeMillis())
                    it.lastKey = key
                }
            } else if (current.playing != playing) {
                edit(ctx) {
                    it.tick(now)
                    it.playing = playing
                }
            } else {
                return@execute
            }
            PetWidgetProvider.updateAll(ctx)
        }
    }

    fun onMusicStopped(context: Context) {
        val ctx = context.applicationContext
        executor.execute {
            if (!load(ctx).playing) return@execute
            edit(ctx) {
                it.tick(System.currentTimeMillis())
                it.playing = false
            }
            PetWidgetProvider.updateAll(ctx)
        }
    }

    fun bitmap(s: PetState, frame: Int, now: Long, scale: Int = 10): Bitmap {
        val px = SpriteRenderer.render(
            s.stage, s.form(), s.currentMood, s.expression(now), s.playing, s.xp, frame
        )
        val size = SpriteRenderer.N * scale
        return Bitmap.createBitmap(SpriteRenderer.scale(px, scale), size, size, Bitmap.Config.ARGB_8888)
    }
}
