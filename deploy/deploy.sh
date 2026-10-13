#!/usr/bin/env bash
# 서버에서 도커 컨테이너로 앱을 실행한다 (D-127). 무중단 블루-그린(D-126).
# GitHub Actions가 app.jar.new, Dockerfile, 이 스크립트를 ~/blogproject에 올린 뒤 SSH로 접속해 실행한다.
# 서버에서 jar와 Dockerfile로 이미지를 만든다(Java 21 실행 환경이 이미지에 들어 있어 서버 Java와 상관없다).
# 두 포트(APP_PORT, APP_PORT_B)를 번갈아 쓴다. 새 앱을 쉬는 포트에 먼저 켜고 응답을 확인한 뒤 옛 앱을 끈다.
# nginx는 두 포트를 함께 upstream으로 두고, 꺼진 쪽은 건너뛴다(deploy/nginx 설정). 그래서 사용자는 끊기지 않는다.
# 비밀 값은 Actions가 환경변수로 넘긴다. 이 파일에는 적지 않는다.
# 같이 쓰는 서버라 docker inspect로 다른 사람에게 보이지 않게, 비밀 값은 -e로 넘기지 않고
# 나만 읽을 수 있는 파일(secrets.env)을 컨테이너의 /app/.env로 붙인다.
set -euo pipefail

APP_DIR="$HOME/blogproject"
JAR_NEW="$APP_DIR/app.jar.new"
ACTIVE_FILE="$APP_DIR/active-port"   # 지금 사용자를 받는 포트. release-note도 이 파일로 앱을 찾는다
# nginx가 두 포트를 모두 보도록 바꾸고 reload한 뒤에 이 파일을 만든다(touch). 없으면 예전처럼 APP_PORT 하나만 쓴다.
# nginx가 8371을 모르는 채로 그쪽에 앱을 띄우면 사이트가 내려가기 때문이다.
BLUE_GREEN_FLAG="$APP_DIR/blue-green-on"
APP_PORT_B="${APP_PORT_B:-8371}"
IMAGE="mainttblog-app"                   # 같은 서버의 다른 팀과 겹치지 않는 이름
SECRETS_FILE="$APP_DIR/secrets.env"

for name in APP_PORT DB_ADDRESS DB_PORT DB_NAME DB_USERNAME DB_PASSWORD \
            JWT_SECRET CODE_HASH_SECRET BLACKLIST_HASH_SECRET; do
  if [ -z "${!name:-}" ]; then
    echo "환경변수 $name 가 비어 있습니다. GitHub Repository secrets를 확인하세요." >&2
    exit 1
  fi
done

DB_URL="jdbc:mysql://${DB_ADDRESS}:${DB_PORT}/${DB_NAME}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
APP_BASE_URL="${APP_BASE_URL:-http://localhost:${APP_PORT}}"   # 외부 주소. 포트가 바뀌어도 같다
FILE_STORAGE_ROOT="$APP_DIR/uploads"          # 재배포해도 남는다(D-75). 컨테이너의 /app/uploads에 붙인다

mkdir -p "$FILE_STORAGE_ROOT"

# 테스트 블로그 표지(2026-10-13 사용자 요청): 저장소의 deploy/seed-covers를 업로드 폴더에 복사한다.
# 이미 있는 파일은 덮어쓰지 않는다. DB의 images 행은 따로 넣는다.
if [ -d "$APP_DIR/deploy/seed-covers" ]; then
  cp -n "$APP_DIR/deploy/seed-covers/"* "$FILE_STORAGE_ROOT/" 2>/dev/null || true
fi

command -v docker > /dev/null || { echo "서버에서 docker를 찾지 못했습니다." >&2; exit 1; }

# 비밀 값 파일: 나만 읽고 쓸 수 있게 만든다. 값은 .properties 형식(역슬래시만 이스케이프)
umask 077
{
  printf 'DB_URL=%s\n' "$DB_URL"
  for name in DB_USERNAME DB_PASSWORD JWT_SECRET CODE_HASH_SECRET BLACKLIST_HASH_SECRET \
              MAIL_USERNAME MAIL_PASSWORD TURNSTILE_SITE_KEY TURNSTILE_SECRET_KEY APP_BASE_URL \
              OAUTH_GOOGLE_CLIENT_ID OAUTH_GOOGLE_CLIENT_SECRET OAUTH_KAKAO_CLIENT_ID OAUTH_KAKAO_CLIENT_SECRET \
              OAUTH_NAVER_CLIENT_ID OAUTH_NAVER_CLIENT_SECRET; do
    value="${!name:-}"
    printf '%s=%s\n' "$name" "${value//\\/\\\\}"
  done
} > "$SECRETS_FILE"
chmod 600 "$SECRETS_FILE"
umask 022

# 이미지 만들기: 받은 jar와 Dockerfile만 있는 폴더에서 만든다
BUILD_DIR="$APP_DIR/image-build"
rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
mv -f "$JAR_NEW" "$BUILD_DIR/app.jar"
cp "$APP_DIR/Dockerfile" "$BUILD_DIR/Dockerfile"
TAG="$(date +%Y%m%d%H%M%S)"
echo "이미지 만드는 중: $IMAGE:$TAG"
docker build -q -t "$IMAGE:$TAG" "$BUILD_DIR"
rm -rf "$BUILD_DIR"


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

