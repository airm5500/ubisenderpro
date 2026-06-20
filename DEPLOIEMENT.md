# Guide de déploiement — UbiSenderPro sur Payara Server 5

Ce guide décrit, étape par étape, le déploiement d'UbiSenderPro sur **Payara Server 5**
avec **MariaDB**. Il est aligné sur la configuration livrée :

| Élément | Valeur |
|---|---|
| Artefact | `ubisenderpro.war` |
| Context root | `/ubisenderpro` |
| Base de données | `ubisenderpro_db` |
| Datasource (JNDI) | `UbiSenderProDS` |
| Pool de connexions | `UbiSenderProPool` |
| Unité de persistance | `ubisenderproPU` |
| Pilote JDBC | `org.mariadb.jdbc.MariaDbDataSource` (mariadb-java-client 2.7.4) |
| Port HTTP par défaut | `8080` (admin : `4848`) |

---

## 1. Prérequis

- **JDK 11** (Temurin/Adoptium, Zulu ou OpenJDK).
- **Payara Server 5** (5.2022.x recommandé) — édition *Full*, pas *Micro*.
- **MariaDB 10.3+** (ou MySQL 5.7+).
- **Maven 3.6+** pour construire le WAR.
- **SDK Ext JS 4.2** (fichiers `ext-all.js` et `resources/css/ext-all.css`).

Vérifier Java :

```bash
java -version      # doit afficher 11.x
```

> Le projet compile en Java 11. Payara 5 tourne sur JDK 8 ou 11 ; utilisez **JDK 11**.

---

## 2. Préparer MariaDB

### 2.1 Créer la base et l'utilisateur

```sql
CREATE DATABASE ubisenderpro_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'ubisenderpro'@'localhost' IDENTIFIED BY 'ubisenderpro';
GRANT ALL PRIVILEGES ON ubisenderpro_db.* TO 'ubisenderpro'@'localhost';
FLUSH PRIVILEGES;
```

> Adaptez l'utilisateur/mot de passe ; reportez les mêmes valeurs dans le pool (étape 5).

### 2.2 Schéma : deux options

- **Option A — automatique (Flyway, par défaut).** Ne rien faire ici : au premier
  démarrage, l'application crée les 41 tables et insère les données de référence
  (migrations `V1`→`V7`), puis hache le mot de passe admin.
- **Option B — manuelle (hors Flyway).** Charger le script complet, puis désactiver Flyway :

```bash
mysql -u ubisenderpro -p ubisenderpro_db < src/main/resources/db/install/ubisenderpro_install.sql
# au lancement de Payara : export UBISENDERPRO_SKIP_FLYWAY=true   (voir étape 7)
```

---

## 3. Installer Payara

```bash
cd /opt
sudo unzip payara-5.2022.5.zip          # -> /opt/payara5
export PAYARA_HOME=/opt/payara5
export PATH=$PAYARA_HOME/bin:$PATH       # rend 'asadmin' accessible
```

Démarrer le domaine par défaut :

```bash
asadmin start-domain domain1
```

Consoles :
- Application : `http://SERVEUR:8080`
- Administration : `http://SERVEUR:4848`

> Première connexion admin : définir un mot de passe via
> `asadmin change-admin-password` puis `asadmin enable-secure-admin` si l'admin est exposé.

---

## 4. Pilote JDBC

> **Vous déployez sur la Payara qui héberge déjà Prestige ?** Le pilote
> `mysql-connector-java` y est déjà présent. Le `glassfish-resources.xml` livré utilise
> par défaut `com.mysql.jdbc.jdbc2.optional.MysqlDataSource` (driver MySQL, compatible
> MariaDB) : **rien à installer**, passez à l'étape 5. Pour utiliser le pilote MariaDB
> officiel à la place, voir l'option alternative commentée dans le fichier et l'étape ci-dessous.

Si le pilote n'est pas présent côté serveur, il doit y être ajouté (scope `provided`,
non embarqué dans le WAR) :

```bash
# Télécharger mariadb-java-client-2.7.4.jar puis :
cp mariadb-java-client-2.7.4.jar $PAYARA_HOME/glassfish/domains/domain1/lib/
asadmin restart-domain domain1
```

> Le dossier `domains/domain1/lib/` est chargé par le classloader commun du domaine,
> ce qui rend le pilote visible pour le pool de connexions.

