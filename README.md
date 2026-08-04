# 🛒 Cadi Scanner

Application Android native (Kotlin) pour suivre le total de son caddie **et** comparer les prix.

## Fonctions

**Nouveau (lot 3)**
- 🧠 **Mémoire de vos prix** : l'app retient ce que *vous* avez payé, où et quand. Au scan : « Vous l'avez payé 2,49 € chez Lidl le 12/07 », avec vos min/max/moyenne et une alerte si le prix a grimpé de plus de 10 %
- 📝 **Liste de courses** : préparez-la à l'avance, les articles se **cochent tout seuls** quand vous les scannez. Bandeau de rappel sur l'écran principal
- ⭐ **Suggestions** basées sur vos achats les plus fréquents
- 🌍 **Contribuer à Open Prices** : bouton qui copie le relevé et ouvre le site

> Note sur la contribution : l'envoi direct par l'API demande un compte Open Food Facts,
> un jeton Bearer et l'upload d'une photo de preuve. Le format exact du POST n'est pas
> documenté publiquement (il est dans le Swagger généré sur `/api/docs`), donc l'app
> passe pour l'instant par le site plutôt que d'utiliser des endpoints devinés.

**Lot 2**
- 📊 **Historique des courses** : chaque caddie validé est archivé (date, magasin, détail), avec graphique des 6 derniers mois et panier moyen
- 🏷️ **Détection des lots promo** : « 2 achetés = 1 offert », « 2ᵉ à -50 % », « les 3 pour 5 € », « -30 % » → le vrai prix payé est calculé et l'économie affichée
- ⚖️ **Prix au kilo distingué** du prix de vente : plus de confusion entre 3,49 €/kg et le prix unitaire
- 📤 **Export** de la liste en texte (WhatsApp, SMS) ou en CSV (tableur)

**Lot 1**
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
