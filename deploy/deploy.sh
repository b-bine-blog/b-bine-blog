#!/usr/bin/env bash
# 서버에서 jar를 실행한다 (D-106, 도커 없이). 무중단 블루-그린(D-126).
# GitHub Actions가 app.jar.new와 이 스크립트를 ~/blogproject에 올린 뒤 SSH로 접속해 실행한다.
# 두 포트(APP_PORT, APP_PORT_B)를 번갈아 쓴다. 새 앱을 쉬는 포트에 먼저 켜고 응답을 확인한 뒤 옛 앱을 끈다.
# nginx는 두 포트를 함께 upstream으로 두고, 꺼진 쪽은 건너뛴다(deploy/nginx 설정). 그래서 사용자는 끊기지 않는다.
# 비밀 값은 Actions가 환경변수로 넘긴다. 이 파일에는 적지 않는다.
set -euo pipefail

APP_DIR="$HOME/blogproject"
JAR_NEW="$APP_DIR/app.jar.new"
ACTIVE_FILE="$APP_DIR/active-port"   # 지금 사용자를 받는 포트. release-note도 이 파일로 앱을 찾는다
# nginx가 두 포트를 모두 보도록 바꾸고 reload한 뒤에 이 파일을 만든다(touch). 없으면 예전처럼 APP_PORT 하나만 쓴다.
# nginx가 8371을 모르는 채로 그쪽에 앱을 띄우면 사이트가 내려가기 때문이다.
BLUE_GREEN_FLAG="$APP_DIR/blue-green-on"
APP_PORT_B="${APP_PORT_B:-8371}"

for name in APP_PORT DB_ADDRESS DB_PORT DB_NAME DB_USERNAME DB_PASSWORD \
            JWT_SECRET CODE_HASH_SECRET BLACKLIST_HASH_SECRET; do
  if [ -z "${!name:-}" ]; then
    echo "환경변수 $name 가 비어 있습니다. GitHub Repository secrets를 확인하세요." >&2
    exit 1
  fi
done

export DB_URL="jdbc:mysql://${DB_ADDRESS}:${DB_PORT}/${DB_NAME}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
export APP_BASE_URL="${APP_BASE_URL:-http://localhost:${APP_PORT}}"   # 외부 주소. 포트가 바뀌어도 같다
export AUTH_COOKIE_SECURE="${AUTH_COOKIE_SECURE:-true}"
export FILE_STORAGE_ROOT="$APP_DIR/uploads"          # 재배포해도 남는다(D-75)
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

# 지금 도는 포트와 새로 켤 포트 정하기
ACTIVE_PORT="$(cat "$ACTIVE_FILE" 2>/dev/null || true)"
if [ "$ACTIVE_PORT" != "$APP_PORT" ] && [ "$ACTIVE_PORT" != "$APP_PORT_B" ]; then
  ACTIVE_PORT="$APP_PORT"   # 처음 바꿀 때: 예전 방식 앱은 APP_PORT에서 돈다
fi
if [ ! -f "$BLUE_GREEN_FLAG" ]; then
  NEW_PORT="$APP_PORT"     # 무중단 준비 전: 같은 포트에서 껐다 켠다(그동안 점검 중 화면)
elif [ "$ACTIVE_PORT" = "$APP_PORT" ]; then
  NEW_PORT="$APP_PORT_B"
else
  NEW_PORT="$APP_PORT"
fi

jar_of() { echo "$APP_DIR/app-$1.jar"; }
pid_of() { echo "$APP_DIR/app-$1.pid"; }
log_of() { echo "$APP_DIR/app-$1.log"; }

# 이 스크립트가 남긴 PID만 멈춘다(같이 쓰는 서버라 다른 프로세스는 건드리지 않는다)
stop_app() {  # $1 PID 파일, $2 jar 경로
  local pid_file="$1" jar="$2" pid
  [ -f "$pid_file" ] || return 0
  pid="$(cat "$pid_file")"
  if kill -0 "$pid" 2>/dev/null && grep -q "$jar" "/proc/$pid/cmdline" 2>/dev/null; then
    echo "앱(PID $pid) 멈추는 중... 처리 중인 요청은 끝내고 내려간다(graceful)"
    kill "$pid"
    for i in $(seq 1 40); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 1
    done
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$pid_file"
}

is_up() {  # $1 포트. 설정 API가 200이면 요청을 받을 수 있다
  if command -v curl > /dev/null; then
    curl -fs -o /dev/null "http://127.0.0.1:$1/api/public-config"
  else
    (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
  fi
}

# 쉬는 포트에 남아 있는 앱(지난 배포가 실패했을 때 등)은 먼저 정리한다
stop_app "$(pid_of "$NEW_PORT")" "$(jar_of "$NEW_PORT")"
if [ "$NEW_PORT" = "$ACTIVE_PORT" ]; then
  echo "무중단 준비 전($BLUE_GREEN_FLAG 없음): 옛 앱을 끄고 같은 포트에서 다시 켭니다."
  stop_app "$APP_DIR/app.pid" "$APP_DIR/app.jar"
fi
if is_up "$NEW_PORT"; then
  echo "포트 $NEW_PORT를 다른 프로그램이 쓰고 있습니다. 배포를 멈춥니다." >&2
  exit 1
fi

NEW_JAR="$(jar_of "$NEW_PORT")"
mv -f "$JAR_NEW" "$NEW_JAR"

echo "새 앱 실행 중 (포트 $NEW_PORT, 지금 사용자는 포트 $ACTIVE_PORT)..."
# setsid + nohup: SSH 접속이 끝나도 앱이 계속 돈다
SERVER_PORT="$NEW_PORT" setsid nohup "$JAVA_BIN" -Xmx512m -jar "$NEW_JAR" > "$(log_of "$NEW_PORT")" 2>&1 < /dev/null &
NEW_PID=$!
echo "$NEW_PID" > "$(pid_of "$NEW_PORT")"

echo "새 앱이 뜨기를 기다리는 중..."
ok=0
for i in $(seq 1 60); do
  if is_up "$NEW_PORT"; then ok=1; break; fi
  kill -0 "$NEW_PID" 2>/dev/null || break
  sleep 3
done
if [ "$ok" != 1 ]; then
  echo "새 앱이 응답하지 않습니다. 옛 앱(포트 $ACTIVE_PORT)이 돌고 있었다면 그대로 둡니다. 마지막 로그:" >&2
  tail -n 80 "$(log_of "$NEW_PORT")" >&2 || true
  stop_app "$(pid_of "$NEW_PORT")" "$NEW_JAR"
  exit 1
fi

# 새 앱으로 넘기고 옛 앱을 내린다. nginx는 꺼진 포트를 건너뛰므로 따로 바꾸지 않는다
echo "$NEW_PORT" > "$ACTIVE_FILE"
if [ "$NEW_PORT" != "$ACTIVE_PORT" ]; then
  stop_app "$(pid_of "$ACTIVE_PORT")" "$(jar_of "$ACTIVE_PORT")"
fi
stop_app "$APP_DIR/app.pid" "$APP_DIR/app.jar"   # 예전 방식(app.jar, app.pid)으로 돌던 앱
echo "배포 완료: 포트 $NEW_PORT (PID $NEW_PID, 로그 $(log_of "$NEW_PORT"))"
