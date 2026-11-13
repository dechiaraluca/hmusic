# hmusic : ta musique, ton humeur

Une application de musique « à la Spotify » en Java, pilotée par l'humeur, avec **deux modes** :

- **Composer** : hmusic **invente en temps réel** une musique unique à partir de ton humeur,
  et la transforme en continu quand ton humeur change (aucun fichier audio).
- **Écouter** : hmusic choisit de **vrais morceaux** complets et légaux sur
  [Jamendo](https://www.jamendo.com) (licences Creative Commons) selon ton humeur, et fait évoluer
  la file d'attente quand ton humeur change.

Les deux modes partagent le pavé d'humeur, l'analyse de texte, les stations, les voyages guidés et le journal.

## Lancer l'application

Il faut seulement **Java 17 ou plus**. Maven est téléchargé automatiquement par le *Maven Wrapper*.

```bat
run.bat        :: compile (si besoin) puis lance l'application
build.bat      :: compile seulement -> target\hmusic.jar
```

Ou depuis un terminal : `mvnw.cmd package` puis `java -jar target\hmusic.jar`.
Le projet s'ouvre aussi directement dans IntelliJ, Eclipse ou VS Code (projet Maven).

### Version Windows autonome et site de présentation

- `dist.bat` fabrique `target\hmusic-windows.zip` : un dossier avec `hmusic.exe`, Java intégré (`jpackage`) et
  la banque de sons. Rien à installer pour la personne qui le télécharge. Ce zip est publié dans les
  *Releases* GitHub.
- `site/` contient la page de présentation (HTML statique), publiée par Netlify grâce à `netlify.toml`.
  Le bouton « Télécharger » pointe vers `releases/latest/download/hmusic-windows.zip`.

### Activer le mode Écouter (une seule fois)

1. Crée un compte gratuit sur [devportal.jamendo.com](https://devportal.jamendo.com/).
2. Crée une application (« Create a new app ») et copie son **Client ID**.
3. Dans hmusic, passe en mode **Écouter** et colle le Client ID.

Il est enregistré dans `~/.hmusic/settings.properties`. On peut aussi le fournir par la variable
d'environnement `JAMENDO_CLIENT_ID`.

### Son du mode Composer

Le dossier `soundfonts/` contient **GeneralUser GS** (S. Christian Collins), une banque de sons
General MIDI gratuite, bien plus réaliste que celle intégrée à Java. Elle est chargée automatiquement.
Sa licence est dans `soundfonts/GeneralUser-GS-LICENSE.txt`.

### Premier lancement

1. Crée un compte : **le tout premier compte est administrateur** (aucun mot de passe admin n'est écrit
   dans le code). Les comptes suivants sont des auditeurs.
2. Avec le compte admin, ouvre le menu (clic sur ton profil, en haut à droite) puis **Importer depuis
   Jamendo** pour remplir le catalogue (environ 20 morceaux pour chacun de 14 genres). Il faut avoir
   configuré le Client ID Jamendo.
3. Crée un compte auditeur, passe en mode **Écouter**, écris ton humeur : le Recommandeur propose une
   playlist tirée du catalogue. Tu peux aussi cliquer sur une des **8 catégories d'humeur** (Heureux,
   Tendu, Serein…) : les morceaux du catalogue sont classés par le même score que le `MockRecommandeur`
   (tags d'ambiance + genre).

L'avatar du profil prend la couleur de l'humeur du moment (anneau orange pour un compte admin).

Tests : `mvnw.cmd test`.

### Activer la playlist IA

L'`IaRecommandeur` réutilise le client du Projet 3 (API Groq). La clé est lue dans la variable
d'environnement **`AI_API_KEY`** : elle n'est jamais écrite dans le code ni dans un fichier du projet.

```bat
setx AI_API_KEY "ta-clé-groq"
```

Ferme puis rouvre le terminal (ou VS Code) avant de relancer `run.bat`. Sans cette variable, hmusic utilise
le `MockRecommandeur` (hors ligne). Si l'IA échoue (réseau, quota), hmusic l'indique et bascule
automatiquement sur le `MockRecommandeur` pour cette playlist. La variable facultative `AI_MODEL` permet de
changer de modèle, comme au Projet 3.

## Comptes, catalogue et playlist

| Élément | Où | Rôle |
|---|---|---|
| `Utilisateur` (abstraite) | `utilisateur/` | identifiant, mot de passe haché, menu commun |
| `Auditeur`, `Admin` | `utilisateur/` | sous-classes : chacune fournit ses propres options de menu |
| `GestionUtilisateurs` | `utilisateur/` | inscription, connexion, sauvegarde par sérialisation |
| `MotDePasse` | `utilisateur/` | SHA-256 + sel aléatoire |
| `Morceau`, `Catalogue` | `catalogue/` | catalogue rangé dans une `Map<genre, List<Morceau>>` (+ index par identifiant) |
| `ImportJamendo` | `stream/` | remplit le catalogue depuis la bibliothèque Jamendo |
| `Recommandeur` (interface) | `reco/` | `MockRecommandeur` (sans réseau) et `IaRecommandeur` (IA) |
| `RecommandeurPersonnalise` | `reco/` | **patron Décorateur** : enveloppe un autre Recommandeur et remonte les morceaux qui plaisent à l'auditeur |
| `ProfilGouts` | `reco/` | goûts appris (tags, genres, artistes), sauvegardé avec le compte de l'auditeur |
| `PlaylistIa` | `reco/` | prompt envoyé à l'IA + **contrôle de sa réponse** |
| `AiClient`, `HttpAiClient`, `AiException` | `ia/` | repris du Projet 3 (ajouts signalés en commentaire) |

- **Auditeur** : favoris (morceaux et artistes) et abonnements, depuis les boutons sous le morceau en cours
  (mode Écouter), puis dans *Mes favoris* et *Mes abonnements* (menu).
- **Admin** : ajouter un morceau à la main, gérer le catalogue (filtrer par genre, rechercher, supprimer),
  importer depuis Jamendo, **lister les utilisateurs inscrits** (le « défi »).
- **Le recommandeur apprend** (auditeur) : mettre en favori (+3), aimer l'artiste (+3), écouter jusqu'au bout (+1),
  passer avant 30 s (−1). Ces signaux pondèrent les tags, le genre et l'artiste ; les playlists et les catégories
  d'humeur remontent ensuite ce qui plaît. Poids bornés entre −6 et +12.
- Les données sont dans `~/.hmusic/` : `utilisateurs.ser`, `catalogue.ser`, `journal-<identifiant>.csv`,
  `ecoutes-<identifiant>.csv` (historique d'écoute pour le bilan).

### Points de contrôle : où regarder dans le code

| Question | Réponse dans… |
|---|---|
| Pourquoi `Utilisateur` est abstraite ? Et `new Utilisateur()` ? | commentaire de `utilisateur/Utilisateur.java` |
| Pourquoi `Recommandeur` est une interface ? | commentaire de `reco/Recommandeur.java` |
| Morceau proposé par l'IA qui n'existe pas ? | `reco/PlaylistIa.controler(...)`, testé dans `RecommandeurTest` |
| Pourquoi une `Map` pour le catalogue ? | commentaire de `catalogue/Catalogue.java` |
| Menu différent selon l'utilisateur (polymorphisme) ? | `Utilisateur.menu()` + `optionsSpecifiques()` dans `Auditeur` / `Admin` |
| Ajouter une fonction sans modifier les recommandeurs ? | `reco/RecommandeurPersonnalise` (Décorateur), testé dans `RecommandeurPersonnaliseTest` |

## Fonctionnalités

| Fonction | Description |
|---|---|
| **Deux modes** | Sélecteur *Composer / Écouter* en haut à droite. Passer d'un mode à l'autre conserve l'humeur, et la lecture continue dans le nouveau mode si elle était en cours. |
| **Pavé d'humeur** | Un plan 2D (modèle circomplexe de Russell) : horizontalement de *désagréable* à *agréable*, verticalement de *calme* à *énergique*. |
| **« Comment tu te sens ? »** | On écrit une phrase (« crevé mais content », « très stressé par les partiels ») et un analyseur de texte français en déduit l'humeur, en gérant les négations et les intensificateurs. |
| **Voyages guidés** | Inspirés du *principe d'iso-modulation* utilisé en musicothérapie : la musique **rejoint d'abord** ton humeur, puis **glisse doucement** vers une humeur cible. **Clic droit** sur le pavé = voyage personnalisé. En mode Écouter, le voyage dure 3 fois plus longtemps pour laisser passer plusieurs morceaux. |
| **Stations d'humeur** | Des « playlists infinies » prédéfinies (Pluie sur la vitre, Course nocturne…). |
| **Composer : morceaux générés** | 32 mesures, un **titre poétique inventé** et une **pochette générative** unique. Le visualiseur fait naître une particule pour chaque note jouée. |
| **Composer : export MIDI** | Toute la musique composée pendant la session s'exporte en `.mid` (MuseScore, GarageBand, FL Studio…). |
| **Écouter : vrais morceaux** | Pochette, artiste, album, tags d'ambiance, lien vers Jamendo, **égaliseur en direct** (analyse FFT du son), file « À suivre » cliquable, avance dans le morceau en cliquant sur la barre de progression. |
| **Journal d'humeur** | Chaque humeur choisie est enregistrée (`~/.hmusic/journal-<identifiant>.csv`), affichée en points sur le pavé et en « ruban de couleurs » dans le journal. |
| **Bilan du mois** | Menu > *Mon bilan du mois* : une carte façon *Wrapped* (humeur dominante, calendrier coloré jour par jour, temps d'écoute, artistes, musique fétiche, goûts appris), exportable en PNG. Et surtout **« ce qui t'aide vraiment »** : pour chaque station, voyage ou catégorie, hmusic compare l'humeur que tu as déclarée juste avant et juste après (3 h maximum, avant de lancer une autre musique). |
| **Composer : clavier musical** | La rangée du milieu (`Q S D F G H J K L M` en AZERTY) joue des notes **toujours dans la gamme du moment**, par-dessus la composition, avec l'instrument de la mélodie. Les notes s'enregistrent dans l'export `.mid`. |

## Comment l'humeur devient de la musique

### Mode Composer (règles de composition)

| Paramètre | Dépend de… | Règle |
|---|---|---|
| Tempo | énergie | de 64 BPM (calme) à 140 BPM (énergique) |
| Mode / gamme | valence | Lydien, Majeur, Mixolydien (lumineux) → Dorien → Mineur, Phrygien (sombre) |
| Accords | mode | suites typiques (I–V–vi–IV en majeur, i–VI–III–VII en mineur…) |
| Instruments | quadrant | *Aquarelle* (serein), *Soleil* (joyeux), *Néon* (euphorique), *Orage* (tendu), *Pluie* (triste), *Brume* (neutre) |
| Densité de notes | énergie | plus d'énergie = plus de notes, arpèges plus rapides |
| Batterie | énergie | absente si calme, percussions légères, puis rythme « four on the floor » |
| Réverbération | énergie | plus c'est calme, plus l'espace sonore est grand |

La mélodie est un **motif de 2 mesures** créé par marche aléatoire sur les degrés de la gamme,
recalé sur les notes de l'accord aux temps forts, et qui varie toutes les 4 mesures.

### Mode Écouter (recherche Jamendo)

| Critère Jamendo | Dépend de… | Exemple |
|---|---|---|
| `fuzzytags` (ambiance) | famille d'émotion | Serein → `relaxing calm peaceful chill meditation` ; Tendu → `dark aggressive intense` |
| `speed` (tempo) | énergie | énergie basse → `verylow low` ; haute → `high veryhigh` |
| `groupby=artist_id` | — | un seul morceau par artiste, pour la variété |
| `durationbetween=90_480` | — | ni jingles, ni mix d'une heure |

La file d'attente est renouvelée quand l'humeur change de famille. Un choix explicite (clic sur le pavé,
station, phrase) change de morceau tout de suite. Un changement progressif (voyage) ne modifie que
les morceaux suivants, sans couper celui en cours.

## Architecture

```
src/main/java/hmusic/
├── Main.java                  point d'entrée
├── model/                     le « quoi » (aucune dépendance à Swing ni au son)
│   ├── Mood, Sector           humeur = (valence, énergie), 8 familles d'émotions
│   ├── Scale, Instrumentation modes musicaux, orchestres General MIDI
│   ├── MusicalProfile         traduction humeur → paramètres musicaux
│   └── Station, Journey, TitleGenerator
├── engine/                    mode Composer
│   ├── MoodEngine             compositeur temps réel (thread dédié + javax.sound.midi)
│   ├── EngineListener         patron Observer
│   └── MidiRecorder           enregistrement de la session en .mid
├── stream/                    mode Écouter
│   ├── JamendoClient          appels HTTP à l'API Jamendo (java.net.http + Gson)
│   ├── MoodTags               traduction humeur → tags et tempo Jamendo
│   ├── StreamPlayer           lecteur MP3 en streaming (JLayer) : pause, avance, volume
│   ├── Spectrum               FFT pour l'égaliseur
│   └── ListenController       file d'attente selon l'humeur, enchaînement des morceaux
├── analysis/MoodAnalyzer      analyse de texte → humeur
├── data/                      MoodJournal, HistoriqueEcoute (CSV), Bilan (calcul du bilan du mois)
├── config/Settings            réglages (Client ID Jamendo)
└── ui/                        interface Swing (composants dessinés à la main)
    ├── MainWindow             fenêtre + contrôleur des deux modes
    ├── MoodPad, Visualizer    pavé d'humeur, visualiseur génératif
    ├── ListenPanel            lecteur Jamendo (connexion, morceau en cours, file d'attente)
    └── ModeSwitch, Sidebar, PlayerBar, JournalDialog, ImageCache, …
```

Dépendances (gérées par Maven, voir `pom.xml`) : **JLayer** pour décoder le MP3 (Java ne sait pas le faire
seul) et **Gson** pour lire les réponses JSON de Jamendo.

Points techniques intéressants à présenter :

- **Programmation concurrente** : le compositeur, le lecteur MP3, les appels réseau et le chargement des
  pochettes tournent chacun dans leur propre thread. L'interface ne leur parle qu'à travers des champs
  `volatile`, et leurs résultats reviennent sur le thread Swing avec `SwingUtilities.invokeLater`.
- **Patron Observer** (`EngineListener`, `ListenController.View`) : l'interface réagit aux évènements
  sans que les moteurs la connaissent.
- **Patron Décorateur** (`RecommandeurPersonnalise`) : la personnalisation s'ajoute à n'importe quel Recommandeur.
- **Communication entre threads sans verrou** : le clavier musical dépose les touches dans une
  `ConcurrentLinkedQueue` ; seul le thread audio touche au synthétiseur.
- **Traitement du signal** : décodage MP3 trame par trame, réglage du volume sur les échantillons,
  FFT maison (Cooley-Tukey) regroupée en bandes logarithmiques.
- **API REST** : requête HTTP, lecture du JSON, gestion des erreurs (clé invalide, réseau coupé).
- **Séparation modèle / moteurs / vue** : le package `model` ne dépend ni de Swing ni du son.

## Crédits

- Musiques du mode Écouter : artistes de [Jamendo](https://www.jamendo.com), licences Creative Commons.
- Banque de sons : [GeneralUser GS](https://github.com/mrbumpy409/GeneralUser-GS) par S. Christian Collins.
- Décodage MP3 : JLayer (JavaZOOM, LGPL). JSON : Gson (Google, Apache 2.0).
