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

    // --- Activités et compétences
    var occupation: Occupation? = null
    val stats = FloatArray(Stat.values().size)
    /** Début du segment d'écoute pas encore comptabilisé (0 = pas de musique). */
    var segStart = 0L
    /** Affinité du morceau en cours avec chaque activité (0..1). */
    var curAff = FloatArray(Occupation.values().size) { 0.3f }
    var trackMaxMin = 10f
    var trackCreditedMin = 0f
    var activityMinutes = 0f

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
        playing && occupation != null -> Expression.CONCENTRE
        playing -> Expression.DANSE
        isNight(now) -> Expression.DORT
        hunger < 20f -> Expression.AFFAME
        energy < 15f -> Expression.FATIGUE
        joy < 25f -> Expression.GRINCHEUX
        else -> Expression.CONTENT
    }

    /** Fait passer le temps : faim et joie baissent, l'énergie remonte au repos. */
    fun tick(now: Long) {
        credit(now)
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

    /** Multiplicateur de gains entre l'activité choisie et le morceau en cours. */
    fun synergy(): Float {
        val o = occupation ?: return 1f
        return Occupation.multiplier(curAff[o.ordinal])
    }

    /**
     * Comptabilise les minutes de musique écoutées depuis le dernier passage :
     * l'activité en cours rapporte des points de compétence au rythme de la musique.
     */
    fun credit(now: Long) {
        if (!playing || segStart == 0L) {
            segStart = if (playing) now else 0L
            return
        }
        var minutes = ((now - segStart) / 60_000f).coerceAtLeast(0f)
        segStart = now
        // Jamais plus que la durée du morceau (évite les gains fantômes si une pause a été ratée).
        minutes = minutes.coerceAtMost((trackMaxMin - trackCreditedMin).coerceAtLeast(0f))
        trackCreditedMin += minutes
        val o = occupation ?: return
        if (stage == Stage.OEUF || minutes <= 0f) return
        val gain = minutes * synergy()
        for (st in Stat.values()) stats[st.ordinal] += gain * o.weight(st)
        energy -= o.energyPerMin * minutes
        hunger -= o.hungerPerMin * minutes
        activityMinutes += minutes
        clamp()
        if (o.energyPerMin > 0f && energy < 8f) {
            occupation = null
            lastEvent = "$name est épuisé et arrête de ${o.verb}. Laisse-le se reposer ou méditer."
        }
    }

    /** @return false si la créature refuse (œuf, trop fatiguée). */
    fun setOccupation(o: Occupation?, now: Long): Boolean {
        tick(now)
        if (o != null && stage == Stage.OEUF) {
            lastEvent = "Un œuf ne peut pas encore ${o.verb} !"
            return false
        }
        if (o != null && o.energyPerMin > 0f && energy < 15f) {
            lastEvent = "$name est trop fatigué pour ${o.verb}."
            return false
        }
        occupation = o
        lastEvent = when {
            o == null -> "$name se repose."
            playing -> "$name commence à ${o.verb} : synergie ×${fmt(synergy())} (${Occupation.synergyLabel(synergy())})."
            else -> "$name est prêt à ${o.verb}. Lance de la musique !"
        }
        return true
    }

    /** Un nouveau morceau démarre : la musique nourrit la créature. */
    fun onTrack(title: String, artist: String, info: MoodResolver.TrackInfo, durationMs: Long, now: Long) {
        tick(now)
        val mood = info.mood
        curAff = info.affinity.copyOf()
        trackMaxMin = if (durationMs > 0) durationMs / 60_000f + 0.5f else 10f
        trackCreditedMin = 0f
        segStart = now
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
            occupation != null -> {
                val o = occupation!!
                val m = synergy()
                "${o.emoji} ${o.label} sur ce morceau : synergie ×${fmt(m)} (${Occupation.synergyLabel(m)})."
            }
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
        o.put("occupation", occupation?.name ?: "")
        val st = JSONObject()
        for (x in Stat.values()) st.put(x.name, stats[x.ordinal].toDouble())
        o.put("stats", st)
        o.put("segStart", segStart)
        o.put("curAff", JSONArray().apply { curAff.forEach { put(it.toDouble()) } })
        o.put("trackMaxMin", trackMaxMin.toDouble())
        o.put("trackCreditedMin", trackCreditedMin.toDouble())
        o.put("activityMinutes", activityMinutes.toDouble())
        return o.toString()
    }

    companion object {
        fun fmt(f: Float): String = String.format(java.util.Locale.FRANCE, "%.1f", f)

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
                s.occupation = Occupation.values().firstOrNull { it.name == o.optString("occupation") }
                o.optJSONObject("stats")?.let { st ->
                    for (x in Stat.values()) s.stats[x.ordinal] = st.optDouble(x.name, 0.0).toFloat()
                }
                s.segStart = o.optLong("segStart", 0L)
                o.optJSONArray("curAff")?.let { a ->
                    for (i in 0 until minOf(a.length(), s.curAff.size)) s.curAff[i] = a.optDouble(i, 0.3).toFloat()
                }
                s.trackMaxMin = o.optDouble("trackMaxMin", 10.0).toFloat()
                s.trackCreditedMin = o.optDouble("trackCreditedMin", 0.0).toFloat()
                s.activityMinutes = o.optDouble("activityMinutes", 0.0).toFloat()
            } catch (e: Exception) {
                // État corrompu : on repart d'un œuf plutôt que de planter.
            }
            return s
        }
    }
}
