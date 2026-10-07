# Étude de cas : Détecter les ruptures technologiques chez Altéa Services

*Mastère CTO & Tech Lead : application du live « Définition du protocole de veille stratégique »*

---

## 1. L'entreprise

**Altéa Services** (entreprise fictive) est une entreprise de services numériques de 1 400 personnes, basée à Lyon. Elle développe et maintient des logiciels pour des clients de l'industrie, de la santé et du secteur public. Une grande partie de son chiffre d'affaires vient de prestations de développement et de recette.

Depuis deux ans, l'IA générative bouleverse son marché. Le comité de direction demande au CTO un dispositif de veille capable de **repérer tôt les ruptures technologiques** et d'**éclairer les décisions** des 6 à 18 prochains mois.

## 2. Cinq défis, cinq décisions à prendre

| Défi | Décision à prendre | Axe de veille (KIT) · question (KIQ) |
|---|---|---|
| Les agents d'IA commencent à automatiser le développement et la recette | Faut-il lancer une offre « agents métier » ? | KIT-2 · Quels processus deviennent automatisables de bout en bout ? |
| La facture des API de modèles d'IA pèse sur les marges | Faut-il héberger nos propres modèles ? | KIT-1 · Quand un petit modèle hébergé chez nous atteindra-t-il la qualité attendue ? |
| Les clients santé et public exigent un hébergement en France | Faut-il construire une offre d'IA souveraine ? | KIT-3 · Quelles technologies permettent une IA souveraine ou embarquée ? |
| L'AI Act entre en application | Quel plan de mise en conformité, et quand ? | KIT-4 · Quelles obligations s'appliquent à nos produits ? |
| Les produits d'IA se connectent entre eux | Quel standard d'intégration adopter ? | KIT-2 · Quel standard d'interopérabilité entre agents s'impose ? |

> **Principe du live appliqué ici :** on ne surveille pas « l'IA » en général. On part des décisions, on en tire des questions précises (KIQ), puis on choisit les sources et les indicateurs qui permettent d'y répondre.

## 3. Le radar configuré pour Altéa

| Élément du protocole | Choix d'Altéa |
|---|---|
| Axes (KIT) | 4 axes, un par quadrant du radar : Modèles · Agents · Inférence et souveraineté · Conformité et sécurité |
| Questions (KIQ) | 2 par axe, chacune rattachée à une décision, un responsable et un horizon |
| Sources | Recherche (arXiv), code ouvert (GitHub), communauté (Hacker News), presse spécialisée |
| Indicateurs | Croissance anormale (G²), diffusion entre types de sources, nouveauté, impact |
| Lecture | Anneaux d'action : **Agir** (0–6 mois), **Préparer** (6–18 mois), **Explorer** (18–36 mois), **Surveiller** |
| Revue humaine | Revue hebdomadaire : chaque sujet est jugé « pertinent » ou « bruit » |

## 4. Ce que le radar a détecté

Données réelles collectées le 7 octobre 2026 : 12 169 signaux, période récente de 14 jours comparée aux 60 jours précédents.

