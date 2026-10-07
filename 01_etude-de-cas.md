# Étude de cas : Industrialiser le radar d'innovation

*Mastère CTO & Tech Lead : prolongement du live « Définition du protocole de veille stratégique »*


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
| Données | Au plus 24 h de données perdues en cas d'incident grave ; restauration en moins de 30 min |
| Sécurité | Accès en HTTPS ; chaque service n'accède qu'à ce dont il a besoin |
| Conformité | Hébergement dans l'UE, aucune donnée personnelle dans les journaux |

L'équipe compte deux développeurs Java et Angular et un administrateur système. Personne ne maîtrise Kubernetes.

## 5. L'architecture retenue

```
Navigateur ──HTTPS──► radar-web (nginx : front Angular, relais /api)
                              │
                              ▼
arXiv, Hacker News, GitHub ──► signal-collector (Java) ──► radar-api (Java) ──► PostgreSQL
                                                                                (sauvegarde chaque nuit)

Supervision : Prometheus, Alertmanager et Grafana (métriques, alertes) · Vector et Loki (journaux)
Le tout dans Docker Compose, sur un serveur chez un hébergeur européen.
```

Le protocole de veille est intégré au service : chaque technologie suivie est rattachée à un axe (KIT) et à une question décisionnelle (KIQ), avec une action recommandée, un responsable et une date de revue.

## 6. Six décisions clés

| # | Question | Décision | Pourquoi | Ce que l'on accepte |
|---|---|---|---|---|
| 1 | Un ou plusieurs services ? | **Deux services** : l'API et le collecteur | Une panne d'une source externe ne bloque pas le radar | Un contrat d'API interne à maintenir |
| 2 | Kubernetes ou Docker Compose ? | **Docker Compose**, sur un serveur chez un hébergeur européen | 300 utilisateurs : un serveur suffit ; l'équipe déploie en une commande, sans compétence Kubernetes | Pas de bascule automatique vers un autre serveur. *Kubernetes deviendra pertinent si la charge ou l'exigence de disponibilité augmentent nettement.* |
| 3 | Comment sécuriser les échanges ? | **Un seul point d'entrée en HTTPS** et des **réseaux Docker séparés** | La base n'est joignable que par l'API ; le collecteur ne voit que l'API | Les échanges internes ne sont pas chiffrés : acceptable sur un seul serveur |
| 4 | Comment protéger les données ? | **PostgreSQL** sur un volume persistant, **sauvegardé chaque nuit** hors du serveur | Simple ; les signaux se recollectent, seuls les verdicts des analystes sont précieux | Jusqu'à 24 h de données perdues ; une restauration à tester chaque mois |
| 5 | Comment savoir que tout va bien ? | **Prometheus, Grafana, Vector, Loki** | Outils open source et souverains ; une alerte par symptôme visible | Des alertes à régler pour ne pas noyer l'équipe |
| 6 | Qui reçoit quelle alerte ? | **Trois destinataires** : astreinte (critique), exploitation (avertissement), équipe veille (signal de rupture) | Une alerte qui réveille quelqu'un doit exiger une action immédiate | Une configuration de routage à maintenir |

## 7. Cinq pannes, cinq réactions

| Panne | Ce que l'on observe | Alerte et destinataire | La bonne réaction | Point abordé |
|---|---|---|---|---|
| **1. Le collecteur s'arrête** (ou une source change de format) | Le radar reste consultable, mais la veille ne se met plus à jour | Avertissement : exploitation | Corriger dans la journée ; signaler l'interruption dans la note de veille | Isolation des pannes, panne silencieuse |
| **2. L'API tombe** | Le radar affiche « API indisponible » | Critique : astreinte | Docker la redémarre seul après un plantage. Si elle retombe : **rétablir d'abord** (version précédente), chercher la cause ensuite | Disponibilité, budget d'erreur |
| **3. Une nouvelle version est défectueuse** | Le taux d'erreur monte après une mise en production | Critique : astreinte | Les images sont versionnées : revenir à la version précédente en une commande | Versionnement, retour arrière |
| **4. La base de données s'arrête** | L'API répond en erreur | Critique : astreinte | Redémarrer la base ; si le disque est perdu, restaurer la dernière sauvegarde (moins de 30 min) | Sauvegarde, restauration testée |
| **5. Un sujet dépasse le seuil de rupture** | Une technologie accélère fortement dans les sources | Information : **équipe veille** | Inscrire le sujet à la revue hebdomadaire ; ne jamais réveiller l'astreinte pour cela | Routage des alertes, fatigue d'alerte |

**Bonus sécurité.** Si le collecteur était piraté, il ne verrait que l'API : il n'a aucun accès à la base, qui est sur un réseau séparé. Le risque restant : injecter de faux signaux, d'où l'importance de la validation humaine vue en live.

## 8. Ce qu'il faut retenir

1. **Une veille en panne est pire qu'une absence de veille** : elle donne une fausse assurance. La fraîcheur des données doit être surveillée.
2. **Une architecture se choisit dans un contexte** : Docker Compose suffit à Altéa aujourd'hui ; Kubernetes serait un coût sans bénéfice à cette échelle.
3. **Une sauvegarde jamais restaurée n'est pas une sauvegarde** : la restauration se teste chaque mois.
4. **Toutes les alertes ne se valent pas** : critique pour l'astreinte, avertissement pour l'exploitation, signal métier pour l'équipe veille.
5. **Rétablir d'abord, comprendre ensuite** : chaque minute de panne consomme le budget d'erreur.

## 9. Questions pour la discussion

1. À partir de quel volume ou de quelle exigence Altéa devrait-elle passer à Kubernetes ?
2. Le service tourne sur un seul serveur : quel est le risque, et comment le réduire sans tout complexifier ?
3. Quels indicateurs présenter au comité de direction pour justifier le coût du service ?
