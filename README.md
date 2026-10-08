# blogproject

티스토리형 블로그 플랫폼(메인블로그) 프로젝트입니다.

## 내 컴퓨터에서 바로 써 보기

준비물: JDK 21, Docker(Docker Desktop). 저장소 루트에서 아래 하나만 실행합니다.

```bash
./local/start.sh        # macOS·Linux
local\start.bat         # Windows
```

`.env`를 로컬용 값으로 만들고, MySQL을 띄워 스키마와 테스트 데이터를 넣은 뒤 앱을 띄웁니다. 다 뜨면 http://localhost:8080 으로 들어갑니다.

| 계정 | 비밀번호 | 들어 있는 것 |
|---|---|---|
| owner@local.test | Passw0rd! | 블로그 `travel-log`의 블로그장. 참여 신청 1건이 기다리는 중 |
| member@local.test | Passw0rd! | `travel-log` 멤버, 블로그 `daily-note`의 블로그장 |
| newbie@local.test | Passw0rd! | `travel-log`에 참여 신청한 회원 |
| admin@local.test | Passw0rd! | 관리자 |

블로그는 `http://localhost:8080/blog/travel-log`, 운영 화면은 뒤에 `/manage`를 붙입니다.
회원가입 인증번호 메일은 실제로 나가지 않고 테스트 메일함 http://localhost:8025 (Mailpit)에 쌓입니다. 아무 이메일 주소로 가입해도 그 화면에서 인증번호를 볼 수 있습니다.
실제 Gmail로 보내려면 `.env`의 `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_SMTP_AUTH=true`, `MAIL_STARTTLS=true`로 바꾸고 `MAIL_USERNAME`, `MAIL_PASSWORD`(보내는 Gmail 계정의 앱 비밀번호)를 채웁니다.
멈출 때는 Ctrl+C, DB까지 지우고 처음부터 다시 넣으려면 `docker compose -f local/docker-compose.yml down -v`.

## 실행

준비물: JDK 21, Docker(로컬 MySQL과 테스트용).

```bash
# 1. 로컬 MySQL 8.0에 스키마 넣기 (원천은 Crowfoot 문서, docs/04-design/schema.sql)
docker run -d --name mainblog-db -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=mainblog \
  -v "$PWD/docs/04-design/schema.sql:/docker-entrypoint-initdb.d/1-schema.sql:ro" \
  -v "$PWD/docs/04-design/shedlock.sql:/docker-entrypoint-initdb.d/2-shedlock.sql:ro" \
  mysql:8.0

# 2. 환경변수: .env.example을 .env로 복사해 채운다. 실행할 때 자동으로 읽는다.
cp .env.example .env
#   DB_URL=jdbc:mysql://localhost:3306/mainblog
#   DB_USERNAME=root / DB_PASSWORD=root
#   JWT_SECRET, CODE_HASH_SECRET, BLACKLIST_HASH_SECRET: 32바이트 이상 무작위 값 (예: openssl rand -base64 48)
#   MAIL_*: Gmail 앱 비밀번호. 비워 두면 메일만 실패하고 화면 응답은 같다
#   TURNSTILE_*: 로컬은 Cloudflare 테스트 키 1x00000000000000000000AA / 1x0000000000000000000000000000000AA

# 3. 실행 → http://localhost:8080
./gradlew bootRun
```

로컬 실행은 http이므로 쿠키의 Secure 속성 때문에 `localhost`로 접속합니다(브라우저가 localhost는 예외로 둡니다).
숫자 규칙(만료 시간, 제한 횟수 등)은 모두 `src/main/resources/application.yml`의 설정값이고 환경변수로 바꿀 수 있습니다(D-68).

## 테스트

```bash
./gradlew test
```

Testcontainers가 MySQL 8.0 컨테이너를 띄우고(`docs/04-design/schema.sql`), 메일은 GreenMail(SMTP 3025)로 받습니다. Docker가 떠 있어야 합니다.

## 문서

- [CLAUDE.md](CLAUDE.md): AI 코딩 에이전트 작업 규칙
- [.specify/memory/constitution.md](.specify/memory/constitution.md): 개발 원칙 (Spec Kit)
- [specs/](specs/README.md): 기능별 명세. 001 회원 기능은 [specs/001-member-auth/](specs/001-member-auth/spec.md)
  (API 계약 `contracts/`, 확인 방법 `quickstart.md`)
- [docs/](docs/README.md): 원본 요구사항 문서와 결정 기록
