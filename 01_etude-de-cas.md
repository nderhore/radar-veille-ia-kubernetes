# Étude de cas : Industrialiser le radar d'innovation

*Mastère CTO & Tech Lead : prolongement du live « Définition du protocole de veille stratégique »*

> Version courte, centrée sur l'architecture et les pannes. Détail complet des options et critères : `01b_etude-de-cas-version-detaillee.md`. Variante centrée sur la détection des ruptures : `01c_etude-de-cas-angle-veille.md`.

---

## 1. L'entreprise : Altéa Services

**Altéa Services** (entreprise fictive) est une entreprise de services numériques de 1 400 personnes, basée à Lyon. Elle développe des logiciels pour des clients de l'industrie, de la santé et du secteur public.

Il y a six mois, la direction technique a lancé le radar de veille présenté en live. Il fonctionne : deux décisions du comité de direction s'appuient déjà sur ses analyses.

## 2. Pourquoi ce radar compte pour Altéa

| Défi d'Altéa | Ce que le radar doit éclairer |
|---|---|
| Les agents d'IA automatisent une partie de nos prestations de développement et de recette | Quels processus deviennent automatisables, et quelle offre lancer ? |
| Les clients santé et public exigent souveraineté et conformité (AI Act) | Quelles technologies pour une IA souveraine ? Quelles obligations, et quand ? |
| Le coût des modèles d'IA pèse sur nos marges | Quand un petit modèle hébergé chez nous suffira-t-il ? |
| Les attaques sur les assistants IA se multiplient | Quelles nouvelles attaques nous menacent ? |

**Conséquence :** si le radar tombe en panne, Altéa prend ses décisions sur des informations périmées. Or c'est déjà arrivé deux fois pendant le pilote, **sans que personne ne s'en aperçoive**.

## 3. La demande

Le comité de direction demande de transformer le pilote en **vrai service** :

1. l'ouvrir à 300 collaborateurs (chefs de produit, architectes, avant-vente) ;
2. le proposer à 5 clients grands comptes ;
3. le rendre **fiable** : plus aucune panne silencieuse.

> **Problématique.** Comment rendre ce service fiable, sécurisé et observable, sans construire une architecture trop lourde pour l'équipe ?

## 4. Les exigences

| Exigence | Cible |
|---|---|
| Disponibilité | 99,5 % des requêtes sans erreur sur 30 jours (environ 3 h 36 d'indisponibilité tolérée par mois) |
| Fraîcheur de la veille | Chaque source collectée toutes les 6 h ; toute panne de collecte détectée |
| Données | Aucune perte en cas de panne d'un serveur ; restauration en moins de 30 min |
| Sécurité | Échanges chiffrés entre services, accès limités au strict nécessaire |
| Conformité | Hébergement dans l'UE, aucune donnée personnelle dans les journaux |

## 5. L'architecture retenue

```
Navigateur ──► Passerelle ──► radar-web (Angular)
                   │
                   └────────► radar-api (Java) ◄─── signal-collector (Java) ◄─── arXiv, Hacker News, GitHub
                                   │
                                   ▼
                     PostgreSQL (3 serveurs, bascule automatique)

Supervision : Prometheus et Grafana (métriques, alertes) · Vector et Loki (journaux)
```

Le protocole de veille est intégré au service : chaque technologie suivie est rattachée à un axe (KIT) et à une question décisionnelle (KIQ), avec une action recommandée, un responsable et une date de revue.

## 6. Six décisions clés

| # | Question | Décision | Pourquoi | Ce que l'on accepte |
|---|---|---|---|---|
| 1 | Un ou plusieurs services ? | **Deux services** : l'API et le collecteur | Une panne d'une source externe ne bloque pas le radar | Un contrat d'API interne à maintenir |
| 2 | Kubernetes ou une offre plus simple ? | **Kubernetes**, chez un hébergeur européen | L'équipe l'utilise déjà ; exigence d'hébergement dans l'UE | Un coût fixe de plateforme. *Sans compétence Kubernetes, une offre PaaS serait le bon choix.* |
| 3 | Comment sécuriser les échanges ? | **Istio** (service mesh) | Chiffrement et contrôle d'accès entre services sans modifier le code | Un composant de plus à maîtriser |
| 4 | Quelle base de données ? | **PostgreSQL sur 3 serveurs** (CloudNativePG) | Bascule automatique, sauvegardes continues | L'équipe exploite la base et doit tester les restaurations |
| 5 | Comment savoir que tout va bien ? | **Prometheus, Grafana, Vector, Loki** | Outils open source et souverains ; une alerte par symptôme visible | Des alertes à régler pour ne pas noyer l'équipe |
| 6 | Qui reçoit quelle alerte ? | **Trois destinataires** : astreinte (critique), exploitation (avertissement), équipe veille (signal de rupture) | Une alerte qui réveille quelqu'un doit exiger une action immédiate | Une configuration de routage à maintenir |

## 7. Cinq pannes, cinq réactions

| Panne | Ce que l'on observe | Alerte et destinataire | La bonne réaction | Point abordé |
|---|---|---|---|---|
| **1. Le collecteur s'arrête** (ou une source change de format) | Le radar reste consultable, mais la veille ne se met plus à jour | Avertissement : exploitation | Corriger dans la journée ; signaler l'interruption dans la note de veille | Isolation des pannes, panne silencieuse |
| **2. L'API tombe** | Le radar affiche « API indisponible » | Critique : astreinte | **Rétablir d'abord** (redémarrage, retour à la version précédente), chercher la cause ensuite | Disponibilité, budget d'erreur |
| **3. Une nouvelle version est défectueuse** | Le taux d'erreur monte après une mise en production | Critique : astreinte | Grâce au déploiement progressif (10 % du trafic sur la nouvelle version), revenir en arrière en une commande | Livraison progressive (canari) |
| **4. Le serveur principal de la base tombe** | Un serveur de secours prend le relais en quelques secondes | Avertissement : exploitation | Pas besoin de réveiller l'astreinte ; vérifier le lendemain le retour à 3 serveurs | Haute disponibilité, réplication |
| **5. Un sujet dépasse le seuil de rupture** | Une technologie accélère fortement dans les sources | Information : **équipe veille** | Inscrire le sujet à la revue hebdomadaire ; ne jamais réveiller l'astreinte pour cela | Routage des alertes, fatigue d'alerte |

**Bonus sécurité.** Si le collecteur était piraté, il ne pourrait joindre que les sources autorisées et n'aurait le droit que d'envoyer des signaux à l'API. Le risque restant : injecter de faux signaux, d'où l'importance de la validation humaine vue en live.

## 8. Ce qu'il faut retenir

1. **Une veille en panne est pire qu'une absence de veille** : elle donne une fausse assurance. La fraîcheur des données doit être surveillée.
2. **Une architecture se choisit dans un contexte** : Kubernetes convient à Altéa parce que l'équipe le maîtrise déjà.
3. **Toutes les alertes ne se valent pas** : critique pour l'astreinte, avertissement pour l'exploitation, signal métier pour l'équipe veille.
4. **Rétablir d'abord, comprendre ensuite** : chaque minute de panne consomme le budget d'erreur.

## 9. Questions pour la discussion

1. Une jeune pousse de 5 personnes aurait-elle fait les mêmes choix ?
2. Le collecteur fonctionne sur un seul serveur : est-ce acceptable ? Comment l'éviter sans collecter deux fois ?
3. Quels indicateurs présenter au comité de direction pour justifier le coût de la plateforme ?
