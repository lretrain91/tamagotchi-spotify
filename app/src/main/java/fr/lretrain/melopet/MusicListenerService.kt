package fr.lretrain.melopet

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

/**
 * Service système qui reste actif en fond tant que l'accès aux notifications
 * est accordé. Il ne lit pas les notifications : il s'en sert seulement pour
 * suivre la session média de Spotify et réagir à chaque changement de morceau.
 */
class MusicListenerService : NotificationListenerService() {

    companion object {
        val MUSIC_APPS = setOf(
            "com.spotify.music",
            "com.spotify.lite",
        )
    }

    private var sessionManager: MediaSessionManager? = null
    private val controllers = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list ?: emptyList())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val msm = getSystemService(MediaSessionManager::class.java) ?: return
        sessionManager = msm
        val me = ComponentName(this, MusicListenerService::class.java)
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, me)
            attach(msm.getActiveSessions(me))
        } catch (e: SecurityException) {
            // Accès aux notifications retiré entre-temps.
        }
    }

    override fun onListenerDisconnected() {
        detachAll()
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
        }
        PetEngine.onMusicStopped(this)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        detachAll()
        super.onDestroy()
    }

    private fun attach(list: List<MediaController>) {
        val wanted = list.filter { it.packageName in MUSIC_APPS }
        val tokens = wanted.map { it.sessionToken }.toSet()

        val stale = controllers.keys.filter { it !in tokens }
        for (t in stale) {
            controllers.remove(t)?.let { (c, cb) -> c.unregisterCallback(cb) }
        }
        if (wanted.isEmpty()) {
            PetEngine.onMusicStopped(this)
            return
        }
        for (c in wanted) {
            if (c.sessionToken in controllers) continue
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = handle(c)
                override fun onPlaybackStateChanged(state: PlaybackState?) = handle(c)
                override fun onSessionDestroyed() = PetEngine.onMusicStopped(this@MusicListenerService)
            }
            c.registerCallback(cb)
            controllers[c.sessionToken] = c to cb
            handle(c)
        }
    }

    private fun detachAll() {
        for ((c, cb) in controllers.values) {
            try {
                c.unregisterCallback(cb)
            } catch (e: Exception) {
            }
        }
        controllers.clear()
    }

    private fun handle(c: MediaController) {
        val md = c.metadata ?: return
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = (md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.trim().orEmpty()
        if (title.isEmpty() || artist.isEmpty()) return
        // Les publicités de Spotify gratuit ne nourrissent pas la créature.
        if (artist.equals("Spotify", ignoreCase = true) || title.equals("Advertisement", ignoreCase = true)) return
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        PetEngine.onMedia(this, title, artist, playing, duration)
    }
}
