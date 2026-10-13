#!/usr/bin/env bash
# 서버에서 jar를 실행한다 (D-106, 도커 없이).
# GitHub Actions가 app.jar와 이 스크립트를 ~/blogproject에 올린 뒤 SSH로 접속해 실행한다.
# 비밀 값은 Actions가 환경변수로 넘긴다. 이 파일에는 적지 않는다.
set -euo pipefail

APP_DIR="$HOME/blogproject"
JAR_NEW="$APP_DIR/app.jar.new"
JAR="$APP_DIR/app.jar"
PID_FILE="$APP_DIR/app.pid"
LOG_FILE="$APP_DIR/app.log"

for name in APP_PORT DB_ADDRESS DB_PORT DB_NAME DB_USERNAME DB_PASSWORD \
            JWT_SECRET CODE_HASH_SECRET BLACKLIST_HASH_SECRET; do
  if [ -z "${!name:-}" ]; then
    echo "환경변수 $name 가 비어 있습니다. GitHub Repository secrets를 확인하세요." >&2
    exit 1
  fi
done

export DB_URL="jdbc:mysql://${DB_ADDRESS}:${DB_PORT}/${DB_NAME}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
export APP_BASE_URL="${APP_BASE_URL:-http://localhost:${APP_PORT}}"
export AUTH_COOKIE_SECURE="${AUTH_COOKIE_SECURE:-true}"
export FILE_STORAGE_ROOT="$APP_DIR/uploads"          # 재배포해도 남는다(D-75)
export SERVER_PORT="$APP_PORT"
# nginx 뒤에서 실제 접속 IP를 쓰게 한다(로그인 횟수 제한 등, ClientIpResolver)
export SERVER_FORWARDHEADERSSTRATEGY=native
export TZ=Asia/Seoul

mkdir -p "$FILE_STORAGE_ROOT"

# 테스트 블로그 표지(2026-10-13 사용자 요청): 저장소의 deploy/seed-covers를 업로드 폴더에 복사한다.
# 이미 있는 파일은 덮어쓰지 않는다. DB의 images 행은 따로 넣는다.
if [ -d "$APP_DIR/deploy/seed-covers" ]; then
  cp -n "$APP_DIR/deploy/seed-covers/"* "$FILE_STORAGE_ROOT/" 2>/dev/null || true
fi

# java 찾기: SSH로 명령만 실행하면 로그인 때 읽는 PATH 설정이 빠져 java를 못 찾을 수 있다
find_java() {
  local c
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then echo "$JAVA_HOME/bin/java"; return; fi
  c="$(command -v java 2>/dev/null || true)"
  [ -z "$c" ] && c="$(bash -lc 'command -v java' 2>/dev/null | tail -n 1 || true)"
  [ -z "$c" ] && c="$(bash -ic 'command -v java' 2>/dev/null | tail -n 1 || true)"
  if [ -n "$c" ] && [ -x "$c" ]; then echo "$c"; return; fi
  for c in "$HOME"/.sdkman/candidates/java/current/bin/java "$HOME"/jdk*/bin/java "$HOME"/*/jdk*/bin/java \
           /opt/java/*/bin/java /opt/jdk*/bin/java /usr/lib/jvm/*21*/bin/java /usr/lib/jvm/*/bin/java; do
    if [ -x "$c" ]; then echo "$c"; return; fi
  done
}
JAVA_BIN="$(find_java)"
if [ -z "$JAVA_BIN" ]; then
  echo "서버에서 java를 찾지 못했습니다. 서버 터미널에서 'which java' 결과를 확인하세요." >&2
  exit 1
fi
echo "java: $JAVA_BIN"

# 이전 앱 멈추기: 이 스크립트가 남긴 PID만 멈춘다(같이 쓰는 서버라 다른 프로세스는 건드리지 않는다)
if [ -f "$PID_FILE" ]; then
  OLD_PID="$(cat "$PID_FILE")"
  if kill -0 "$OLD_PID" 2>/dev/null && grep -q "$JAR" "/proc/$OLD_PID/cmdline" 2>/dev/null; then
    echo "이전 앱(PID $OLD_PID) 멈추는 중..."
    kill "$OLD_PID"
    for i in $(seq 1 30); do
      kill -0 "$OLD_PID" 2>/dev/null || break
      sleep 1
    done
    kill -9 "$OLD_PID" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
fi

if [ -f "$JAR_NEW" ]; then
  mv -f "$JAR_NEW" "$JAR"
fi

echo "새 앱 실행 중 (포트 $APP_PORT)..."
# setsid + nohup: SSH 접속이 끝나도 앱이 계속 돈다
setsid nohup "$JAVA_BIN" -Xmx512m -jar "$JAR" > "$LOG_FILE" 2>&1 < /dev/null &
echo $! > "$PID_FILE"
NEW_PID="$(cat "$PID_FILE")"

is_up() {  # curl이 없으면 포트가 열렸는지만 본다
  if command -v curl > /dev/null; then
    curl -fs -o /dev/null "http://localhost:${APP_PORT}/"
  else
    (exec 3<>"/dev/tcp/127.0.0.1/${APP_PORT}") 2>/dev/null
  fi
}

echo "앱이 뜨기를 기다리는 중..."
for i in $(seq 1 60); do
  if is_up; then
    echo "배포 완료: http://localhost:${APP_PORT}/ (PID $NEW_PID, 로그 $LOG_FILE)"
    exit 0
  fi
  kill -0 "$NEW_PID" 2>/dev/null || break
  sleep 3
done

echo "앱이 응답하지 않습니다. 마지막 로그:" >&2
tail -n 80 "$LOG_FILE" >&2 || true
exit 1
