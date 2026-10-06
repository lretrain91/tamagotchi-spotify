package fr.lretrain.melopet

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Devine l'ambiance d'un artiste à partir de ses genres sur MusicBrainz
 * (base musicale libre, sans clé). Résultats mis en cache par artiste ;
 * l'utilisateur peut corriger une ambiance depuis l'historique.
 */
object MoodResolver {
    private const val PREFS = "moods"
    private const val UA = "Melopet/0.1 ( https://github.com/lretrain91/tamagotchi-spotify )"

    // Ordre = priorité quand un genre correspond à plusieurs ambiances.
    private val RULES: List<Pair<Mood, List<String>>> = listOf(
        Mood.SOMBRE to listOf(
            "goth", "dark", "doom", "post-punk", "emo", "black metal", "industrial",
            "coldwave", "shoegaze", "sad", "witch house", "dungeon synth"
        ),
        Mood.URBAIN to listOf(
            "hip hop", "hip-hop", "rap", "trap", "drill", "r&b", "rnb", "grime",
            "afrobeat", "afro", "dancehall", "boom bap"
        ),
        Mood.ENERGIQUE to listOf(
            "metal", "rock", "punk", "hardcore", "grunge", "drum and bass", "dnb",
            "hardstyle", "garage", "noise", "thrash"
        ),
        Mood.FESTIF to listOf(
            "pop", "dance", "disco", "house", "edm", "electro", "funk", "reggaeton",
            "latin", "eurodance", "variété", "k-pop", "techno", "club", "schlager"
        ),
        Mood.CHILL to listOf(
            "lo-fi", "lofi", "ambient", "jazz", "folk", "acoustic", "classical", "chill",
            "soul", "bossa", "singer-songwriter", "downtempo", "chanson", "piano",
            "new age", "reggae", "blues", "trip hop", "dream pop"
        )
    )

    fun artistKey(artist: String): String = primaryArtist(artist).lowercase()

    /** Spotify sépare les artistes multiples par des virgules. */
    fun primaryArtist(artist: String): String {
        var a = artist
        for (sep in listOf(", ", " feat. ", " feat ", " ft. ")) {
            val i = a.indexOf(sep, ignoreCase = true)
            if (i > 0) a = a.substring(0, i)
        }
        return a.trim()
    }

    fun override(ctx: Context, artist: String): Mood? {
        val v = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("o:" + artistKey(artist), null)
        return Mood.values().firstOrNull { it.name == v }
    }

    fun setOverride(ctx: Context, artist: String, mood: Mood) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("o:" + artistKey(artist), mood.name).apply()
    }

    /** À appeler hors du thread principal (accès réseau). */
    fun resolve(ctx: Context, artist: String): Mood {
        override(ctx, artist)?.let { return it }
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "c:" + artistKey(artist)
        prefs.getString(key, null)?.let { cached ->
            Mood.values().firstOrNull { it.name == cached }?.let { return it }
        }
        val tags = fetchTags(primaryArtist(artist)) ?: return Mood.NEUTRE // réseau KO : on réessaiera
        val mood = classify(tags)
        prefs.edit().putString(key, mood.name).apply()
        return mood
    }

    fun classify(tags: List<Pair<String, Int>>): Mood {
        val score = IntArray(Mood.values().size)
        for ((tag, count) in tags) {
            val t = tag.lowercase()
            val match = RULES.firstOrNull { (_, words) -> words.any { t.contains(it) } } ?: continue
            score[match.first.ordinal] += count.coerceAtLeast(1)
        }
        var best = Mood.NEUTRE
        var bestScore = 0
        for (m in Mood.values()) {
            if (score[m.ordinal] > bestScore) {
                best = m
                bestScore = score[m.ordinal]
            }
        }
        return best
    }

    private fun fetchTags(name: String): List<Pair<String, Int>>? {
        if (name.isBlank()) return emptyList()
        var conn: HttpURLConnection? = null
        return try {
            val q = URLEncoder.encode("artist:\"" + name.replace("\"", "") + "\"", "UTF-8")
            val url = URL("https://musicbrainz.org/ws/2/artist/?query=$q&fmt=json&limit=3")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val artists = JSONObject(body).optJSONArray("artists") ?: return emptyList()
            val result = mutableListOf<Pair<String, Int>>()
            // On prend le premier artiste qui a des genres renseignés.
            for (i in 0 until artists.length()) {
                val a = artists.getJSONObject(i)
                if (a.optInt("score", 0) < 80) continue
                val tags = a.optJSONArray("tags") ?: continue
                for (j in 0 until tags.length()) {
                    val t = tags.getJSONObject(j)
                    result.add(t.optString("name") to t.optInt("count", 1))
                }
                if (result.isNotEmpty()) break
            }
            result
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
