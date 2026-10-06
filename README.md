# Mélopet 🎵

Une petite créature en pixel art qui vit sur l'écran d'accueil de ton téléphone Android et **évolue selon la musique que tu écoutes sur Spotify**.

![Aperçu des stades et des formes](docs/apercu.png)

## Comment ça marche

- **Chaque morceau qui démarre la nourrit**, en temps réel. Pas besoin d'ouvrir l'appli.
- Son **ambiance** est devinée d'après les genres de l'artiste (via [MusicBrainz](https://musicbrainz.org)) :
  Énergique (rock, metal…), Festif (pop, dance…), Chill (jazz, lo-fi, folk…), Sombre (goth, post-punk…), Urbain (rap, R&B…), ou Curieux.
- **Ses couleurs** suivent l'ambiance que tu écoutes le plus. Le **ciel** derrière elle suit le morceau en cours.
- **Évolution** : Œuf → Bébé (3 morceaux) → Ado (30) → Adulte (150). La forme adulte est figée selon tes goûts du moment :
  crête de piques, chapeau de fête, casque audio, cornes, casquette ou antenne.
- **Besoins** : la faim et la joie baissent avec le temps ; l'énergie remonte au repos (surtout la nuit, où elle dort).
  Écouter des artistes variés la rend heureuse, mais le même artiste en boucle l'ennuie.
- Tu peux la **caresser** (une fois toutes les 10 min) et **corriger l'ambiance** d'un artiste en touchant un morceau dans l'historique.

## Installer sur le téléphone

1. Sur ton téléphone, ouvre la page **Releases** de ce dépôt et télécharge `melopet.apk` (dernière version).
2. Ouvre le fichier. Android te demandera d'autoriser l'installation depuis ton navigateur : accepte.
3. Ouvre **Mélopet** et touche **Activer l'accès**, puis active Mélopet dans l'accès aux notifications.
   - Si l'interrupteur est **grisé** (« paramètre restreint ») : Paramètres → Applis → Mélopet → ⋮ en haut à droite → **Autoriser les paramètres restreints**, puis réessaie.
4. Dans Spotify : rien à faire. L'appli suit simplement le lecteur.
5. Touche **Ajouter le widget à l'écran d'accueil** (ou appui long sur l'écran d'accueil → Widgets → Mélopet).

Pour mettre à jour : télécharge la nouvelle release et installe-la par-dessus. Ta créature est conservée.

## Développement

Chaque `push` sur `main` déclenche GitHub Actions, qui compile l'APK et publie une release.

- `SpriteRenderer.java` : dessin de la créature (Java pur, testable hors Android)
- `PetState.kt` : règles du jeu (faim, énergie, joie, évolution)
- `MusicListenerService.kt` : suit la session média de Spotify
- `MoodResolver.kt` : genres MusicBrainz → ambiance
- `PetWidgetProvider.kt` : widget animé ; `MainActivity.kt` : écran principal

Mélopet n'est affilié ni à Spotify ni à Bandai.
