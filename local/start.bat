@echo off
rem 내 컴퓨터에서 메인블로그 띄우기 (Windows). 준비물: JDK 21, Docker Desktop(가짜 메일함 전용).
rem DB는 Crowfoot이 내어 준 MySQL(cf_u33_d1)을 바로 쓴다. 도커는 가짜 메일함(Mailpit)만 띄운다.
rem 1) .env가 없으면 만든다 2) DB 접속 정보가 없으면 묻는다 3) 가짜 메일함을 띄운다 4) 앱을 띄운다 -> http://localhost:8080
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0.."

if not exist .env (
  powershell -NoProfile -Command ^
    "$r = { [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]]) };" ^
    "@('DB_URL=','DB_USERNAME=','DB_PASSWORD='," ^
    "'JWT_SECRET=' + (& $r),'CODE_HASH_SECRET=' + (& $r),'BLACKLIST_HASH_SECRET=' + (& $r)," ^
    "'TURNSTILE_SITE_KEY=1x00000000000000000000AA','TURNSTILE_SECRET_KEY=1x0000000000000000000000000000000AA'," ^
    "'FILE_STORAGE_ROOT=./uploads','APP_BASE_URL=http://localhost:8080','AUTH_COOKIE_SECURE=false','MAIL_HOST=localhost','MAIL_PORT=1025','MAIL_SMTP_AUTH=false','MAIL_STARTTLS=false','MAIL_USERNAME=','MAIL_PASSWORD=') | Set-Content -Encoding ascii .env"
  echo .env를 만들었습니다.
)

findstr /b "AUTH_COOKIE_SECURE=" .env >nul || echo AUTH_COOKIE_SECURE=false>> .env
findstr /b "APP_BASE_URL=" .env >nul || echo APP_BASE_URL=http://localhost:8080>> .env
findstr /b "MAIL_HOST=" .env >nul || (
  echo MAIL_HOST=localhost>> .env
  echo MAIL_PORT=1025>> .env
  echo MAIL_SMTP_AUTH=false>> .env
  echo MAIL_STARTTLS=false>> .env
)

rem DB 접속 정보가 비어 있거나 예전 도커 MySQL이면 Crowfoot 접속 정보를 묻는다
set "NEED_DB="
findstr /r /b "DB_URL=jdbc:" .env >nul || set NEED_DB=1
findstr /b "DB_URL=jdbc:mysql://localhost:3306/mainblog" .env >nul && set NEED_DB=1
if defined NEED_DB (
  echo Crowfoot - 메인블로그 문서 - 데이터베이스 탭에서 접속 정보를 확인해 입력하세요.
  set /p DB_HOST=  호스트: 
  set "DB_PORT=3306"
  set /p DB_PORT=  포트 [3306]: 
  set "DB_NAME=cf_u33_d1"
  set /p DB_NAME=  데이터베이스 이름 [cf_u33_d1]: 
  set /p DB_USER=  사용자 이름: 
  set /p DB_PASS=  비밀번호: 
  powershell -NoProfile -Command ^
    "$l = Get-Content .env | Where-Object { $_ -notmatch '^(DB_URL|DB_USERNAME|DB_PASSWORD)=' };" ^
    "$l += 'DB_URL=jdbc:mysql://' + $env:DB_HOST + ':' + $env:DB_PORT + '/' + $env:DB_NAME + '?characterEncoding=UTF-8';" ^
    "$l += 'DB_USERNAME=' + $env:DB_USER; $l += 'DB_PASSWORD=' + $env:DB_PASS; $l | Set-Content -Encoding ascii .env"
  echo DB 접속 정보를 .env에 저장했습니다.
)

where docker >nul 2>nul && (
  docker compose -f local\docker-compose.yml up -d --wait
  echo 가짜 메일함: http://localhost:8025 ^(회원가입 인증번호를 여기서 확인^)
) || echo 도커가 없어 가짜 메일함을 띄우지 못했습니다. 회원가입 인증 메일만 안 가고 나머지는 됩니다.
echo 앱을 띄웁니다. 다 뜨면 http://localhost:8080 으로 들어가세요.
echo 테스트 계정: owner@local.test / member@local.test / newbie@local.test / admin@local.test (비밀번호 Passw0rd!)
call gradlew.bat bootRun
