#!/bin/bash
# Suite complete des 5 niveaux de la strategie de tests, a lancer a chaque livraison.
# Prerequis : MariaDB demarree avec les bases ubicrm_test (niveau 4) et ubicrm_e2e (niveaux 2, 3, 5),
# accessibles a l'utilisateur ubicrm. La base ubicrm_e2e est videe puis recreee par Flyway : jamais la production.
#   JAVA_HOME     : JDK 25
#   E2E_PORT      : port de l'instance de test (defaut 8081)
#   E2E_CHROMIUM  : Chromium deja installe (facultatif)
set -u
cd "$(dirname "$0")"
PORT=${E2E_PORT:-8081}
JOURNAL=target/tests-complets
mkdir -p "$JOURNAL"
declare -A RESULTAT

compter() { # somme des attributs tests/failures/errors/skipped d'un dossier de rapports JUnit
  python3 - "$1" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
t = f = s = 0
for p in glob.glob(sys.argv[1] + '/TEST-*.xml'):
    r = ET.parse(p).getroot()
    t += int(r.get('tests', 0)); f += int(r.get('failures', 0)) + int(r.get('errors', 0)); s += int(r.get('skipped', 0))
print(f"{t - f - s} reussis, {f} en echec" + (f", {s} ignores" if s else ""))
PY
}

echo "== Niveaux 1 et 4 : unitaires (JUnit) et integration (MariaDB reelle, Flyway)"
./mvnw -B -q verify -Pit > "$JOURNAL/maven.log" 2>&1
RESULTAT[1]="serveur $(compter target/surefire-reports)"
RESULTAT[4]="$(compter target/failsafe-reports)"

echo "== Niveau 1 : unitaires du front (Vitest)"
if (cd front && NO_COLOR=1 npx ng test --watch=false > "../$JOURNAL/front.log" 2>&1); then ETAT=ok; else ETAT=echec; fi
FRONT=$(grep -Eo 'Tests +[0-9]+ passed( \| [0-9]+ failed)?' "$JOURNAL/front.log" | tail -1 | grep -Eo '[0-9]+' | paste -sd' ')
RESULTAT[1]="${RESULTAT[1]} ; front ${FRONT%% *} reussis ($ETAT)"

echo "== Niveaux 2, 3 et 5 : Playwright contre une instance neuve sur le port $PORT"
./mvnw -B -q package -DskipTests > "$JOURNAL/package.log" 2>&1
mysql -e "DROP DATABASE IF EXISTS ubicrm_e2e; CREATE DATABASE ubicrm_e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
CRM_DB_URL=jdbc:mariadb://localhost:3306/ubicrm_e2e "$JAVA_HOME/bin/java" -jar target/ubipharm-crm-0.1.0-SNAPSHOT.jar \
  --server.port="$PORT" > "$JOURNAL/instance-e2e.log" 2>&1 &
INSTANCE=$!
trap 'kill $INSTANCE 2>/dev/null' EXIT
for i in $(seq 1 80); do curl -s -o /dev/null -w "%{http_code}" "localhost:$PORT/api/auth/csrf" | grep -q 200 && break; sleep 1.5; done
(cd e2e && [ -d node_modules ] || npm ci --no-audit --no-fund > /dev/null)
for projet in api ui saisies; do
  (cd e2e && E2E_BASE_URL="http://localhost:$PORT/" npx playwright test --project=$projet --reporter=list > "../$JOURNAL/e2e-$projet.log" 2>&1)
  ligne=$(grep -Eo '[0-9]+ (passed|failed|flaky|skipped)' "$JOURNAL/e2e-$projet.log" | sed 's/passed/reussis/;s/failed/en echec/;s/skipped/ignores/' | paste -sd, -)
  case $projet in api) RESULTAT[2]="$ligne";; ui) RESULTAT[3]="$ligne";; saisies) RESULTAT[5]="$ligne";; esac
done

echo
echo "Resultat par niveau (journaux dans $JOURNAL)"
echo "1. Unitaires et non-regression : ${RESULTAT[1]}"
echo "2. E2E API                     : ${RESULTAT[2]}"
echo "3. E2E interface               : ${RESULTAT[3]}"
echo "4. Integration                 : ${RESULTAT[4]}"
echo "5. UI/UX et saisies            : ${RESULTAT[5]}"
grep -Eq "(^|[^0-9])[1-9][0-9]* en echec|\(echec\)" <<< "${RESULTAT[*]}" && exit 1 || exit 0
