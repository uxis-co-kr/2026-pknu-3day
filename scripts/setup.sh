#!/usr/bin/env bash
#
# 처음 클론한 PC 를 한 번에 세팅한다. 여러 번 돌려도 안전하다 (이미 된 것은 건너뛴다).
#
#   ./scripts/setup.sh            전부
#   ./scripts/setup.sh --skip-db  DB 는 이미 돌고 있다
#   ./scripts/setup.sh --quick    의존성 내려받기·미리 빌드를 건너뛴다
#
# 하는 일: 사전 요구사항 확인 → .env 만들고 비밀값 생성 → DB 기동 → 의존성 설치 →
#          백엔드 미리 빌드. 끝나면 무엇을 치면 되는지 알려 준다.
set -euo pipefail
cd "$(dirname "$0")/.."

SKIP_DB=0
QUICK=0
for arg in "$@"; do
  case "$arg" in
    --skip-db) SKIP_DB=1 ;;
    --quick) QUICK=1 ;;
    -h|--help) sed -n '3,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "모르는 옵션: $arg (--help 로 사용법)"; exit 2 ;;
  esac
done

bold() { printf '\n\033[1m▸ %s\033[0m\n' "$1"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$1"; }
die()  { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }

# ── 1. 사전 요구사항 ────────────────────────────────────────────────────────
bold "사전 요구사항"

command -v docker >/dev/null 2>&1 || die "Docker 가 없다. Docker Desktop 을 깐다: https://docker.com/products/docker-desktop"
docker info >/dev/null 2>&1 || die "Docker 데몬이 꺼져 있다. Docker Desktop 을 켜고 다시 돌린다."
docker compose version >/dev/null 2>&1 || die "docker compose v2 가 필요하다 (Docker Desktop 최신판이면 들어 있다)."
ok "Docker $(docker version --format '{{.Server.Version}}' 2>/dev/null || echo '')"

command -v node >/dev/null 2>&1 || die "Node.js 가 없다. 20 이상을 깐다: https://nodejs.org"
NODE_MAJOR=$(node -p 'process.versions.node.split(".")[0]')
[ "$NODE_MAJOR" -ge 20 ] || die "Node 20 이상이 필요하다 (지금 $(node -v)). Vite 8 이 그 아래에서 돌지 않는다."
ok "Node $(node -v)"

# Gradle 은 JDK 21 위에서만 돈다. 맥에는 여러 JDK 가 깔려 있기 쉬워 여기서 짚어 준다.
java_major() { "$1" -version 2>&1 | awk -F'"' '/version/ {split($2,v,"."); print (v[1]=="1")? v[2] : v[1]; exit}'; }
JDK_OK=0
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && [ "$(java_major "$JAVA_HOME/bin/java")" = "21" ]; then
  JDK_OK=1
  ok "JDK 21 (JAVA_HOME)"
elif command -v /usr/libexec/java_home >/dev/null 2>&1 && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
  warn "JDK 21 은 깔려 있지만 JAVA_HOME 이 다른 곳을 본다. 셸 설정(~/.zshrc)에 넣어 둔다:"
  printf '      export JAVA_HOME=$(/usr/libexec/java_home -v 21)\n'
elif command -v java >/dev/null 2>&1 && [ "$(java_major java)" = "21" ]; then
  JDK_OK=1
  ok "JDK 21 (java)"
else
  warn "JDK 21 이 안 보인다. 없으면 백엔드가 뜨지 않는다 — Temurin 21 또는 Corretto 21 을 깐다."
fi

# ── 2. 환경 파일 ────────────────────────────────────────────────────────────
bold "환경 파일"

copy_example() { # $1=예시 파일 $2=실제 파일
  if [ -f "$2" ]; then ok "$2 (이미 있다)"; else cp "$1" "$2"; ok "$2 를 만들었다"; fi
}
copy_example .env.example .env
copy_example backend/.env.example backend/.env

# JWT_SECRET / ENCRYPTION_KEY 는 비면 백엔드가 기동 중에 멈춘다. 비어 있을 때만 만들어 넣는다 —
# 이미 값이 있는데 새로 만들면 발급해 둔 로그인 토큰과 저장된 GitHub 토큰이 한꺼번에 죽는다.
fill_secret() { # $1=파일 $2=키 $3=바이트수
  local file=$1 key=$2 bytes=$3 current
  current=$(sed -n "s/^${key}=//p" "$file" | head -1)
  if [ -n "$current" ]; then ok "$key (이미 있다)"; return; fi
  command -v openssl >/dev/null 2>&1 || die "openssl 이 없어 $key 를 만들 수 없다. .env 에 직접 적는다."
  local value; value=$(openssl rand -base64 "$bytes")
  if grep -q "^${key}=" "$file"; then
    sed "s|^${key}=.*|${key}=${value}|" "$file" > "$file.tmp" && mv "$file.tmp" "$file"
  else
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
  ok "$key 를 새로 만들어 넣었다"
}
fill_secret backend/.env JWT_SECRET 48
fill_secret backend/.env ENCRYPTION_KEY 32

# ── 3. DB ───────────────────────────────────────────────────────────────────
if [ "$SKIP_DB" = "1" ]; then
  bold "DB (건너뜀)"
else
  bold "DB"
  PORT=$(sed -n 's/^POSTGRES_PORT=//p' .env | head -1)
  PORT=${PORT:-5433}
  docker compose up -d >/dev/null
  printf '  기동을 기다린다'
  healthy=""
  for _ in $(seq 1 30); do
    healthy=$(docker inspect -f '{{.State.Health.Status}}' worklog-postgres 2>/dev/null || echo '')
    [ "$healthy" = "healthy" ] && break
    printf '.'; sleep 2
  done
  printf '\n'
  [ "$healthy" = "healthy" ] || die "postgres 가 뜨지 않았다. docker compose logs postgres 를 본다."
  ok "postgres 준비됨 (127.0.0.1:$PORT)"
fi

# ── 4. 의존성 ───────────────────────────────────────────────────────────────
if [ "$QUICK" = "1" ]; then
  bold "의존성 (건너뜀)"
else
  bold "의존성"
  # package-lock.json 이 있으면 ci 가 빠르고 결과가 같다. node_modules 가 이미 있으면 건드리지 않는다.
  install_deps() { # $1=폴더
    if [ -d "$1/node_modules" ]; then ok "$1 (이미 받아 두었다)"; return; fi
    ( cd "$1" && { [ -f package-lock.json ] && npm ci --silent || npm install --silent; } )
    ok "$1"
  }
  install_deps frontend
  install_deps vscode-extension

  # 첫 bootRun 은 Gradle 이 의존성을 다 내려받느라 몇 분 걸린다. 여기서 미리 끝내 둔다.
  bold "백엔드 미리 빌드"
  if [ "$JDK_OK" = "1" ]; then
    (cd backend && ./gradlew classes --console=plain -q) && ok "컴파일 완료"
  else
    warn "JDK 21 이 준비되면 'cd backend && ./gradlew classes' 로 미리 받아 둔다."
  fi
fi

# ── 5. 다음에 할 일 ─────────────────────────────────────────────────────────
cat <<'NEXT'

──────────────────────────────────────────────────────────────
 세팅 끝. 터미널 둘에서 이렇게 띄운다.

   ① ./scripts/backend.sh        (http://localhost:8080/api)
   ② cd frontend && npm run dev  (http://localhost:5173)

 로그인은 관리자 계정으로 시작한다 — 사원 명부(와플 API)를 붙이기 전까지는
 이 계정만 들어갈 수 있다.

   아이디 admin  /  비밀번호 admin1234      ← 들어가면 바로 바꾼다

 GitHub 수집·LLM 요약·사내망 공유는 backend/.env 를 채우면 켜진다.
 자세한 것은 docs/SETUP.md 를 본다.
──────────────────────────────────────────────────────────────
NEXT
