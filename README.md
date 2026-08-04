# 🛒 Cadi Scanner

Application Android native (Kotlin) pour suivre le total de son caddie **et** comparer les prix.

## Fonctions

**Nouveau (lot 1)**
- 🔎 **Scan continu** : la caméra reste ouverte, on enchaîne les articles sans revenir en arrière
- 🎯 **Budget plafond** : total vert → orange à 80 % → rouge + vibration au dépassement
- 🔊 **Bip + vibration** à chaque code reconnu (pas besoin de regarder l'écran)
- ↩️ **Annuler** la dernière action via le bandeau qui apparaît en bas


- 🔎 **Scan du code-barres** → nom du produit, **prix moyen relevé**, **prix le plus bas connu**, et **le moins cher dans un rayon de 20 km** autour de vous
- 📷 **Photo de l'étiquette** → l'OCR détecte le prix (hors-ligne, ML Kit)
- ➕ Quantités ×2, ×3… avec boutons + / −, total en direct
- ✏️ Saisie manuelle en secours
- 💾 Caddie sauvegardé entre deux ouvertures

## ⚠️ Sur le comparatif de prix — à lire

Aucune enseigne (Leclerc, Carrefour, Lidl, Intermarché…) ne publie ses prix rayon
via une API gratuite. Le comparatif s'appuie donc sur **Open Prices**
(https://prices.openfoodfacts.org), base **collaborative** alimentée par les
utilisateurs, sous licence **ODbL**.

Conséquences concrètes :

- beaucoup de produits n'ont **aucun relevé** → l'app bascule sur la saisie manuelle
- les relevés existants peuvent être **anciens** (la date est affichée)
- la couverture géographique est inégale selon les régions
- ce n'est **pas** un prix officiel : toujours vérifier en rayon

Plus il y a de contributeurs, meilleure est la base : vous pouvez ajouter vos
propres relevés sur prices.openfoodfacts.org ou via l'appli Open Food Facts.

Sources de données : Open Food Facts (noms produits, ODbL) et Open Prices (prix, ODbL).

## Commandes Termux (connexion par navigateur)

```bash
# Installation
pkg update -y && pkg install -y gh git unzip
termux-setup-storage

# Connexion GitHub via le web
gh auth login
#  → GitHub.com / HTTPS / Y / Login with a web browser
#  → saisir le code affiché sur github.com/login/device

# Projet
cd ~ && unzip ~/storage/downloads/cadi-scanner.zip -d cadi-scanner
cd cadi-scanner
git init && git branch -M main
git config --global user.name "TonPseudo"
git config --global user.email "toi@mail.com"
git add . && git commit -m "Premier commit"

# Création du dépôt + push en une commande
gh repo create cadi-scanner --public --source=. --push

# Suivi de la compilation
gh run watch
```

## Récupérer l'APK

Onglet **Actions** du dépôt → dernier build ✅ → Artifacts → `cadi-scanner-apk`
→ dézipper → installer `app-debug.apk` (autoriser les sources inconnues).

Ou en ligne de commande : `gh run download -n cadi-scanner-apk`
