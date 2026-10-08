@echo off
rem 내 컴퓨터에서 메인블로그 띄우기 (Windows). 준비물: JDK 21, Docker Desktop.
rem 1) .env가 없으면 로컬용 값으로 만든다 2) MySQL을 띄운다 3) 앱을 띄운다 -> http://localhost:8080
chcp 65001 >nul
cd /d "%~dp0.."

if not exist .env (
  powershell -NoProfile -Command ^
    "$r = { [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]]) };" ^
    "@('DB_URL=jdbc:mysql://localhost:3306/mainblog','DB_USERNAME=root','DB_PASSWORD=root'," ^
    "'JWT_SECRET=' + (& $r),'CODE_HASH_SECRET=' + (& $r),'BLACKLIST_HASH_SECRET=' + (& $r)," ^
    "'TURNSTILE_SITE_KEY=1x00000000000000000000AA','TURNSTILE_SECRET_KEY=1x0000000000000000000000000000000AA'," ^
    "'FILE_STORAGE_ROOT=./uploads','AUTH_COOKIE_SECURE=false','MAIL_HOST=localhost','MAIL_PORT=1025','MAIL_SMTP_AUTH=false','MAIL_STARTTLS=false','MAIL_USERNAME=','MAIL_PASSWORD=') | Set-Content -Encoding ascii .env"
  echo .env를 만들었습니다.
)

findstr /b "AUTH_COOKIE_SECURE=" .env >nul || echo AUTH_COOKIE_SECURE=false>> .env
findstr /b "MAIL_HOST=" .env >nul || (
  echo MAIL_HOST=localhost>> .env
  echo MAIL_PORT=1025>> .env
  echo MAIL_SMTP_AUTH=false>> .env
  echo MAIL_STARTTLS=false>> .env
)

docker compose -f local\docker-compose.yml up -d --wait || exit /b 1
echo DB 준비 완료. 앱을 띄웁니다. 다 뜨면 http://localhost:8080 으로 들어가세요.
echo 회원가입 인증번호 메일은 http://localhost:8025 (테스트 메일함)에서 확인하세요.
echo 테스트 계정: owner@local.test / member@local.test / newbie@local.test / admin@local.test (비밀번호 Passw0rd!)
call gradlew.bat bootRun
