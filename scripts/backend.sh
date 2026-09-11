#!/usr/bin/env bash
#
# 백엔드를 띄운다. `backend/.env` 를 셸에 올린 뒤 bootRun 을 부른다.
#
# 이 한 겹을 두는 이유: 손으로 칠 때 `set -a && source .env && set +a` 를 빠뜨리기 쉬운데,
# 그러면 JWT_SECRET 이 비어 기동 중에 멈춘다. 무엇이 잘못됐는지 알기 어려운 자리다.
set -euo pipefail
cd "$(dirname "$0")/../backend"

[ -f .env ] || { echo "backend/.env 가 없다. ./scripts/setup.sh 를 먼저 돌린다."; exit 1; }

set -a
# shellcheck disable=SC1091
. ./.env
set +a

if [ -z "${JAVA_HOME:-}" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
  [ -n "$JAVA_HOME" ] && export JAVA_HOME
fi

exec ./gradlew bootRun "$@"
