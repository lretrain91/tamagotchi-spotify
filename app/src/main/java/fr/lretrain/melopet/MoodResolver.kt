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

    /** Résultat de l'analyse d'un artiste : ambiance + affinité 0..1 avec chaque activité. */
    class TrackInfo(val mood: Mood, val affinity: FloatArray)

    // Genres qui conviennent à chaque activité (index = Occupation.ordinal).
    private val ACTIVITY_WORDS: List<List<String>> = listOf(
        // Miner
        listOf(
            "metal", "rock", "punk", "hardcore", "industrial", "grunge", "drum and bass", "dnb",
            "techno", "hardstyle", "rap", "trap", "drill", "stoner", "thrash", "work song", "hard"
        ),
        // Étudier
        listOf(
            "classical", "lo-fi", "lofi", "jazz", "piano", "instrumental", "post-rock", "baroque",
            "soundtrack", "score", "chillhop", "minimal", "study", "math rock", "idm", "bossa", "modern classical"
        ),
        // Méditer
        listOf(
            "ambient", "new age", "drone", "meditation", "dream pop", "shoegaze", "downtempo", "chant",
            "world", "indian", "raga", "healing", "nature", "sleep", "gregorian", "space music"
        )
    )

    /** Affinités par défaut selon l'ambiance, quand les genres ne disent rien. */
    fun moodAffinity(m: Mood): FloatArray = when (m) {
        Mood.ENERGIQUE -> floatArrayOf(0.9f, 0.1f, 0f)
        Mood.URBAIN -> floatArrayOf(0.7f, 0.2f, 0.1f)
        Mood.FESTIF -> floatArrayOf(0.5f, 0.2f, 0.1f)
        Mood.CHILL -> floatArrayOf(0.1f, 0.7f, 0.6f)
        Mood.SOMBRE -> floatArrayOf(0.3f, 0.4f, 0.5f)
        else -> floatArrayOf(0.3f, 0.3f, 0.3f)
    }

    /** À appeler hors du thread principal (accès réseau). */
    fun resolve(ctx: Context, artist: String): TrackInfo {
        val forced = override(ctx, artist)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "c2:" + artistKey(artist)
        var info = prefs.getString(key, null)?.let { decode(it) }
        if (info == null) {
            val tags = fetchTags(primaryArtist(artist))
            if (tags != null) {
                info = analyse(tags)
                prefs.edit().putString(key, encode(info)).apply()
            }
        }
        if (forced != null) {
            // Ambiance corrigée par l'utilisateur ; on garde les affinités des genres si on les a.
            return TrackInfo(forced, info?.affinity ?: moodAffinity(forced))
        }
        return info ?: TrackInfo(Mood.NEUTRE, moodAffinity(Mood.NEUTRE)) // réseau KO : on réessaiera
    }

    fun analyse(tags: List<Pair<String, Int>>): TrackInfo {
        val mood = classify(tags)
        val score = FloatArray(Occupation.values().size)
        for ((tag, count) in tags) {
            val t = tag.lowercase()
            for (i in ACTIVITY_WORDS.indices) {
                if (ACTIVITY_WORDS[i].any { t.contains(it) }) score[i] += count.coerceAtLeast(1).toFloat()
            }
        }
        val best = score.maxOrNull() ?: 0f
        val fallback = moodAffinity(mood)
        val aff = FloatArray(score.size) { i ->
            if (best > 0f) 0.7f * (score[i] / best) + 0.3f * fallback[i] else fallback[i]
        }
        return TrackInfo(mood, aff)
    }

    private fun encode(info: TrackInfo): String =
        info.mood.name + ";" + info.affinity.joinToString(";")

    private fun decode(s: String): TrackInfo? {
        val parts = s.split(";")
        val mood = Mood.values().firstOrNull { it.name == parts.getOrNull(0) } ?: return null
        val aff = FloatArray(Occupation.values().size) { i ->
            parts.getOrNull(i + 1)?.toFloatOrNull() ?: moodAffinity(mood)[i]
        }
        return TrackInfo(mood, aff)
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
