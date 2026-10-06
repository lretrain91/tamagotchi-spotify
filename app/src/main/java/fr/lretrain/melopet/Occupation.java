package fr.lretrain.melopet;

/**
 * Activités que la créature pratique pendant que la musique joue.
 * Les points gagnés par minute dépendent de la synergie entre l'activité
 * et la musique écoutée.
 */
public enum Occupation {
    //       label      emoji  verbe       énergie/min  faim/min  répartition (Force, Vitalité, Intelligence, Magie)
    MINER("Miner", "⛏️", "miner", 0.6f, 0.35f, new float[]{0.7f, 0.3f, 0f, 0f},
            "rock, metal, punk, rap, techno"),
    ETUDIER("Étudier", "📚", "étudier", 0.3f, 0.2f, new float[]{0f, 0f, 0.8f, 0.2f},
            "lo-fi, jazz, classique, piano, instrumental"),
    MEDITER("Méditer", "🧘", "méditer", -0.25f, 0.1f, new float[]{0f, 0.4f, 0f, 0.6f},
            "ambient, new age, drone, dream pop");

    public final String label;
    public final String emoji;
    public final String verb;
    /** Énergie consommée par minute (négatif = l'activité repose). */
    public final float energyPerMin;
    public final float hungerPerMin;
    private final float[] weights;
    /** Genres conseillés, affichés à l'utilisateur. */
    public final String bestMusic;

    Occupation(String label, String emoji, String verb, float energyPerMin, float hungerPerMin,
             float[] weights, String bestMusic) {
        this.label = label;
        this.emoji = emoji;
        this.verb = verb;
        this.energyPerMin = energyPerMin;
        this.hungerPerMin = hungerPerMin;
        this.weights = weights;
        this.bestMusic = bestMusic;
    }

    public float weight(Stat s) {
        return weights[s.ordinal()];
    }

    /** Affinité 0..1 de la musique pour l'activité → multiplicateur ×0,5 à ×2,5. */
    public static float multiplier(float affinity) {
        float a = Math.max(0f, Math.min(1f, affinity));
        return 0.5f + 2f * a;
    }

    public static String synergyLabel(float mult) {
        if (mult >= 2.0f) return "parfait";
        if (mult >= 1.4f) return "bon";
        if (mult >= 0.9f) return "moyen";
        return "mauvais";
    }
}
