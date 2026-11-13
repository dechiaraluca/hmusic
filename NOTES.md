# NOTES.md : usage de l'IA

## Outils utilisés

- **Claude Code** (dans VS Code) : conception et écriture du code, à partir de mes demandes. Mon rôle : choisir
  les fonctionnalités, tester l'application, signaler ce qui n'allait pas, et relire le code pour le comprendre.
- **Groq** (modèle `openai/gpt-oss-20b`), via l'`AiClient` du Projet 3 : c'est l'IA qui compose la playlist
  dans l'application (`IaRecommandeur`). La clé est lue dans la variable d'environnement `AI_API_KEY`.

## 1. Plan des classes proposé par l'IA

| Classe | Hérite de / implémente | Pourquoi |
|---|---|---|
| `Utilisateur` (abstraite) | — | ce qui est commun (identifiant, mot de passe haché, menu commun) ; un utilisateur « sans rôle » ne doit pas exister |
| `Auditeur`, `Admin` | `extends Utilisateur` | chacun redéfinit `role()` et `optionsSpecifiques()` → menu polymorphe |
| `Morceau` (record) | — | objet immuable : titre, artiste, genre (+ infos Jamendo) |
| `Catalogue` | — | `Map<genre, List<Morceau>>` + `Map<id, Morceau>` pour vérifier vite qu'un morceau existe |
| `Recommandeur` (interface) | — | un contrat, deux façons de faire sans code commun |
| `MockRecommandeur`, `IaRecommandeur` | `implements Recommandeur` | hors ligne et déterministe / IA du Projet 3 |
| `PlaylistIa` | — | construit le prompt et **contrôle** la réponse de l'IA (sans réseau, donc testable) |
| `RecommandeurPersonnalise` (ajouté ensuite) | `implements Recommandeur` | patron Décorateur : enveloppe Mock ou IA et tient compte des goûts appris |

## 2. Le prompt de playlist, testé avec plusieurs humeurs

Prompt envoyé (voir `PlaylistIa.prompt`) : l'humeur, des règles strictes (« choisis UNIQUEMENT parmi les
morceaux du catalogue », « n'invente aucun morceau », « réponds uniquement par les identifiants, un par
ligne », « ordonne la playlist pour qu'elle s'enchaîne bien »), puis le catalogue :
`identifiant | titre | artiste | genre | ambiances`.

Test du 13/11/2026 : catalogue de 243 morceaux répartis dans 14 genres, 12 morceaux demandés à chaque fois.

| Humeur saisie | Morceaux rendus | Inventés (rejetés) | Ce que j'observe |
|---|---|---|---|
| « très stressé par les partiels » | 12 | 0 | ambient, classique, lounge, tags *peaceful* / *relaxing* : cohérent |
| « fatigué mais content de ma journée » | **10** | 0 | mélange calme (méditation) + joyeux (*happy*) : le « mais » est bien compris |
| « en colère contre tout le monde » | **9** | 0 | **uniquement du metal** : l'IA reflète la colère, elle ne propose rien pour apaiser |
| « je veux écouter du Daft Punk pour me motiver » | **8** | 0 | Daft Punk n'est pas dans le catalogue : elle ne l'invente pas et propose de l'électro/house à la place, **sans le signaler** |

**Ce que l'IA a ignoré :**
- **le nombre de morceaux demandé** : 12 demandés, 10, 9 ou 8 rendus ;
- « ordonne pour que ça s'enchaîne » : les morceaux sont simplement **regroupés par genre** (le catalogue lui est envoyé genre par genre) ;
- la variété : plusieurs morceaux reviennent pour deux humeurs différentes (stressé / fatigué).

**Ce qu'elle a inventé :** rien pendant ce test. Le contrôle reste indispensable : rien ne garantit
qu'elle respecte toujours la consigne. Le rejet des morceaux inventés est vérifié par des tests JUnit avec
une fausse IA qui invente exprès (`IaRecommandeurTest`, `RecommandeurTest`).

**Autre problème rencontré :** deux demandes à la suite → **erreur 429** (offre gratuite limitée à 8000
jetons par minute, et chaque prompt fait environ 4500 jetons à cause du catalogue). Dans l'application,
le `MockRecommandeur` prend alors le relais automatiquement. C'est aussi pour cette raison que le catalogue
envoyé est limité à 150 morceaux (`IaRecommandeur.MAX_CANDIDATS`).

## 3. Erreurs de l'IA (Claude) repérées pendant le développement

- **Profil en double** : après l'ajout du profil en haut à droite, l'ancien menu en bas à gauche était resté.
  Je l'ai vu sur une capture et je l'ai fait retirer.
- **Fenêtre de connexion derrière les autres** quand on lance depuis le terminal : il fallait aller sur le
  bureau pour la voir. Corrigé (passage au premier plan à l'ouverture + bouton dans la barre des tâches).
- **Point d'interrogation coupé** dans « Comment tu te sens ? » : la **première correction de l'IA n'a rien
  changé**. Elle avait trouvé la bonne cause (texte mesuré au pixel près au dessin, mais arrondi pour la taille)
  mais oublié que `BoxLayout` utilise la taille *maximale* du composant. Repéré en relançant l'application.
- **Journal d'humeur** : deux humeurs à moins de 20 s d'écart étaient fusionnées, ce qui effaçait l'humeur
  « avant » quand on cliquait sur le pavé puis sur une station. Repéré par l'IA elle-même en codant le bilan
  du mois, et corrigé (fusion seulement si même origine).

## 4. Points à bien revoir avant la soutenance

- La sérialisation : pourquoi un compte enregistré avant l'ajout d'un champ est relu avec `null`
  (d'où `Auditeur.gouts()` qui crée le profil à la demande).
- Les threads : compositeur, lecteur MP3 et réseau ont chacun leur thread ; retour sur l'interface avec
  `SwingUtilities.invokeLater` ; le clavier musical passe par une `ConcurrentLinkedQueue`.
- Le patron Décorateur (`RecommandeurPersonnalise`) et la différence avec l'héritage.

## 5. Prompts utiles

- Pour l'IA de l'application : donner **la liste fermée** du catalogue avec des identifiants, et exiger
  **seulement des identifiants**, un par ligne. C'est beaucoup plus facile à contrôler qu'un « titre - artiste ».
- Pour coder avec Claude : décrire le problème tel qu'on le voit, avec une capture d'écran
  (« le profil et le menu apparaissent en bas à gauche aussi, on pourrait le garder qu'en haut à droite »),
  plutôt que de proposer une solution technique.
- « Avec tout ça, on pourrait implémenter quoi de vraiment cool ? » : idées comparées, puis choix argumenté
  (recommandeur qui apprend, bilan du mois, clavier musical).
