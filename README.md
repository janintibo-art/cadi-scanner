# 🛒 Cadi Scanner

Application Android native (Kotlin) pour calculer le total de son caddie :

- 📷 Photographiez l'étiquette prix du magasin
- 🔍 L'OCR (ML Kit, 100 % hors-ligne) détecte le prix automatiquement
- ➕ Les prix s'additionnent, avec quantité ×2, ×3… (boutons + / −)
- ✏️ Saisie manuelle possible si la photo ne passe pas
- 💾 Le caddie est sauvegardé même si on ferme l'appli

## Compilation automatique

À chaque `git push` sur `main`, GitHub Actions compile l'APK.
Récupérez-le dans l'onglet **Actions** → dernier build → **Artifacts** → `cadi-scanner-apk`.

## Commandes Termux

```bash
# 1. Préparation (une seule fois)
termux-setup-storage
pkg update -y && pkg install -y git unzip

# 2. Dézipper (le zip étant dans Téléchargements)
cd ~
unzip ~/storage/downloads/cadi-scanner.zip
cd cadi-scanner

# 3. Créer le dépôt git local
git init
git branch -M main
git add .
git -c user.name="VotrePseudo" -c user.email="vous@mail.com" commit -m "Premier commit"

# 4. Créer le dépôt sur GitHub (via le site github.com → New repository,
#    nom : cadi-scanner, SANS cocher "Add a README") puis :
git remote add origin https://github.com/VOTRE_PSEUDO/cadi-scanner.git
git push -u origin main
# Identifiant : votre pseudo GitHub
# Mot de passe : un token PAT (github.com → Settings → Developer settings
#                → Personal access tokens → Generate, cocher "repo")
```

Option plus simple avec l'outil GitHub CLI :

```bash
pkg install -y gh
gh auth login          # suivre les instructions
gh repo create cadi-scanner --public --source=. --push
```

## Installer l'APK

1. Onglet **Actions** du dépôt → attendre le ✅ (~3-5 min)
2. Cliquer sur le build → télécharger l'artifact `cadi-scanner-apk` (un zip)
3. Le dézipper : `app-debug.apk`
4. Ouvrir l'APK sur le téléphone et autoriser les sources inconnues
