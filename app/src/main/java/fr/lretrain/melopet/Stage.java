package fr.lretrain.melopet;

/** Stades d'évolution, débloqués par l'XP (10 XP par morceau écouté). */
public enum Stage {
    OEUF("Œuf", 0),
    BEBE("Bébé", 30),
    ADO("Ado", 300),
    ADULTE("Adulte", 1500);

    public final String label;
    public final int minXp;

    Stage(String label, int minXp) {
        this.label = label;
        this.minXp = minXp;
    }

    public static Stage forXp(int xp) {
        Stage result = OEUF;
        for (Stage s : values()) {
            if (xp >= s.minXp) result = s;
        }
        return result;
    }

    /** Stade suivant, ou null au stade adulte. */
    public Stage next() {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
    }
}
