#!/usr/bin/env bash
# 내 컴퓨터에서 메인블로그 띄우기 (macOS·Linux). 준비물: JDK 21, Docker(가짜 메일함 전용).
# DB는 Crowfoot이 내어 준 MySQL(문서 654에 연결된 cf_u33_d1)을 바로 쓴다. 도커는 가짜 메일함(Mailpit)만 띄운다.
# 1) .env가 없으면 만든다 2) DB 접속 정보가 없으면 묻는다 3) 가짜 메일함을 띄운다 4) 앱을 띄운다 → http://localhost:8080
set -e
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  rand() { openssl rand -base64 48 | tr -d '\n'; }
  cat > .env <<ENV
DB_URL=
DB_USERNAME=
DB_PASSWORD=
JWT_SECRET=$(rand)
CODE_HASH_SECRET=$(rand)
BLACKLIST_HASH_SECRET=$(rand)
TURNSTILE_SITE_KEY=1x00000000000000000000AA
TURNSTILE_SECRET_KEY=1x0000000000000000000000000000000AA
FILE_STORAGE_ROOT=./uploads
APP_BASE_URL=http://localhost:8080
# http://localhost에서는 Safari가 Secure 쿠키를 버려 로그인이 안 되므로 끈다(운영은 켬)
AUTH_COOKIE_SECURE=false
# 메일은 가짜 메일함(http://localhost:8025)으로 간다. 실제 Gmail로 보내려면 README 참고
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_SMTP_AUTH=false
MAIL_STARTTLS=false
MAIL_USERNAME=
MAIL_PASSWORD=
ENV
  echo ".env를 만들었습니다."
fi

# 예전에 만든 .env에 없으면 더한다
grep -q '^AUTH_COOKIE_SECURE=' .env || echo 'AUTH_COOKIE_SECURE=false' >> .env
grep -q '^APP_BASE_URL=' .env || echo 'APP_BASE_URL=http://localhost:8080' >> .env
if ! grep -q '^MAIL_HOST=' .env && ! grep -q '^MAIL_USERNAME=.\+' .env; then
  printf 'MAIL_HOST=localhost\nMAIL_PORT=1025\nMAIL_SMTP_AUTH=false\nMAIL_STARTTLS=false\n' >> .env
fi

# DB 접속 정보: 비어 있거나 예전 도커 MySQL(localhost)이면 Crowfoot 접속 정보를 묻는다
set_env() {  # 키 값 → .env의 그 줄을 바꾸거나 더한다
  local key="$1" value="$2" tmp
  tmp="$(mktemp)"
  grep -v "^$key=" .env > "$tmp" || true
  printf '%s=%s\n' "$key" "$value" >> "$tmp"
  mv "$tmp" .env
}
DB_URL_NOW="$(grep '^DB_URL=' .env | cut -d= -f2- || true)"
if [ -z "$DB_URL_NOW" ] || echo "$DB_URL_NOW" | grep -q '//localhost:3306/mainblog'; then
  echo "Crowfoot → 메인블로그 문서 → 데이터베이스 탭에서 접속 정보를 확인해 입력하세요."
  read -r -p "  호스트(예: db.crowfoot...): " DB_HOST
  read -r -p "  포트 [3306]: " DB_PORT; DB_PORT="${DB_PORT:-3306}"
  read -r -p "  데이터베이스 이름 [cf_u33_d1]: " DB_NAME; DB_NAME="${DB_NAME:-cf_u33_d1}"
  read -r -p "  사용자 이름: " DB_USER
  read -r -s -p "  비밀번호: " DB_PASS; echo
  set_env DB_URL "jdbc:mysql://$DB_HOST:$DB_PORT/$DB_NAME?characterEncoding=UTF-8"
  set_env DB_USERNAME "$DB_USER"
  set_env DB_PASSWORD "$DB_PASS"
  echo "DB 접속 정보를 .env에 저장했습니다(이 파일은 저장소에 올라가지 않습니다)."
fi

if command -v docker >/dev/null 2>&1; then
  docker compose -f local/docker-compose.yml up -d --wait
  echo "가짜 메일함: http://localhost:8025 (회원가입 인증번호를 여기서 확인)"
else
  echo "도커가 없어 가짜 메일함을 띄우지 못했습니다. 회원가입 인증 메일만 안 가고 나머지는 됩니다."
fi
echo "앱을 띄웁니다. 다 뜨면 http://localhost:8080 으로 들어가세요."
echo "테스트 계정: owner@local.test / member@local.test / newbie@local.test / admin@local.test (비밀번호 Passw0rd!)"
./gradlew bootRun
