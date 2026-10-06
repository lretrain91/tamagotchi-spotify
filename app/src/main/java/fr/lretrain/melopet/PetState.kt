package fr.lretrain.melopet

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class HistoryEntry(val title: String, val artist: String, var mood: Mood, val ts: Long)

/** Tout l'état de la créature, sérialisé en JSON dans les préférences. */
class PetState {
    var name = "Bip"
    var born = System.currentTimeMillis()
    var lastTick = born
    var hunger = 70f
    var energy = 80f
    var joy = 70f
    var xp = 0
    var totalTracks = 0
    val moodCounts = IntArray(Mood.values().size)
    var currentMood = Mood.NEUTRE
    var trackTitle = ""
    var trackArtist = ""
    var lastKey = ""
    var playing = false
    var adultForm: Mood? = null
    var lastPet = 0L
    var lastEvent = "Fais-lui écouter ta musique pour faire éclore l'œuf."
    val history = mutableListOf<HistoryEntry>()

    val stage: Stage get() = Stage.forXp(xp)

    /** Ambiance la plus écoutée (hors « Curieux », sauf si rien d'autre). */
    fun dominantMood(): Mood {
        var best = Mood.NEUTRE
        var bestN = 0
        for (m in Mood.values()) {
            if (m == Mood.NEUTRE) continue
            val n = moodCounts[m.ordinal]
            if (n > bestN) {
                best = m
                bestN = n
            }
        }
        return best
    }

    /** Ambiance qui donne sa forme au corps : figée une fois adulte. */
    fun form(): Mood = if (stage == Stage.ADULTE) adultForm ?: dominantMood() else dominantMood()

    fun expression(now: Long): Expression = when {
        stage == Stage.OEUF -> if (playing) Expression.DANSE else Expression.CONTENT
        hunger <= 0.5f -> Expression.MALADE
        playing -> Expression.DANSE
        isNight(now) -> Expression.DORT
        hunger < 20f -> Expression.AFFAME
        energy < 15f -> Expression.FATIGUE
        joy < 25f -> Expression.GRINCHEUX
        else -> Expression.CONTENT
    }

    /** Fait passer le temps : faim et joie baissent, l'énergie remonte au repos. */
    fun tick(now: Long) {
        val dtH = ((now - lastTick).coerceAtLeast(0L) / 3_600_000f).coerceAtMost(72f)
        lastTick = now
        if (stage == Stage.OEUF) return
        hunger -= 4f * dtH
        joy -= 2.5f * dtH
        val energyRate = when {
            playing -> -1.5f
            isNight(now) -> 12f
            else -> 6f
        }
        energy += energyRate * dtH
        clamp()
    }

    /** Un nouveau morceau démarre : la musique nourrit la créature. */
    fun onTrack(title: String, artist: String, mood: Mood, now: Long) {
        tick(now)
        val before = stage
        var streak = 0
        for (h in history) {
            if (h.artist.equals(artist, ignoreCase = true)) streak++ else break
        }
        val recent = history.take(6).any { it.artist.equals(artist, ignoreCase = true) }
        val joyGain = when {
            streak >= 4 -> -6f
            !recent -> 10f
            else -> 4f
        }
        hunger += 12f
        joy += joyGain
        energy += when (mood) {
            Mood.ENERGIQUE -> -8f
            Mood.FESTIF -> -4f
            Mood.URBAIN -> -3f
            Mood.SOMBRE -> -2f
            Mood.CHILL -> 6f
            else -> 0f
        }
        xp += 10
        totalTracks++
        moodCounts[mood.ordinal]++
        currentMood = mood
        trackTitle = title
        trackArtist = artist
        playing = true
        history.add(0, HistoryEntry(title, artist, mood, now))
        while (history.size > 30) history.removeAt(history.size - 1)
        clamp()

        val after = stage
        lastEvent = when {
            after != before && after == Stage.BEBE -> "L'œuf a éclos ! Voici $name."
            after != before && after == Stage.ADULTE -> {
                val f = dominantMood()
                adultForm = f
                "$name a atteint sa forme adulte : ${f.label} !"
            }
            after != before -> "$name a grandi : le voilà ${after.label.lowercase()} !"
            streak >= 4 -> "Encore $artist ? $name commence à s'ennuyer…"
            !recent -> "Nouvelle découverte ! $name adore."
            else -> "Ambiance ${mood.label.lowercase()} : $name se trémousse."
        }
    }