---

## 5. Créer la datasource `UbiSenderProDS`

Deux méthodes, **au choix**.

### 5.1 Méthode A — automatique via le WAR (recommandée)

Le projet embarque `src/main/webapp/WEB-INF/glassfish-resources.xml`. À chaque déploiement,
Payara crée automatiquement le pool `UbiSenderProPool` et la ressource `UbiSenderProDS`.
**Rien à faire** ici si les valeurs (url/user/password) du fichier conviennent ;
sinon, éditez-les avant de construire le WAR.

### 5.2 Méthode B — manuelle via asadmin

```bash
asadmin create-jdbc-connection-pool \
  --datasourceclassname org.mariadb.jdbc.MariaDbDataSource \
  --restype javax.sql.DataSource \
  --property user=ubisenderpro:password=ubisenderpro:url="jdbc\\:mariadb\\://localhost\\:3306/ubisenderpro_db?useUnicode\\=true\\&characterEncoding\\=UTF-8" \
  UbiSenderProPool

asadmin create-jdbc-resource --connectionpoolid UbiSenderProPool UbiSenderProDS
```

> Dans `asadmin`, les caractères `:` `=` `&` à l'intérieur de l'URL doivent être échappés
> par `\\`. Adaptez hôte/port/identifiants.

### 5.3 Tester le pool

```bash
asadmin ping-connection-pool UbiSenderProPool
# Réponse attendue : Command ping-connection-pool executed successfully.
```

Si le ping échoue : vérifier que MariaDB tourne, que le pilote est dans `lib/`,
et que l'URL/identifiants sont corrects.

---

## 6. Construire le WAR

### 6.1 Ajouter le SDK Ext JS (front)

Le dossier `ext/` est ignoré par Git. Réutilisez le SDK **Ext JS 4.2 déjà présent dans
Prestige** : copiez tout le dossier

```text
prestige/src/main/webapp/general/ext   ->   ubisenderpro/src/main/webapp/ext
```

`index.html` charge `ext/ext-all.js` et le thème Neptune compilé
(`ext/packages/ext-theme-neptune/build/resources/ext-theme-neptune-all.css`).

> Sans ce dossier, le backend REST fonctionne mais l'interface affiche un message
> d'erreur explicite (au lieu d'une page blanche).

Exemple de copie (Windows) :

```bat
xcopy /E /I D:\projet\prestige\src\main\webapp\general\ext D:\projet\ubisenderpro\src\main\webapp\ext
```

### 6.2 Packager

```bash
mvn clean package
# -> target/ubisenderpro.war
```

---

## 7. Déployer

### 7.1 (Option B uniquement) désactiver Flyway

Si la base a été provisionnée manuellement (étape 2.2 option B), démarrez Payara avec
la variable d'environnement **avant** le déploiement :

```bash
asadmin stop-domain domain1
export UBISENDERPRO_SKIP_FLYWAY=true
asadmin start-domain domain1
# Variante JVM (persistant) :
# asadmin create-jvm-options -Dubisenderpro.skipFlyway=true
```

### 7.2 Déploiement en ligne de commande

```bash
asadmin deploy --contextroot /ubisenderpro --name ubisenderpro target/ubisenderpro.war
```

Ou via la console d'admin : **Applications → Deploy → ** sélectionner le WAR,
context root `/ubisenderpro`.

### 7.3 Vérifier le démarrage

```bash
tail -f $PAYARA_HOME/glassfish/domains/domain1/logs/server.log
```

Messages attendus :
- `UbiSenderPro : migrations Flyway appliquées.` (option A), ou
  `migrations Flyway désactivées` (option B) ;
- `UbiSenderPro : mot de passe admin initialisé.` (premier démarrage, option A).

---

## 8. Première connexion

Ouvrir `http://SERVEUR:8080/ubisenderpro`.

- Identifiant : `admin`
- Mot de passe : `Admin@2026`

**Changez ce mot de passe** après la première connexion.

Test rapide de l'API (sans l'UI) :

```bash
curl -s -X POST http://localhost:8080/ubisenderpro/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"admin","motDePasse":"Admin@2026"}'
# -> { "token": "...", "user": {...} }
```

---

## 9. Exposer le webhook WhatsApp en HTTPS

