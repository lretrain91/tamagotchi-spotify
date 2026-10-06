package fr.lretrain.melopet;

/** Ce que la créature ressent en ce moment (détermine son visage). */
public enum Expression {
    CONTENT("Content"),
    DANSE("Danse"),
    CONCENTRE("Concentré"),
    DORT("Dort"),
    FATIGUE("Fatigué"),
    AFFAME("Affamé"),
    GRINCHEUX("Grincheux"),
    MALADE("Malade");

    public final String label;

    Expression(String label) {
        this.label = label;
    }
}
