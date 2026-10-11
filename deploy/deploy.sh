#!/usr/bin/env bash
# 서버에서 도커 이미지를 불러와 실행한다 (D-105).
# GitHub Actions가 이미지 파일(blogproject-image.tar.gz)과 이 스크립트를 ~/blogproject에 올린 뒤
# SSH로 접속해 실행한다. 비밀 값은 Actions가 환경변수로 넘긴다. 이 파일에는 적지 않는다.
set -euo pipefail

# 서버의 도커를 반 전체가 같이 쓰므로 이름에 계정을 붙여 다른 사람 컨테이너와 겹치지 않게 한다
APP_NAME="${APP_NAME:-aigj_01_008-mainblog}"
APP_DIR="$HOME/blogproject"
IMAGE_FILE="$APP_DIR/blogproject-image.tar.gz"
IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG가 비어 있습니다}"

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
# nginx 뒤에서 실제 접속 IP를 쓰게 한다(로그인 횟수 제한 등, ClientIpResolver)
export SERVER_FORWARDHEADERSSTRATEGY=native

mkdir -p "$APP_DIR/uploads"

echo "이미지 불러오는 중..."
docker load -i "$IMAGE_FILE"

echo "이전 컨테이너 정리 중..."
docker rm -f "$APP_NAME" > /dev/null 2>&1 || true

echo "새 컨테이너 실행 중 (포트 $APP_PORT)..."
# -e 이름 만 적으면 값은 지금 셸의 환경변수에서 가져간다. 명령줄에 비밀 값이 남지 않는다.
docker run -d \
  --name "$APP_NAME" \
  --restart unless-stopped \
  -p "${APP_PORT}:8080" \
  -v "$APP_DIR/uploads:/app/uploads" \
  -e DB_URL -e DB_USERNAME -e DB_PASSWORD \
  -e JWT_SECRET -e CODE_HASH_SECRET -e BLACKLIST_HASH_SECRET \
  -e MAIL_USERNAME -e MAIL_PASSWORD \
  -e TURNSTILE_SITE_KEY -e TURNSTILE_SECRET_KEY \
  -e APP_BASE_URL -e AUTH_COOKIE_SECURE -e SERVER_FORWARDHEADERSSTRATEGY \
  "$APP_NAME:$IMAGE_TAG"

rm -f "$IMAGE_FILE"
# 내 예전 이미지만 지운다(디스크 절약). 같이 쓰는 서버라 docker image prune은 쓰지 않는다.
docker images --format '{{.Repository}}:{{.Tag}}' "$APP_NAME" \
  | grep -v ":${IMAGE_TAG}\$" | xargs -r docker rmi > /dev/null 2>&1 || true

echo "앱이 뜨기를 기다리는 중..."
for i in $(seq 1 30); do
  if command -v curl > /dev/null && curl -fs -o /dev/null "http://localhost:${APP_PORT}/"; then
    echo "배포 완료: http://localhost:${APP_PORT}/"
    exit 0
  fi
  if [ "$(docker inspect -f '{{.State.Running}}' "$APP_NAME" 2>/dev/null)" != "true" ]; then
    break
  fi
  sleep 3
done

echo "앱이 응답하지 않습니다. 마지막 로그:" >&2
docker logs --tail 80 "$APP_NAME" >&2 || true
exit 1
