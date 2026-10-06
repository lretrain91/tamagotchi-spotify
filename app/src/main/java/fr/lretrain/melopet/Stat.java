package fr.lretrain.melopet;

/** Compétences gagnées par les activités. */
public enum Stat {
    FORCE("Force", "💪"),
    VITALITE("Vitalité", "❤️"),
    INTELLIGENCE("Intelligence", "🧠"),
    MAGIE("Magie", "✨");

    public final String label;
    public final String emoji;

    Stat(String label, String emoji) {
        this.label = label;
        this.emoji = emoji;
    }

    /** Niveau 1 à 0 point, 2 à 10, 3 à 40, 4 à 90… */
    public static int level(float points) {
        return (int) Math.floor(Math.sqrt(Math.max(0f, points) / 10.0)) + 1;
    }

    public static float pointsForLevel(int level) {
        int l = Math.max(0, level - 1);
        return 10f * l * l;
    }
}