container_of() { echo "mainttblog-$1"; }

# 이 스크립트가 만든 컨테이너만 멈춘다. 처리 중인 요청은 끝내고 내려간다(graceful, 최대 40초)
stop_container() {  # $1 포트
  local name
  name="$(container_of "$1")"
  if docker inspect "$name" > /dev/null 2>&1; then
    echo "컨테이너 $name 멈추는 중..."
    docker stop -t 40 "$name" > /dev/null || true
    docker rm -f "$name" > /dev/null 2>&1 || true
  fi
}

# 예전 jar 방식(D-106)으로 돌던 앱. 그 스크립트가 남긴 PID만 멈춘다(같이 쓰는 서버)
stop_legacy_jar() {
  local pid_file jar pid
  for pid_file in "$APP_DIR"/app.pid "$APP_DIR"/app-*.pid; do
    [ -f "$pid_file" ] || continue
    pid="$(cat "$pid_file")"
    jar="${pid_file%.pid}.jar"
    if kill -0 "$pid" 2>/dev/null && grep -q "$jar" "/proc/$pid/cmdline" 2>/dev/null; then
      echo "예전 jar 앱(PID $pid) 멈추는 중..."
      kill "$pid"
      for i in $(seq 1 40); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
      kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$pid_file" "$jar"
  done
}

is_up() {  # $1 포트. 설정 API가 200이면 요청을 받을 수 있다
  if command -v curl > /dev/null; then
    curl -fs -o /dev/null "http://127.0.0.1:$1/api/public-config"
  else
    (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
  fi
}

# 쉬는 포트에 남아 있는 컨테이너(지난 배포가 실패했을 때 등)는 먼저 정리한다
stop_container "$NEW_PORT"
if [ "$NEW_PORT" = "$ACTIVE_PORT" ]; then
  echo "무중단 준비 전($BLUE_GREEN_FLAG 없음): 옛 앱을 끄고 같은 포트에서 다시 켭니다."
  stop_legacy_jar
fi
if is_up "$NEW_PORT"; then
  echo "포트 $NEW_PORT를 다른 프로그램이 쓰고 있습니다. 배포를 멈춥니다." >&2
  exit 1
fi

NAME="$(container_of "$NEW_PORT")"
echo "새 컨테이너 실행 중: $NAME (포트 $NEW_PORT, 지금 사용자는 포트 $ACTIVE_PORT)..."
# --network host: 앱이 서버의 포트를 바로 쓴다(nginx 설정, DB 주소가 jar 때와 같다)
# --user: 업로드 파일이 내 계정 소유로 남게 한다
# --restart unless-stopped: 서버가 다시 켜지면 컨테이너도 다시 뜬다
docker run -d \
  --name "$NAME" \
  --restart unless-stopped \
  --network host \
  --user "$(id -u):$(id -g)" \
  --log-opt max-size=20m --log-opt max-file=3 \
  -e SERVER_PORT="$NEW_PORT" \
  -e AUTH_COOKIE_SECURE="${AUTH_COOKIE_SECURE:-true}" \
  -e SERVER_FORWARDHEADERSSTRATEGY=native \
  -v "$FILE_STORAGE_ROOT:/app/uploads" \
  -v "$SECRETS_FILE:/app/.env:ro" \
  "$IMAGE:$TAG" > /dev/null

echo "새 앱이 뜨기를 기다리는 중..."
ok=0
for i in $(seq 1 60); do
  if is_up "$NEW_PORT"; then ok=1; break; fi
  [ "$(docker inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null)" = "true" ] || break
  sleep 3
done
if [ "$ok" != 1 ]; then
  echo "새 앱이 응답하지 않습니다. 옛 앱(포트 $ACTIVE_PORT)이 돌고 있었다면 그대로 둡니다. 마지막 로그:" >&2
  docker logs --tail 80 "$NAME" >&2 || true
  stop_container "$NEW_PORT"
  docker image rm "$IMAGE:$TAG" > /dev/null 2>&1 || true
  exit 1
fi

# 새 앱으로 넘기고 옛 앱을 내린다. nginx는 꺼진 포트를 건너뛰므로 따로 바꾸지 않는다
echo "$NEW_PORT" > "$ACTIVE_FILE"
if [ "$NEW_PORT" != "$ACTIVE_PORT" ]; then
  stop_container "$ACTIVE_PORT"
  stop_legacy_jar
fi

# 우리 이미지 중 지금 쓰는 것만 남기고 지운다(다른 팀 이미지는 건드리지 않는다)
docker images --format '{{.Repository}}:{{.Tag}}' "$IMAGE" | grep -vx "$IMAGE:$TAG" | xargs -r docker image rm > /dev/null 2>&1 || true
echo "배포 완료: $NAME (포트 $NEW_PORT, 로그는 docker logs $NAME)"
