package fr.lretrain.melopet;

/**
 * Dessine la créature en pixel art 32x32, sans dépendance Android
 * (renvoie un tableau de pixels ARGB), pour pouvoir être testé hors du téléphone.
 */
public final class SpriteRenderer {
    public static final int N = 32;

    // Couches logiques de la créature
    private static final int T = 0, OUT = 1, BODY = 2, LIGHT = 3, SHADE = 4, ACC = 5,
            EYE = 6, WHITE = 7, CHEEK = 8, MOUTH = 9,
            WOOD = 10, METAL = 11, ROCK = 12, ROCKD = 13, PAGE = 14, LINE = 15;

    private static final int C_OUT = 0xFF1E1A2B;
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_CHEEK = 0xFFFF7A9A;
    private static final int C_MOUTH = 0xFF7A1F3D;
    private static final int C_SWEAT = 0xFF8FD8FF;
    private static final int C_SPARK = 0xFFFFE14D;

    private static final int GROUND_Y = 29;

    private static final int[][] BAYER = {
            {0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
    private static final int[][] STARS = {
            {3, 3}, {9, 6}, {27, 3}, {21, 7}, {4, 15}, {29, 13}, {14, 2}, {18, 11}};
    private static final String[] NOTE = {".XX.", ".X.X", ".X..", "XX..", "XX.."};
    private static final String[] ZED = {"XXXX", "..X.", ".X..", "XXXX"};

    private SpriteRenderer() {
    }

    /**
     * @param form    ambiance qui définit le corps (ambiance dominante / forme adulte)
     * @param current ambiance du morceau en cours (ciel, notes)
     * @param frame   0 ou 1, pour l'animation
     * @param occ     activité en cours (null = aucune)
     */
    public static int[] render(Stage stage, Mood form, Mood current, Expression expr,
                               boolean playing, int xp, int frame, Occupation occ) {
        int[] g = new int[N * N];
        boolean egg = stage == Stage.OEUF;
        boolean acting = occ != null && playing && !egg && expr == Expression.CONCENTRE;
        boolean still = expr == Expression.DORT || expr == Expression.FATIGUE
                || expr == Expression.MALADE;

        int dx = 0, dy = 0;
        if (egg) {
            if (playing) dx = frame == 0 ? -1 : 1;
        } else if (acting) {
            if (occ == Occupation.MEDITER) dy = frame == 0 ? -2 : -3;
            else if (occ == Occupation.MINER) dy = frame == 0 ? -1 : 0;
        } else if (expr == Expression.DANSE) {
            dy = frame == 0 ? 0 : -2;
            dx = frame == 0 ? -1 : 1;
        } else if (!still) {
            dy = frame == 0 ? 0 : -1;
        }

        int cx, cy, rx, ry;
        switch (stage) {
            case OEUF:
                cx = 16; cy = 20; rx = 6; ry = 8;
                break;
            case BEBE:
                cx = 16; cy = 22; rx = 7; ry = 6;
                break;
            case ADO:
                cx = 16; cy = 20; rx = 8; ry = 7;
                break;
            default:
                cx = 16; cy = 18; rx = 10; ry = 9;
                break;
        }
        if (acting && occ == Occupation.MINER) cx -= 4; // place pour la pioche et le rocher
        int baseCx = cx;
        cx += dx;
        cy += dy;
        boolean grown = stage == Stage.ADO || stage == Stage.ADULTE;

        // Pieds et bras (dessinés avant le corps, qui les recouvre en partie)
        if (grown) {
            int fy = cy + ry + 1;
            int half = rx / 2;
            for (int x = -1; x <= 1; x++) {
                set(g, cx - half + x, fy, SHADE);
                set(g, cx + half + x, fy, SHADE);
                set(g, cx - half + x, fy - 1, SHADE);
                set(g, cx + half + x, fy - 1, SHADE);
            }
            int ay = expr == Expression.DANSE ? cy - 3 : cy + 2;
            int ayRight = ay;
            if (acting && occ == Occupation.MINER) ayRight = frame == 0 ? cy - 1 : cy + 2;
            if (acting && occ == Occupation.ETUDIER) { ay = cy + 3; ayRight = cy + 3; }
            ellipse(g, cx - rx - 1, ay, 1, 2, BODY);
            ellipse(g, cx + rx + 1, ayRight, 1, 2, BODY);
        }

        // Corps
        for (int y = cy - ry - 1; y <= cy + ry + 1; y++) {
            for (int x = cx - rx - 1; x <= cx + rx + 1; x++) {
                double erx = (egg && y < cy) ? rx * 0.8 : rx;
                double u = (x - cx) / (erx + 0.5);
                double v = (y - cy) / (ry + 0.5);
                double d = u * u + v * v;
                if (d > 1.0) continue;
                int c = BODY;
                if (u * 0.55 + v * 0.85 > 0.55 && d > 0.45) c = SHADE;
                double hu = u + 0.42, hv = v + 0.48;
                if (hu * hu + hv * hv < 0.035) c = LIGHT;
                set(g, x, y, c);
            }
        }

        // Ventre clair
        if (grown) {
            int bcy = cy + (int) Math.round(ry * 0.45);
            double brx = rx * 0.5, bry = ry * 0.38;
            for (int y = bcy - ry; y <= bcy + ry; y++) {
                for (int x = cx - rx; x <= cx + rx; x++) {
                    double u = (x - cx) / (brx + 0.5), v = (y - bcy) / (bry + 0.5);
                    int cur = get(g, x, y);
                    if (u * u + v * v <= 1.0 && (cur == BODY || cur == SHADE)) set(g, x, y, LIGHT);
                }
            }
        }

        if (egg) {
            drawEggDetails(g, cx, cy, xp);
        } else {
            if (grown) drawAccessory(g, form, stage, cx, cy, rx, ry, frame, still);
            drawFace(g, stage, form, expr, cx, cy, acting ? occ : null);
            if (acting && occ == Occupation.MINER) drawMining(g, cx, cy, rx, frame);
            if (acting && occ == Occupation.ETUDIER) drawBook(g, stage, cx, cy, frame);
        }

        // Contour automatique
        int[] outlined = g.clone();
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                if (g[y * N + x] != T) continue;
                if (get(g, x - 1, y) != T || get(g, x + 1, y) != T
                        || get(g, x, y - 1) != T || get(g, x, y + 1) != T) {
                    outlined[y * N + x] = OUT;
                }
            }
        }
        g = outlined;

        // Composition : ciel, sol, ombre, créature, effets
        int[] out = new int[N * N];
        int sky1 = current.sky1, sky2 = current.sky2;
        int groundTop = mix(sky2, current.accent, 0.3);
        int ground = mix(sky2, 0xFF000000, 0.35);
        int shadow = mix(ground, 0xFF000000, 0.45);
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int c;
                if (y < GROUND_Y) {
                    int level = (int) (y / (double) (GROUND_Y - 1) * 16.0);
                    c = level > BAYER[y & 3][x & 3] ? sky2 : sky1;
                } else if (y == GROUND_Y) {
                    c = groundTop;
                } else {
                    c = ((x + y) % 7 == 0) ? groundTop : ground;
                }
                out[y * N + x] = c;
            }
        }
        for (int i = 0; i < STARS.length; i++) {
            boolean bright = (i + frame) % 3 == 0;
            int sc = mix(sky1, C_WHITE, bright ? 0.9 : 0.4);
            out[STARS[i][1] * N + STARS[i][0]] = sc;
        }
        int sw = rx - 1 - (dy < 0 ? 1 : 0) - (acting && occ == Occupation.MEDITER ? 2 : 0);
        for (int x = baseCx - sw; x <= baseCx + sw; x++) putPx(out, x, GROUND_Y, shadow);
        for (int x = baseCx - sw + 2; x <= baseCx + sw - 2; x++) putPx(out, x, GROUND_Y + 1, shadow);

        for (int i = 0; i < N * N; i++) {
            if (g[i] != T) out[i] = color(g[i], form);
        }

        // Effets par-dessus
        int fx = mix(current.accent, C_WHITE, 0.2);
        if (playing && !acting) {
            if (frame == 0) {
                glyph(out, NOTE, 1, 10, fx);
                glyph(out, NOTE, 27, 3, fx);
            } else {
                glyph(out, NOTE, 2, 7, fx);
                glyph(out, NOTE, 26, 6, fx);
            }
        }
        if (expr == Expression.DORT && !egg) {
            int zc = mix(C_WHITE, sky1, 0.15);
            glyph(out, ZED, 23, 5 - frame, zc);
            glyph(out, ZED, 27, 1 - frame + 1, zc);
        }
        if (acting) drawActivityEffects(out, occ, current, cx, cy, rx, ry, frame);
        if (expr == Expression.MALADE && !egg) {
            int sx = cx + rx - 2, sy = cy - ry + 2 + frame;
            putPx(out, sx, sy, C_SWEAT);
            putPx(out, sx, sy + 1, C_SWEAT);
            putPx(out, sx - 1, sy + 1, C_SWEAT);
        }
        return out;
    }

    /** Agrandit l'image en blocs de s×s pixels (pixel art net). */
    public static int[] scale(int[] src, int s) {
        int w = N * s;
        int[] o = new int[w * w];
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int c = src[y * N + x];
                for (int yy = 0; yy < s; yy++) {
                    int row = (y * s + yy) * w + x * s;
                    for (int xx = 0; xx < s; xx++) o[row + xx] = c;
                }
            }
        }
        return o;
    }

    // ---------------------------------------------------------------- détails

    private static void drawEggDetails(int[] g, int cx, int cy, int xp) {
        int[][] spots = {{-3, -2}, {-2, -2}, {-3, -1}, {2, 2}, {3, 2}, {2, 3}, {0, -5}, {1, -5}, {-1, 5}};
        for (int[] p : spots) {
            if (get(g, cx + p[0], cy + p[1]) != T) set(g, cx + p[0], cy + p[1], ACC);
        }
        if (xp >= 20) {
            // L'œuf se fissure juste avant d'éclore
            for (int i = -4; i <= 4; i++) {
                int y = cy - 1 + ((i & 1) == 0 ? 0 : 1);
                if (get(g, cx + i, y) != T) set(g, cx + i, y, OUT);
            }
        }
    }

    private static void drawAccessory(int[] g, Mood form, Stage stage, int cx, int cy,
                                      int rx, int ry, int frame, boolean still) {
        int top = cy - ry;
        switch (form) {
            case ENERGIQUE: { // crête de piques
                int off = rx / 2 - 1;
                int[][] spikes = {{-off, 3}, {0, 4}, {off, 3}};
                for (int[] sp : spikes) {
                    int c = cx + sp[0], h = sp[1];
                    for (int i = 1; i <= h; i++) {
                        int w = (h - i) / 2;
                        for (int x = c - w; x <= c + w; x++) set(g, x, top - i + 1, ACC);
                    }
                }
                break;
            }
            case FESTIF: { // chapeau de fête
                for (int i = 0; i < 7; i++) {
                    int w = 3 - (i * 3) / 6;
                    int col = (i % 2 == 0) ? ACC : WHITE;
                    for (int x = cx - w; x <= cx + w; x++) set(g, x, top - i, col);
                }
                set(g, cx - 1, top - 7, WHITE);
                set(g, cx, top - 7, WHITE);
                set(g, cx + 1, top - 7, WHITE);
                set(g, cx, top - 8, WHITE);
                break;
            }
            case CHILL: { // casque audio
                for (int y = top - 3; y <= cy - 2; y++) {
                    for (int x = cx - rx - 2; x <= cx + rx + 2; x++) {
                        double u = (x - cx) / (rx + 1.0), v = (y - cy) / (ry + 1.5);
                        double d = u * u + v * v;
                        if (d >= 1.0 && d < 1.25) set(g, x, y, ACC);
                    }
                }
                for (int y = cy - 2; y <= cy + 1; y++) {
                    for (int x = 0; x <= 2; x++) {
                        set(g, cx - rx - 2 + x, y, ACC);
                        set(g, cx + rx + 2 - x, y, ACC);
                    }
                }
                break;
            }
            case SOMBRE: { // petites cornes
                int hx = rx / 2 + 1;
                int[][] horn = {{0, 0}, {1, 0}, {0, -1}, {1, -1}, {-1, -2}, {0, -2}, {-1, -3}, {-2, -4}};
                int lb = cx - hx, rb = cx + hx;
                int by = topAt(g, lb, cy) + 1;
                for (int[] p : horn) {
                    set(g, lb + p[0], by + p[1], WHITE);
                    set(g, rb - p[0], by + p[1], WHITE);
                }
                break;
            }
            case URBAIN: { // casquette visière de côté
                int capRows = stage == Stage.ADULTE ? 3 : 2;
                for (int y = top - 1; y <= top + capRows; y++) {
                    for (int x = cx - rx - 1; x <= cx + rx + 1; x++) {
                        boolean onBody = get(g, x, y) != T;
                        boolean dome = y == top - 1 && Math.abs(x - cx) <= rx / 2 - 1;
                        if (onBody || dome) set(g, x, y, ACC);
                    }
                }
                for (int x = cx - 2; x <= cx + rx + 3; x++) set(g, x, top + capRows, ACC);
                set(g, cx, top - 2, ACC);
                break;
            }
            default: { // antenne
                int bx = (frame == 1 && !still) ? 1 : 0;
                set(g, cx, top - 1, SHADE);
                set(g, cx, top - 2, SHADE);
                set(g, cx + bx, top - 3, SHADE);
                for (int x = -1; x <= 1; x++) {
                    set(g, cx + bx + x, top - 5, ACC);
                    set(g, cx + bx + x, top - 4, ACC);
                }
                set(g, cx + bx, top - 6, ACC);
                break;
            }
        }
    }

    private static void drawFace(int[] g, Stage stage, Mood form, Expression expr, int cx, int cy,
                                 Occupation occ) {
        boolean baby = stage == Stage.BEBE;
        int s = baby ? 2 : (stage == Stage.ADO ? 3 : 4);
        int eh = baby ? 2 : 3;
        int ey = cy - (baby ? 1 : (stage == Stage.ADO ? 2 : 3));
        int my = ey + eh + 1;
        int eye = (form == Mood.SOMBRE && !baby) ? ACC : EYE;
        boolean shades = form == Mood.URBAIN && !baby
                && (expr == Expression.CONTENT || expr == Expression.DANSE);

        if (expr == Expression.CONCENTRE && occ != null) {
            switch (occ) {
                case MINER: // regard déterminé
                    for (int r = 1; r < eh; r++) {
                        set(g, cx - s - 1, ey + r, eye);
                        set(g, cx - s, ey + r, eye);
                        set(g, cx + s, ey + r, eye);
                        set(g, cx + s + 1, ey + r, eye);
                    }
                    set(g, cx - s - 2, ey - 1, EYE);
                    set(g, cx - s - 1, ey, EYE);
                    set(g, cx + s + 2, ey - 1, EYE);
                    set(g, cx + s + 1, ey, EYE);
                    set(g, cx - 1, my, EYE);
                    set(g, cx, my, EYE);
                    set(g, cx + 1, my, EYE);
                    return;
                case ETUDIER: // yeux baissés sur le livre
                    openEyes(g, cx, ey + 1, s, Math.max(1, eh - 1), eye);
                    set(g, cx, my, EYE);
                    return;
                default: // méditation : yeux fermés, petit sourire
                    int ly = ey + eh - 1;
                    set(g, cx - s - 1, ly, EYE);
                    set(g, cx - s, ly, EYE);
                    set(g, cx + s, ly, EYE);
                    set(g, cx + s + 1, ly, EYE);
                    set(g, cx - 1, my, EYE);
                    set(g, cx, my + 1, EYE);
                    set(g, cx + 1, my, EYE);
                    cheeks(g, cx, ey + eh, s);
                    return;
            }
        }
        switch (expr) {
            case CONCENTRE: // sans activité : visage content
            case DANSE:
                if (shades) {
                    sunglasses(g, cx, ey, s);
                } else {
                    caret(g, cx - s - 1, ey, eye);
                    caret(g, cx + s + 1, ey, eye);
                }
                set(g, cx - 1, my, EYE);
                set(g, cx, my, EYE);
                set(g, cx + 1, my, EYE);
                set(g, cx - 1, my + 1, EYE);
                set(g, cx, my + 1, MOUTH);
                set(g, cx + 1, my + 1, EYE);
                set(g, cx, my + 2, EYE);
                cheeks(g, cx, ey + eh, s);
                break;
            case CONTENT:
                if (shades) sunglasses(g, cx, ey, s);
                else openEyes(g, cx, ey, s, eh, eye);
                set(g, cx - 2, my, EYE);
                set(g, cx - 1, my + 1, EYE);
                set(g, cx, my + 1, EYE);
                set(g, cx + 1, my + 1, EYE);
                set(g, cx + 2, my, EYE);
                cheeks(g, cx, ey + eh, s);
                break;
            case DORT:
            case FATIGUE: {
                int ly = ey + eh - 1;
                set(g, cx - s - 1, ly, EYE);
                set(g, cx - s, ly, EYE);
                set(g, cx + s, ly, EYE);
                set(g, cx + s + 1, ly, EYE);
                if (expr == Expression.FATIGUE) {
                    set(g, cx - s - 2, ly - 1, EYE);
                    set(g, cx + s + 2, ly - 1, EYE);
                    set(g, cx - 1, my, EYE);
                    set(g, cx, my, EYE);
                    set(g, cx + 1, my, EYE);
                } else {
                    set(g, cx, my, MOUTH);
                }
                break;
            }
            case AFFAME:
                openEyes(g, cx, ey + 1, s, eh - 1, eye);
                set(g, cx - 2, my + 1, EYE);
                set(g, cx - 1, my, EYE);
                set(g, cx, my, EYE);
                set(g, cx + 1, my, EYE);
                set(g, cx + 2, my + 1, EYE);
                set(g, cx + 1, my + 1, WHITE); // filet de bave
                break;
            case GRINCHEUX:
                for (int r = 1; r < eh; r++) {
                    set(g, cx - s - 1, ey + r, eye);
                    set(g, cx - s, ey + r, eye);
                    set(g, cx + s, ey + r, eye);
                    set(g, cx + s + 1, ey + r, eye);
                }
                set(g, cx - s - 2, ey - 2, EYE);
                set(g, cx - s - 1, ey - 1, EYE);
                set(g, cx - s, ey, EYE);
                set(g, cx + s + 2, ey - 2, EYE);
                set(g, cx + s + 1, ey - 1, EYE);
                set(g, cx + s, ey, EYE);
                set(g, cx - 1, my + 1, EYE);
                set(g, cx, my + 1, EYE);
                set(g, cx + 1, my + 1, EYE);
                break;
            case MALADE:
                cross(g, cx - s - 1, ey + 1);
                cross(g, cx + s + 1, ey + 1);
                set(g, cx - 2, my + 1, EYE);
                set(g, cx - 1, my, EYE);
                set(g, cx, my + 1, EYE);
                set(g, cx + 1, my, EYE);
                set(g, cx + 2, my + 1, EYE);
                break;
        }
    }

    private static void openEyes(int[] g, int cx, int ey, int s, int eh, int eye) {
        for (int r = 0; r < eh; r++) {
            set(g, cx - s - 1, ey + r, eye);
            set(g, cx - s, ey + r, eye);
            set(g, cx + s, ey + r, eye);
            set(g, cx + s + 1, ey + r, eye);
        }
        set(g, cx - s - 1, ey, WHITE);
        set(g, cx + s, ey, WHITE);
    }

    private static void caret(int[] g, int px, int ey, int eye) {
        set(g, px - 1, ey + 1, eye);
        set(g, px, ey, eye);
        set(g, px + 1, ey + 1, eye);
    }

    private static void cross(int[] g, int px, int py) {
        set(g, px, py, EYE);
        set(g, px - 1, py - 1, EYE);
        set(g, px + 1, py - 1, EYE);
        set(g, px - 1, py + 1, EYE);
        set(g, px + 1, py + 1, EYE);
    }

    private static void sunglasses(int[] g, int cx, int ey, int s) {
        for (int y = ey; y <= ey + 1; y++) {
            for (int x = 0; x <= 2; x++) {
                set(g, cx - s - 2 + x, y, EYE);
                set(g, cx + s + x, y, EYE);
            }
        }
        for (int x = cx - s + 1; x <= cx + s - 1; x++) set(g, x, ey, EYE);
        set(g, cx - s - 2, ey, WHITE);
        set(g, cx + s, ey, WHITE);
    }

    private static void cheeks(int[] g, int cx, int y, int s) {
        set(g, cx - s - 2, y, CHEEK);
        set(g, cx + s + 2, y, CHEEK);
    }

    // ---------------------------------------------------------------- activités

    private static void drawMining(int[] g, int cx, int cy, int rx, int frame) {
        int r = cx + rx;
        // Rocher avec un cristal, posé au sol
        int rcx = r + 6;
        for (int y = 24; y <= 28; y++) {
            for (int x = rcx - 3; x <= rcx + 3; x++) {
                double u = (x - rcx) / 3.5, v = (y - 26) / 2.5;
                if (u * u + v * v > 1.0) continue;
                set(g, x, y, (u + v > 0.5) ? ROCKD : ROCK);
            }
        }
        set(g, rcx - 1, 25, ACC);
        set(g, rcx - 1, 24, ACC);
        set(g, rcx, 25, ACC);

        int hx = r + 1, hy = cy + 1;
        if (frame == 0) { // pioche levée
            for (int i = 0; i <= 4; i++) set(g, hx + i, hy - i, WOOD);
            int ex = hx + 5, ey = hy - 5;
            for (int k = -2; k <= 2; k++) set(g, ex + k, ey + k, METAL);
        } else { // coup sur le rocher
            for (int i = 0; i <= 3; i++) set(g, hx + i, hy + i, WOOD);
            int ex = hx + 4, ey = hy + 4;
            for (int k = -2; k <= 2; k++) set(g, ex - k, ey + k, METAL);
        }
    }

    private static void drawBook(int[] g, Stage stage, int cx, int cy, int frame) {
        int yb = cy + (stage == Stage.BEBE ? 2 : 3);
        int bw = stage == Stage.BEBE ? 3 : 4;
        for (int y = yb; y <= yb + 4; y++) {
            for (int x = cx - bw; x <= cx + bw; x++) {
                int c;
                if (y == yb + 4 || x == cx - bw || x == cx + bw) c = ACC;
                else if (x == cx) c = LINE;
                else if ((y - yb) % 2 == 1 && ((x - cx) & 1) == 1) c = LINE;
                else c = PAGE;
                set(g, x, y, c);
            }
        }
        if (frame == 1) { // page qui se tourne
            set(g, cx + 1, yb - 1, PAGE);
            set(g, cx + 2, yb - 1, PAGE);
            set(g, cx + 2, yb - 2, PAGE);
        }
    }

    private static void drawActivityEffects(int[] out, Occupation occ, Mood current,
                                            int cx, int cy, int rx, int ry, int frame) {
        switch (occ) {
            case MINER:
                if (frame == 1) {
                    int r = cx + rx;
                    putPx(out, r + 3, 22, C_SPARK);
                    putPx(out, r + 2, 21, C_SPARK);
                    putPx(out, r + 7, 22, C_SPARK);
                    putPx(out, r + 8, 21, C_SPARK);
                    putPx(out, r + 5, 21, C_WHITE);
                    // goutte de sueur
                    putPx(out, cx - rx + 1, cy - ry + 3, C_SWEAT);
                    putPx(out, cx - rx + 1, cy - ry + 4, C_SWEAT);
                }
                break;
            case ETUDIER:
                if (frame == 1) { // petite idée
                    int ix = cx + rx - 1, iy = cy - ry - 3;
                    putPx(out, ix, iy, C_SPARK);
                    putPx(out, ix - 1, iy, C_SPARK);
                    putPx(out, ix + 1, iy, C_SPARK);
                    putPx(out, ix, iy - 1, C_SPARK);
                    putPx(out, ix, iy + 1, C_SPARK);
                }
                break;
            default: { // aura de méditation qui tourne
                int ac = mix(current.accent, C_WHITE, 0.3);
                for (int k = 0; k < 8; k++) {
                    double a = Math.toRadians(k * 45 + frame * 22.5);
                    int x = (int) Math.round(cx + (rx + 2.5) * Math.cos(a));
                    int y = (int) Math.round(cy + (ry + 2.5) * Math.sin(a));
                    putPx(out, x, y, ac);
                }
                break;
            }
        }
    }

    // ---------------------------------------------------------------- outils

    private static int topAt(int[] g, int x, int fallback) {
        for (int y = 0; y < N; y++) {
            if (get(g, x, y) != T) return y;
        }
        return fallback;
    }

    private static void ellipse(int[] g, int cx, int cy, int rx, int ry, int c) {
        for (int y = cy - ry; y <= cy + ry; y++) {
            for (int x = cx - rx; x <= cx + rx; x++) {
                double u = (x - cx) / (rx + 0.5), v = (y - cy) / (ry + 0.5);
                if (u * u + v * v <= 1.0) set(g, x, y, c);
            }
        }
    }

    private static void glyph(int[] out, String[] rows, int x0, int y0, int c) {
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                if (rows[y].charAt(x) == 'X') putPx(out, x0 + x, y0 + y, c);
            }
        }
    }

    private static int color(int idx, Mood form) {
        switch (idx) {
            case OUT: return C_OUT;
            case BODY: return form.body;
            case LIGHT: return form.light;
            case SHADE: return form.shade;
            case ACC: return form.accent;
            case EYE: return C_OUT;
            case WHITE: return C_WHITE;
            case CHEEK: return C_CHEEK;
            case MOUTH: return C_MOUTH;
            case WOOD: return 0xFF9A6A3A;
            case METAL: return 0xFFC8D0DC;
            case ROCK: return 0xFF8A8499;
            case ROCKD: return 0xFF5E596E;
            case PAGE: return 0xFFF7F3E8;
            case LINE: return 0xFFB8B0C8;
            default: return 0;
        }
    }

    static int mix(int a, int b, double t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) Math.round(ar + (br - ar) * t);
        int gg = (int) Math.round(ag + (bg - ag) * t);
        int bl = (int) Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    private static int get(int[] g, int x, int y) {
        if (x < 0 || y < 0 || x >= N || y >= N) return T;
        return g[y * N + x];
    }

    private static void set(int[] g, int x, int y, int v) {
        if (x < 0 || y < 0 || x >= N || y >= N) return;
        g[y * N + x] = v;
    }

    private static void putPx(int[] out, int x, int y, int c) {
        if (x < 0 || y < 0 || x >= N || y >= N) return;
        out[y * N + x] = c;
    }
}