    fun pet(now: Long): Boolean {
        tick(now)
        if (now - lastPet < 10 * 60_000L) {
            lastEvent = "$name a déjà eu son câlin, reviens un peu plus tard."
            return false
        }
        lastPet = now
        joy += 8f
        clamp()
        lastEvent = if (stage == Stage.OEUF) "L'œuf frémit doucement." else "$name ronronne de bonheur."
        return true
    }

    /** Corrige l'ambiance d'un artiste dans l'historique récent et les compteurs. */
    fun reassign(artist: String, mood: Mood) {
        val key = MoodResolver.artistKey(artist)
        for (h in history) {
            if (MoodResolver.artistKey(h.artist) == key && h.mood != mood) {
                moodCounts[h.mood.ordinal] = (moodCounts[h.mood.ordinal] - 1).coerceAtLeast(0)
                moodCounts[mood.ordinal]++
                h.mood = mood
            }
        }
        if (MoodResolver.artistKey(trackArtist) == key) currentMood = mood
    }

    private fun clamp() {
        hunger = hunger.coerceIn(0f, 100f)
        energy = energy.coerceIn(0f, 100f)
        joy = joy.coerceIn(0f, 100f)
    }

    fun toJson(): String {
        val o = JSONObject()
        o.put("name", name)
        o.put("born", born)
        o.put("lastTick", lastTick)
        o.put("hunger", hunger.toDouble())
        o.put("energy", energy.toDouble())
        o.put("joy", joy.toDouble())
        o.put("xp", xp)
        o.put("totalTracks", totalTracks)
        val counts = JSONObject()
        for (m in Mood.values()) counts.put(m.name, moodCounts[m.ordinal])
        o.put("moodCounts", counts)
        o.put("currentMood", currentMood.name)
        o.put("trackTitle", trackTitle)
        o.put("trackArtist", trackArtist)
        o.put("lastKey", lastKey)
        o.put("playing", playing)
        o.put("adultForm", adultForm?.name ?: "")
        o.put("lastPet", lastPet)
        o.put("lastEvent", lastEvent)
        val arr = JSONArray()
        for (h in history) {
            arr.put(JSONObject().put("t", h.title).put("a", h.artist).put("m", h.mood.name).put("ts", h.ts))
        }
        o.put("history", arr)
        return o.toString()
    }

    companion object {
        fun isNight(now: Long): Boolean {
            val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
            return hour >= 23 || hour < 7
        }

        private fun moodOf(s: String?): Mood? = Mood.values().firstOrNull { it.name == s }

        fun fromJson(json: String?): PetState {
            val s = PetState()
            if (json.isNullOrEmpty()) return s
            try {
                val o = JSONObject(json)
                s.name = o.optString("name", s.name)
                s.born = o.optLong("born", s.born)
                s.lastTick = o.optLong("lastTick", s.lastTick)
                s.hunger = o.optDouble("hunger", 70.0).toFloat()
                s.energy = o.optDouble("energy", 80.0).toFloat()
                s.joy = o.optDouble("joy", 70.0).toFloat()
                s.xp = o.optInt("xp", 0)
                s.totalTracks = o.optInt("totalTracks", 0)
                o.optJSONObject("moodCounts")?.let { c ->
                    for (m in Mood.values()) s.moodCounts[m.ordinal] = c.optInt(m.name, 0)
                }
                s.currentMood = moodOf(o.optString("currentMood")) ?: Mood.NEUTRE
                s.trackTitle = o.optString("trackTitle", "")
                s.trackArtist = o.optString("trackArtist", "")
                s.lastKey = o.optString("lastKey", "")
                s.playing = o.optBoolean("playing", false)
                s.adultForm = moodOf(o.optString("adultForm"))
                s.lastPet = o.optLong("lastPet", 0L)
                s.lastEvent = o.optString("lastEvent", s.lastEvent)
                o.optJSONArray("history")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val h = arr.getJSONObject(i)
                        s.history.add(
                            HistoryEntry(
                                h.optString("t"), h.optString("a"),
                                moodOf(h.optString("m")) ?: Mood.NEUTRE, h.optLong("ts")
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // État corrompu : on repart d'un œuf plutôt que de planter.
            }
            return s
        }
    }
}