La Cloud API de Meta exige une URL de webhook **publique en HTTPS**. Payara écoute en HTTP
sur 8080 : placez un reverse proxy TLS devant.

### Exemple Nginx

```nginx
server {
    listen 443 ssl;
    server_name crm.mondomaine.com;

    ssl_certificate     /etc/letsencrypt/live/crm.mondomaine.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/crm.mondomaine.com/privkey.pem;

    location /ubisenderpro/ {
        proxy_pass http://127.0.0.1:8080/ubisenderpro/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Dans la console Meta (produit WhatsApp → Configuration → Webhooks) :
- **URL de rappel** : `https://crm.mondomaine.com/ubisenderpro/api/v1/webhooks/whatsapp`
- **Jeton de vérification** : la valeur `verifyToken` saisie dans l'écran *Paramètres → Comptes WhatsApp*
- **Champs abonnés** : `messages`

Meta appelle d'abord `GET` (défi de vérification), auquel l'application répond
automatiquement si le verify token correspond.

---

## 10. Démarrage automatique (systemd)

`/etc/systemd/system/payara.service` :

```ini
[Unit]
Description=Payara Server
After=network.target mariadb.service

[Service]
Type=forking
User=payara
Environment="UBISENDERPRO_SKIP_FLYWAY=false"
ExecStart=/opt/payara5/bin/asadmin start-domain domain1
ExecStop=/opt/payara5/bin/asadmin stop-domain domain1
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now payara
```

---

## 11. Mettre à jour / redéployer

```bash
mvn clean package
asadmin redeploy --name ubisenderpro target/ubisenderpro.war
```

> Les migrations Flyway non encore appliquées (nouvelles versions `Vx`) s'exécutent
> automatiquement au redéploiement, sauf si Flyway est désactivé.

Désinstaller :

```bash
asadmin undeploy ubisenderpro
```

---

## 12. Production : recommandations

- **Sécuriser l'admin** : `asadmin enable-secure-admin`, mot de passe fort, port 4848 non exposé.
- **Tokens WhatsApp** : utilisez un *access token* permanent (utilisateur système Meta) ;
  ne le committez jamais. Stockage en base via l'écran Paramètres.
- **HTTPS** obligatoire pour le webhook et pour l'UI (cookies/jeton de session).
- **Sauvegardes** : `mysqldump ubisenderpro_db` planifié.
- **Dimensionner le pool** : ajuster `--steadypoolsize` / `--maxpoolsize` selon la charge.
- **Journaux** : `domains/domain1/logs/server.log` ; activez la rotation.
- **Fuseau horaire** : alignez l'OS, MariaDB et la JVM (les campagnes programmées en dépendent).

---

## 13. Dépannage

| Symptôme | Cause probable | Action |
|---|---|---|
| `ping-connection-pool` échoue | Pilote absent ou URL/identifiants erronés | Vérifier `domains/domain1/lib/`, l'URL, l'utilisateur MariaDB |
| `Class not found: org.mariadb.jdbc.MariaDbDataSource` | Pilote non installé | Copier le jar dans `lib/` puis `restart-domain` |
| `ClassNotFoundException: org.hibernate.jpa.HibernatePersistenceProvider` | `persistence.xml` forçait Hibernate (absent de Payara) | Corrigé : le projet utilise désormais **EclipseLink** (provider natif Payara). Reconstruire le WAR |
| Déploiement échoue sur la préparation JPA | Schéma incomplet/différent | Laisser Flyway créer le schéma, ou recharger le script d'install |
| Migrations rejouées en erreur après install manuel | Flyway non désactivé | `export UBISENDERPRO_SKIP_FLYWAY=true` |
| Interface blanche | SDK Ext JS absent | Déposer `ext/` puis reconstruire le WAR |
| `401` sur les API | Jeton manquant/expiré | Se reconnecter ; en-tête `Authorization: Bearer <token>` |
| Webhook non vérifié par Meta | Verify token différent ou pas de HTTPS | Aligner le verify token ; exposer en HTTPS |
| Messages sortants en `ECHOUE` | Token Meta invalide, hors fenêtre 24h, ou modèle non approuvé | Vérifier le token, utiliser un modèle approuvé pour le hors-fenêtre |

---

Pour la configuration applicative (API REST, WhatsApp, imports), voir `README.md`.