| Sujet | Signaux récents / référence | Tendance | Lecture pour Altéa |
|---|---|---|---|
| **On-policy distillation** (entraîner un petit modèle à partir d'un grand) | 35 / 28 | Forte accélération (G² = 18,4) | Signal émergent sur la question « petit modèle hébergé chez nous » : à expérimenter |
| **Computer use** (agents qui utilisent les logiciels comme un humain) | 28 / 55 | Stable, mais présent dans toutes les sources | Technologie en cours d'industrialisation : menace et opportunité pour les prestations de recette |
| **Model Context Protocol** (standard de connexion des agents) | 29 / 135 | Recul dans la recherche, présent partout ailleurs | Le standard s'installe : il est adopté plutôt qu'étudié |
| **RAG** (recherche documentaire augmentée) | 42 / 200 | Recul relatif marqué | Technique banalisée : elle ne sera plus un facteur de différenciation |
| **Prompt injection** (manipulation des assistants) | 49 / 111 | Stable, sujet durable | Menace permanente à intégrer au plan de sécurité |

**Attention aux lectures trop rapides :** un recul dans les sources ne signifie pas que la technologie disparaît. Souvent, on cesse d'écrire sur ce que tout le monde utilise déjà.

## 5. Quatre décisions à instruire

### Décision A : Lancer une offre « agents métier » ? (KIQ-2.1)

- **Signaux.** Computer use présent dans les quatre types de sources ; nombreux projets d'agents sur GitHub.
- **Options.** Lancer l'offre maintenant · mener une preuve de concept chez un client pilote · attendre.
- **Recommandation.** Preuve de concept de 6 semaines sur la recette automatisée, chez un client volontaire, avec un critère de succès chiffré (part des tests automatisés).
- **Signal de révision.** Un concurrent annonce une offre équivalente : accélérer.

### Décision B : Héberger nos propres modèles ? (KIQ-1.2)

- **Signaux.** Accélération de la « distillation » et des petits modèles dans la recherche, encore peu de code mature.
- **Options.** Rester sur les API · tester un petit modèle sur un cas d'usage interne · internaliser tout de suite.
- **Recommandation.** Tester un petit modèle sur un cas d'usage interne (support, documentation) et comparer qualité et coût sur 3 mois.
- **Signal de révision.** Apparition d'outils prêts à l'emploi dans le code ouvert : passer en « Préparer ».

### Décision C : Quel standard d'intégration ? (KIQ-2.2)

- **Signaux.** Model Context Protocol présent partout, en phase d'adoption ; le protocole Agent-to-Agent est plus récent et moins diffusé.
- **Recommandation.** Adopter Model Context Protocol pour connecter nos produits ; surveiller Agent-to-Agent.

### Décision D : Plan de conformité AI Act (KIQ-4.1)

- **Signaux.** Peu de signaux techniques, mais des échéances réglementaires fixes : c'est une **contrainte certaine**, pas une rupture incertaine.
- **Recommandation.** Plan de conformité porté par le DPO et la direction juridique ; la veille suit les textes d'application et les outils d'audit (filigrane, traçabilité).

## 6. Mises en situation de veille

| Situation | Exemple | La bonne réaction |
|---|---|---|
| **Signal faible isolé** | « Class-incremental learning » : 5 articles récents, aucun avant, uniquement dans la recherche | Anneau « Surveiller » : un seul type de source, pas encore de convergence |
| **Fausse alerte** | « Decision models » arrive en tête du classement automatique | L'analyste le juge « bruit » (artefact de vocabulaire) : il disparaît au calcul suivant |
| **Angle mort** | Une question (KIQ) n'a aucun signal récent | Revoir le plan de sourcing : ajouter une source ou une requête |
| **Recul n'est pas déclin** | RAG recule dans les sources | Vérifier l'adoption avant de conclure : la technique est devenue standard |
| **Confirmation par convergence** | La distillation ressort à la fois par les mots-clés et par le regroupement thématique | Deux méthodes indépendantes concordent : la confiance dans le signal augmente |

## 7. Comment le radar est outillé (pour aller plus loin)

Le radar d'Altéa est un service web : un collecteur interroge les sources toutes les 3 heures, une API calcule les indicateurs et rattache chaque sujet au protocole (axe, question, action, responsable, date de revue), une interface affiche le radar et la **couverture** de chaque question. Une alerte prévient l'équipe veille lorsqu'un sujet franchit le seuil de rupture, et une autre lorsque la collecte s'arrête : une veille qui tombe en panne sans prévenir donne une fausse assurance.

La démonstration (`demo/`) montre ce service en fonctionnement. Le détail des choix techniques figure en annexe (`01b_etude-de-cas-version-detaillee.md`, dossier `plateforme/`).

## 8. Ce qu'il faut retenir

1. **On part des décisions**, pas des technologies : chaque sujet du radar répond à une question posée par un décideur.
2. **Une rupture se mesure** : croissance anormale, diffusion d'un type de source à l'autre, convergence de plusieurs méthodes.
3. **L'humain reste indispensable** : il écarte les fausses alertes et interprète les reculs.
4. **Chaque analyse débouche sur une action** datée, avec un responsable et un signal de révision.

## 9. Questions pour la discussion

1. Parmi les quatre décisions, laquelle est la plus urgente pour Altéa ? Pourquoi ?
2. Quelle source manque au radar pour la décision B (petits modèles) ?
3. Comment distinguer une vraie rupture d'un simple effet de mode ?
