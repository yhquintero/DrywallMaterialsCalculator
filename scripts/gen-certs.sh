#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Genera una CA local + certificado de servidor para desarrollar la consola
# en HTTPS real (https://localhost:8443 y https://localhost:3000).
#
#   ./scripts/gen-certs.sh                 # localhost
#   HOSTS="consola.local,192.168.1.50" ./scripts/gen-certs.sh
#
# Salida (ignorada por git):
#   server/certs/ca.crt          ← instala esta CA en tu SO/navegador
#   server/certs/server.crt
#   server/certs/server.key
#   web/certs/dev.crt / dev.key  ← los usa `npm run dev:https`
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if command -v node >/dev/null 2>&1; then
  exec node "$ROOT/scripts/gen-certs.mjs" "$@"
fi

SERVER_CERTS="$ROOT/server/certs"
WEB_CERTS="$ROOT/web/certs"
DAYS="${DAYS:-825}"
HOSTS="${HOSTS:-localhost,127.0.0.1,::1}"

mkdir -p "$SERVER_CERTS" "$WEB_CERTS"

if ! command -v openssl >/dev/null 2>&1; then
  echo "✗ Se necesita openssl instalado." >&2
  exit 1
fi

# SAN list: nombres → DNS, IPs → IP
SAN=""
IFS=',' read -ra ITEMS <<< "$HOSTS"
for item in "${ITEMS[@]}"; do
  item="$(echo "$item" | xargs)"
  [[ -z "$item" ]] && continue
  if [[ "$item" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || [[ "$item" == *:* ]]; then
    SAN="$SAN,IP:$item"
  else
    SAN="$SAN,DNS:$item"
  fi
done
SAN="${SAN#,}"

echo "→ SAN: $SAN"

# 1) CA local
if [[ ! -f "$SERVER_CERTS/ca.key" ]]; then
  openssl genrsa -out "$SERVER_CERTS/ca.key" 4096
  openssl req -x509 -new -nodes -key "$SERVER_CERTS/ca.key" -sha256 -days 3650 \
    -subj "/C=CU/O=DrywallPro Master/CN=DrywallPro Local CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -out "$SERVER_CERTS/ca.crt"
  echo "✔ CA local creada: $SERVER_CERTS/ca.crt"
else
  echo "ℹ CA local ya existe, se reutiliza."
fi

gen_leaf() {
  local outdir="$1" name="$2" cn="$3"
  mkdir -p "$outdir"
  openssl genrsa -out "$outdir/$name.key" 2048
  openssl req -new -key "$outdir/$name.key" -subj "/C=CU/O=DrywallPro Master/CN=$cn" \
    -out "$outdir/$name.csr"
  cat > "$outdir/$name.ext" <<EOF
basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=$SAN
EOF
  openssl x509 -req -in "$outdir/$name.csr" -CA "$SERVER_CERTS/ca.crt" -CAkey "$SERVER_CERTS/ca.key" \
    -CAcreateserial -out "$outdir/$name.crt" -days "$DAYS" -sha256 -extfile "$outdir/$name.ext"
  rm -f "$outdir/$name.csr" "$outdir/$name.ext"
  echo "✔ Certificado: $outdir/$name.crt (+ $name.key)"
}

gen_leaf "$SERVER_CERTS" "server" "localhost"
gen_leaf "$WEB_CERTS" "dev" "localhost"

chmod 600 "$SERVER_CERTS"/*.key "$WEB_CERTS"/*.key 2>/dev/null || true

cat <<EOF

────────────────────────────────────────────────────────────
 Certificados listos (carpetas ignoradas por git)

 Para que el navegador confíe en https://localhost instala la CA:
   • Linux   : cp server/certs/ca.crt /usr/local/share/ca-certificates/drywallpro-ca.crt && sudo update-ca-certificates
   • macOS   : sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain server/certs/ca.crt
   • Windows : certutil -addstore -f ROOT server\\certs\\ca.crt
   • Chrome/Edge: Ajustes → Privacidad y seguridad → Seguridad → Gestionar certificados → Autoridades → Importar

 Arrancar todo en HTTPS:
   npm run dev:https        (web + API con TLS, desde la raíz del repo)
────────────────────────────────────────────────────────────
EOF
