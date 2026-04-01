# Presentation Du Projet

## 1. Intitule du projet

Application web de gestion d'archives et de transmission de dossiers pour BTK Finance.

## 2. Contexte et objectif

Ce projet a pour objectif de digitaliser la gestion des archives physiques et le cycle de vie des dossiers au sein de l'entreprise. L'application permet de centraliser la consultation, la recherche, la demande, la validation, le suivi, la restitution et le rappel des dossiers, tout en assurant une meilleure tracabilite des operations.

L'objectif principal est de remplacer une gestion manuelle ou dispersee par une plateforme unique, fiable et plus rapide a utiliser.

## 3. Technologies utilisees

### Backend

- Java 11
- Jakarta EE 9.1
- CDI pour la gestion des beans
- JPA / Hibernate 6 pour l'acces aux donnees
- Oracle Database avec datasource `java:/BTKDS`

### Frontend

- JSF Facelets
- PrimeFaces 13
- XHTML
- CSS personnalise
- JavaScript pour les interactions dynamiques

### Outils et infrastructure

- Maven pour la gestion du projet et des dependances
- WildFly / Jakarta EE Server pour le deploiement
- Apache POI pour les exports Excel
- OpenPDF pour la generation de documents PDF

## 4. Fonctionnalites principales

- Authentification utilisateur avec gestion de session
- Gestion des utilisateurs et des roles
- Tableau de bord avec statistiques globales
- Gestion des archives et des dossiers
- Gestion des boites et des emplacements
- Consultation des archives et des boites
- Creation et traitement des demandes de dossier
- Validation ou refus des demandes par l'administration
- Suivi des demandes et des dossiers restitues
- Notifications des nouvelles demandes, validations, refus et restitutions
- Envoi de rappels pour les dossiers non restitues
- Editions et export des donnees

## 5. Architecture generale

Le projet repose sur une architecture web Java classique et robuste :

- Couche presentation : pages XHTML avec JSF/PrimeFaces
- Couche metier : beans CDI pour la logique applicative
- Couche persistance : JPA/Hibernate connecte a Oracle
- Couche serveur : deploiement sur WildFly

Cette architecture permet une bonne separation des responsabilites entre l'interface, la logique metier et la base de donnees.

## 6. Points forts du projet

### Points forts fonctionnels

- Couverture complete du processus de transmission de dossiers
- Suivi clair des etats : en attente, approuve, refuse, restitue
- Notifications integrees pour ameliorer la reactivite des utilisateurs
- Gestion de rappels pour renforcer le controle des restitutions
- Differenciation des acces selon les profils et les roles

### Points forts techniques

- Utilisation d'un stack Jakarta moderne et professionnel
- Interface riche grace a PrimeFaces
- Integration avec Oracle adaptee a un contexte d'entreprise
- Structure modulaire avec beans separes par fonctionnalite
- Possibilite d'evolution facile vers de nouvelles fonctionnalites
- Gestion des erreurs de session avec redirection propre en cas de `ViewExpiredException`

### Points forts pour l'entreprise

- Gain de temps dans la recherche et le traitement des dossiers
- Reduction des erreurs manuelles
- Amelioration de la tracabilite
- Meilleure visibilite sur les demandes en cours
- Centralisation des operations dans une seule application

## 7. Valeur ajoutee du projet

Cette application apporte une vraie valeur metier car elle ne se limite pas a stocker des informations. Elle structure tout le cycle de gestion documentaire : consultation, demande, decision, suivi, notification et restitution.

Elle contribue donc a :

- mieux organiser les archives,
- mieux suivre les responsabilites,
- mieux controler les mouvements des dossiers,
- et moderniser les processus internes.

## 8. Conclusion

Le projet BTK Finance - Gestion des Archives et Transmission des Dossiers est une application web metier complete, construite avec des technologies solides et adaptees au monde de l'entreprise. Il repond a un besoin concret d'organisation, de suivi et de securisation des dossiers.

Ce projet montre a la fois :

- une maitrise des technologies Java web,
- une bonne comprehension du besoin metier,
- et une capacite a construire une solution utile, evolutive et professionnalisee.

## 9. Resume court pour presentation orale

J'ai realise une application web de gestion d'archives et de transmission de dossiers pour BTK Finance. Le projet a ete developpe avec Java, Jakarta EE, JSF, PrimeFaces, Hibernate et Oracle. Il permet de gerer les archives, les demandes de dossiers, les validations administratives, les notifications, les rappels et les statistiques. Les points forts du projet sont la centralisation des operations, la tracabilite des mouvements, la gestion par roles et une interface metier adaptee aux besoins de l'entreprise.
