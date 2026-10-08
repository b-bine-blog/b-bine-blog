#!/usr/bin/env bash
# 내 컴퓨터에서 메인블로그 띄우기 (macOS·Linux). 준비물: JDK 21, Docker.
# 1) .env가 없으면 로컬용 값으로 만든다 2) MySQL을 띄운다 3) 앱을 띄운다 → http://localhost:8080
set -e
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  rand() { openssl rand -base64 48 | tr -d '\n'; }
  cat > .env <<ENV
DB_URL=jdbc:mysql://localhost:3306/mainblog
DB_USERNAME=root
DB_PASSWORD=root
JWT_SECRET=$(rand)
CODE_HASH_SECRET=$(rand)
BLACKLIST_HASH_SECRET=$(rand)
TURNSTILE_SITE_KEY=1x00000000000000000000AA
TURNSTILE_SECRET_KEY=1x0000000000000000000000000000000AA
FILE_STORAGE_ROOT=./uploads
# 회원가입 인증 메일을 받으려면 Gmail 주소와 앱 비밀번호를 넣는다
MAIL_USERNAME=
MAIL_PASSWORD=
ENV
  echo ".env를 만들었습니다."
fi

docker compose -f local/docker-compose.yml up -d --wait
echo "DB 준비 완료. 앱을 띄웁니다. 다 뜨면 http://localhost:8080 으로 들어가세요."
echo "테스트 계정: owner@local.test / member@local.test / newbie@local.test / admin@local.test (비밀번호 Passw0rd!)"
./gradlew bootRun
