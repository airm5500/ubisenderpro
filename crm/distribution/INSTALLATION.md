# Installation du CRM UBIPHARM sur un poste Windows

Ce guide installe le CRM sur le poste serveur d'UBIPHARM CI. Le paquet
`UBIPHARM-CRM-<version>.zip` embarque Java et Node.js : seule la base MariaDB
s'installe a part. Deux services Windows tournent ensuite en permanence, et
redemarrent seuls avec le poste :

| Service | Role | Port |
| --- | --- | --- |
| UBIPHARM CRM | Application web (interface et envois e-mail, SMS) | 8080 (choisi a l'installation) |
| UBIPHARM CRM - WhatsApp | Compagnon WhatsApp Web, utilise par le CRM pour les relances WhatsApp | 3000 (interne) |

Poste conseille : Windows 10/11 64 bits ou Windows Server 2019 et plus, 8 Go de
memoire, 10 Go libres, allume en permanence pendant les heures d'envoi.

## Arborescence

```
C:\UBIPHARM-CRM\
  application\    remplace a chaque mise a jour (Java, Node, CRM, scripts)
  donnees\        cree a l'installation, jamais remplace :
    application-prod.yml   configuration du CRM (base, serveur e-mail)
    wa-web.env             configuration du compagnon WhatsApp
    wa-web-sessions\       session WhatsApp (evite de rescanner le QR code)
    logs\                  journaux des deux services
```

## 1. Installer MariaDB

1. Telecharger MariaDB Server 11.4 (LTS) pour Windows x64, format MSI, sur
   https://mariadb.org/download/.
2. Pendant l'installation : choisir un mot de passe `root` (a conserver en lieu
   sur), cocher **Use UTF8 as default server's character set**, laisser
   **Install as service** coche et le port 3306. Ne pas cocher l'acces root
   depuis d'autres machines.
3. Ouvrir **MySQL Client (MariaDB 11.4)** depuis le menu Demarrer, saisir le
   mot de passe root, puis creer la base et son utilisateur, en remplacant
   `MotDePasseBase` par un mot de passe robuste :

```sql
CREATE DATABASE ubicrm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'ubicrm'@'localhost' IDENTIFIED BY 'MotDePasseBase';
GRANT ALL ON ubicrm.* TO 'ubicrm'@'localhost';
```

Le CRM cree lui-meme ses tables au premier demarrage.

## 2. Extraire le paquet

Clic droit sur `UBIPHARM-CRM-<version>.zip` > **Extraire tout**, destination
`C:\`. On obtient `C:\UBIPHARM-CRM\application`.

Si Windows a bloque le fichier telecharge (clic droit > Proprietes >
**Debloquer**), le debloquer avant l'extraction.

## 3. Lancer l'installation

1. Dans `C:\UBIPHARM-CRM\application`, clic droit sur `installer.bat` >
   **Executer en tant qu'administrateur**.
2. Premiere execution : le script demande le port de l'interface web
   (Entree = 8080 ; s'il est deja pris sur le poste, saisir par exemple 8090),
   cree `C:\UBIPHARM-CRM\donnees` et ouvre
   `application-prod.yml` dans le Bloc-notes. Remplacer `A_RENSEIGNER` par le
   mot de passe `MotDePasseBase` de l'etape 1, enregistrer, fermer.
   Le jeton qui relie le CRM au compagnon WhatsApp est deja genere dans les deux
   fichiers : ne pas y toucher.
3. Relancer `installer.bat` en administrateur. Il installe et demarre les deux
   services, attend que le CRM reponde (jusqu'a 2 minutes la premiere fois) et
   affiche l'adresse.

## 4. Premiere connexion et reglages

Ouvrir http://localhost:8080 sur le poste (ou le port choisi a l'installation), se connecter avec le compte `admin`
et le mot de passe `Admin@2026` : le CRM demande aussitot d'en choisir un autre.
Ensuite, depuis l'interface :

- **Utilisateurs et habilitations** : creer les comptes de la Direction du
  Recouvrement, des agents et du marketing.
- **Numeros WhatsApp** : connecter le numero d'envoi en scannant le QR code
  avec le telephone (WhatsApp > Appareils connectes).
- **Fournisseurs SMS** : saisir la cle API LeTexto et le nom d'expediteur, puis
  **Envoyer l'essai** et **Consulter le solde**.
- **Parametres** : `Adresse HTTPS publique du CRM` (accuses de reception des
  SMS), plages horaires et plafonds d'envoi, expediteur des e-mails.
- **Imports** : charger les clients, les portefeuilles puis les factures.

## 5. Acces depuis les autres postes et en HTTPS

Le CRM est prevu pour etre servi derriere l'adresse HTTPS publique d'UBIPHARM :
le reverse proxy (ou la passerelle) qui porte le certificat redirige vers
`http://<nom-du-poste>:8080`. Dans ce cas :

