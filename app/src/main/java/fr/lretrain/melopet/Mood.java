package fr.lretrain.melopet;

/**
 * Ambiance musicale. Chaque ambiance a sa palette : le corps de la créature
 * prend les couleurs de son ambiance dominante, le ciel celles du morceau en cours.
 */
public enum Mood {
    //        label        body        light       shade       accent      sky1        sky2
    NEUTRE("Curieux",   0xFFC9C3E6, 0xFFEEEAFB, 0xFF9189C0, 0xFFFFD166, 0xFF23243A, 0xFF34365A),
    ENERGIQUE("Énergique", 0xFFFF6B4A, 0xFFFFB199, 0xFFC7402A, 0xFFFFE14D, 0xFF3A0F12, 0xFF5C1A1A),
    FESTIF("Festif",    0xFFFF8FC7, 0xFFFFD1E8, 0xFFD35E9C, 0xFF7CF7FF, 0xFF2A1040, 0xFF4A1E6B),
    CHILL("Chill",      0xFF8FD3C7, 0xFFD2F4EE, 0xFF5AA79B, 0xFFF79A72, 0xFF12303A, 0xFF1E4D5C),
    SOMBRE("Sombre",    0xFF6B5A8E, 0xFF9C8BC0, 0xFF3E3160, 0xFFFF3D6E, 0xFF0D0A14, 0xFF1C1428),
    URBAIN("Urbain",    0xFFF2B544, 0xFFFFE0A0, 0xFFB98118, 0xFF2EC4B6, 0xFF141B2D, 0xFF22304A);

    public final String label;
    public final int body, light, shade, accent, sky1, sky2;

    Mood(String label, int body, int light, int shade, int accent, int sky1, int sky2) {
        this.label = label;
        this.body = body;
        this.light = light;
        this.shade = shade;
        this.accent = accent;
        this.sky1 = sky1;
        this.sky2 = sky2;
    }
}