- ouvrir le port 8080 au seul reverse proxy, en invite de commandes
  administrateur :
  `netsh advfirewall firewall add rule name="UBIPHARM CRM" dir=in action=allow protocol=TCP localport=8080`
  (ajouter `remoteip=<adresse du proxy>` pour restreindre) ;
- renseigner cette adresse publique dans **Parametres**.

Le port 3000 du compagnon WhatsApp ne doit pas etre ouvert : seul le CRM du
meme poste l'appelle.

Pour un acces en HTTP simple sur le reseau interne
(`http://<nom-du-poste>:8080`), sans HTTPS, mettre `secure: false` sous
`cookie:` dans `donnees\application-prod.yml`, puis redemarrer le service
**UBIPHARM CRM** : sinon la connexion echoue depuis les autres postes.

## 6. Serveur e-mail

Tant que le serveur SMTP n'est pas renseigne, le canal e-mail est signale
« non configure » et aucun e-mail ne part. Pour l'activer, dans
`donnees\application-prod.yml`, retirer les `#` du bloc `mail:` et renseigner
le serveur, le port, le compte et son mot de passe, puis redemarrer le service
**UBIPHARM CRM**.

## 7. Exploitation au quotidien

- **Services** : `services.msc`, lignes **UBIPHARM CRM** et
  **UBIPHARM CRM - WhatsApp** (Demarrer, Arreter, Redemarrer). En cas d'arret
  anormal, Windows les relance seul.
- **Journaux** : `C:\UBIPHARM-CRM\donnees\logs`. `crm.log` est le journal de
  l'application ; les fichiers `*-service.out.log` et `*.err.log` gardent la
  sortie des deux services.
- **Configuration** : apres toute modification de `application-prod.yml` ou de
  `wa-web.env`, redemarrer le service concerne.
- **Arret du poste** : un arret normal de Windows arrete proprement les deux
  services ; la session WhatsApp est conservee.

## 8. Sauvegarde

A sauvegarder chaque jour, hors du poste :

- la base, avec `mariadb-dump` (exemple de tache planifiee quotidienne, en
  adaptant le chemin de MariaDB et le dossier de destination) :
  ```
  "C:\Program Files\MariaDB 11.4\bin\mariadb-dump.exe" -u ubicrm -pMotDePasseBase --single-transaction --routines ubicrm > D:\Sauvegardes\ubicrm.sql
  ```
- le dossier `C:\UBIPHARM-CRM\donnees` (configuration et session WhatsApp).

## 9. Mise a jour

1. Sauvegarder la base (etape 8).
2. Dans l'ancien dossier `application`, lancer `desinstaller.bat` en
   administrateur : les services s'arretent, `donnees` et la base restent.
3. Renommer `C:\UBIPHARM-CRM\application` en `application-<ancienne version>`.
4. Extraire le nouveau paquet dans `C:\` (etape 2).
5. Lancer le nouvel `installer.bat` en administrateur : il reprend la
   configuration existante et met la base a jour au demarrage.

En cas de probleme, revenir a l'ancienne version en inversant les dossiers et
en relancant son `installer.bat` (avec la sauvegarde de la base si elle a deja
ete mise a jour).

## 10. Desinstallation

`desinstaller.bat` en administrateur arrete et supprime les deux services. Le
dossier `C:\UBIPHARM-CRM` et la base `ubicrm` restent en place : ils
contiennent l'historique des relances.

## Construire le paquet

Sur un poste Windows relie a Internet, avec Git pour Windows, depuis une copie
du depot :

```
powershell -ExecutionPolicy Bypass -File crm\distribution\construire-paquet-windows.ps1
```

Le script telecharge un JDK 25 Temurin, Node.js portable et WinSW (gardes dans
`crm\distribution\cache`), construit l'interface et le jar, reduit Java aux
modules utiles (jlink) et produit `crm\distribution\paquet\UBIPHARM-CRM-<version>.zip`.
Les options `-Jdk` et `-WinSW` evitent les telechargements quand ces outils sont
deja sur le poste. Les tests demandent une base MariaDB et se lancent a part
(`mvnw.cmd verify -Pit`).
