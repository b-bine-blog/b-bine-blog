# b-bine blog

여러 사람이 함께 쓰는 블로그 플랫폼입니다. 👉 **https://mainttblog.java21.net**

## 업데이트

새 버전이 나가면 맨 위에 더합니다. 사이트의 공지사항에도 같은 내용이 "업데이트"로 올라갑니다.

| 버전 | 날짜 | 바뀐 점 |
|---|---|---|
| [v1.06](release-notes/2026-10-13-blog-cards-fix/notice.md) | 2026-10-13 | 메인 화면 블로그 카드가 안 보이던 문제 수정(목록을 10개 받아 앞 5개만 표시) |
| [v1.05](release-notes/2026-10-13-blog-cards-rules/notice.md) | 2026-10-13 | 메인 화면 블로그 둘러보기를 글 위 표지 카드 5개로, 화면 아래 "운영 규칙"(삭제 시점·제한 안내) 추가 |
| [v1.04](release-notes/2026-10-13-inquiry-dark/notice.md) | 2026-10-13 | "시스템 문의"로 관리자에게 문의(비회원도 가능), 다크 모드 |
| [v1.03](release-notes/2026-10-12-og-preview/notice.md) | 2026-10-12 | 링크 미리보기 그림을 글 화면 모양으로, 업데이트 공지에 "업데이트" 표시 |
| [v1.02](release-notes/2026-10-12-feed-notice/notice.md) | 2026-10-12 | 메인 글 목록 위에 최근 공지 고정, 메뉴 정리 |
| [v1.01](release-notes/2026-10-12-github-link/notice.md) | 2026-10-12 | 화면 아래에 GitHub 링크 |
| [v1.00](release-notes/2026-10-12-b-bine-blog/notice.md) | 2026-10-12 | 새 이름 b-bine blog와 초록 디자인 |

## 프로젝트 소개

티스토리형 블로그 플랫폼(메인블로그) 프로젝트입니다.

## 내 컴퓨터에서 바로 써 보기

준비물: JDK 21, Docker(가짜 메일함에만 씀). DB는 Crowfoot이 내어 준 MySQL(문서 654에 연결된 `cf_u33_d1`)을 바로 씁니다. 저장소 루트에서 아래 하나만 실행합니다.

```bash
./local/start.sh        # macOS·Linux
local\start.bat         # Windows
```

처음 실행하면 Crowfoot DB 접속 정보(호스트·포트·사용자·비밀번호)를 묻습니다. Crowfoot 화면에서 메인블로그 문서의 데이터베이스 탭에 있는 값을 넣으면 `.env`에 저장되고, 다음부터는 묻지 않습니다. 그다음 가짜 메일함을 띄우고 앱을 띄웁니다. 다 뜨면 http://localhost:8080 으로 들어갑니다.

| 계정 | 비밀번호 | 들어 있는 것 |
|---|---|---|
| owner@local.test | Passw0rd! | 블로그 `travel-log`의 블로그장. 참여 신청 1건이 기다리는 중 |
| member@local.test | Passw0rd! | `travel-log` 멤버, 블로그 `daily-note`의 블로그장 |
| newbie@local.test | Passw0rd! | `travel-log`에 참여 신청한 회원 |
| admin@local.test | Passw0rd! | 관리자 |

이 계정들은 Crowfoot DB에 이미 들어 있습니다(`local/seed.sql`과 같은 내용, 번호 101부터).
블로그는 `http://localhost:8080/blog/travel-log`, 운영 화면은 뒤에 `/manage`를 붙입니다.
게시판(004): 블로그 홈에서 글 목록·카테고리·글쓰기, 글 상세에서 댓글·좋아요·링크 복사, 메인에서 피드 탭과 통합 검색(`/search`), 태그 글(`/tags/{이름}`), 공지사항(`/notices`, 관리자만 쓰기)을 씁니다.
게시판 예시 글·댓글은 `local/seed.sql` 아래쪽에 있고 Crowfoot DB에는 아직 넣지 않았습니다.
소셜·알림(003): 닉네임을 누르면 회원 프로필(`/profile/{번호}`)에서 팔로우·차단·신고·팔로워 목록을 보고, 상단 종 아이콘으로 알림 사이드바(탭·읽음·삭제, 30초마다 개수 확인)를 엽니다. 글·댓글·블로그 신고 버튼, 내 정보의 소개 고치기·알림 설정(`/me/notifications.html`)·차단 목록(`/me/blocks.html`)도 있습니다. 예시 팔로우·알림은 `local/seed.sql` 맨 아래에 있습니다.
관리자(005): `admin@local.test`로 로그인하면 상단에 "관리자" 링크가 생기고 `/admin`에서 회원(가린 이메일·전화번호, "전체 보기")·블로그(숨김·강제 폐쇄·블로그장 경고·바로 강퇴)·신고함·글과 댓글(숨김·삭제·복구)·활동 기록·통계 탭을 씁니다. 관리자가 아닌 계정에는 `/admin`이 404입니다. 예시 관리자 신고·숨긴 글·경고는 `local/seed.sql` 맨 아래에 있습니다.
회원가입 인증번호 메일은 실제로 나가지 않고 가짜 메일함 http://localhost:8025 (Mailpit)에 쌓입니다. 아무 이메일 주소로 가입해도 그 화면에서 인증번호를 볼 수 있습니다.
실제 Gmail로 보내려면 `.env`의 `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_SMTP_AUTH=true`, `MAIL_STARTTLS=true`로 바꾸고 `MAIL_USERNAME`, `MAIL_PASSWORD`(보내는 Gmail 계정의 앱 비밀번호)를 채웁니다.
DB 구조를 바꿀 때는 Crowfoot 문서를 먼저 고치고 변경 계획(plan_migration)을 승인해 DB에 반영한 뒤, `docs/04-design/schema.sql`을 export_ddl로 다시 만듭니다.
멈출 때는 Ctrl+C, 가짜 메일함을 끄려면 `docker compose -f local/docker-compose.yml down`.

## 실행

준비물: JDK 21. DB는 Crowfoot DB를 쓰므로 MySQL을 따로 띄우지 않습니다.

```bash
# 1. DB: Crowfoot 문서 654에 연결된 MySQL(cf_u33_d1). 구조는 Crowfoot에서 고치고 plan_migration → 승인 → apply_migration으로 반영한다.
#    접속 정보는 Crowfoot 화면의 데이터베이스 탭에서 본다.

# 2. 환경변수: .env.example을 .env로 복사해 채운다. 실행할 때 자동으로 읽는다.
cp .env.example .env
#   DB_URL=jdbc:mysql://{Crowfoot 호스트}:{포트}/cf_u33_d1?characterEncoding=UTF-8
#   DB_USERNAME / DB_PASSWORD: Crowfoot 데이터베이스 탭의 값
#   APP_BASE_URL=http://localhost:8080 (글 미리보기 og 주소)
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

Testcontainers가 테스트 때만 쓰고 버리는 MySQL 8.0 컨테이너를 띄우고(`docs/04-design/schema.sql`), 메일은 GreenMail(SMTP 3025)로 받습니다. 테스트가 데이터를 지우고 다시 넣기 때문에 Crowfoot DB에는 연결하지 않습니다. 자동 테스트는 개발 환경(Claude 작업 환경)에서 돌리므로, 내 컴퓨터에서 앱만 쓸 때는 필요 없습니다.

## 문서

- [CLAUDE.md](CLAUDE.md): AI 코딩 에이전트 작업 규칙
- [.specify/memory/constitution.md](.specify/memory/constitution.md): 개발 원칙 (Spec Kit)
- [specs/](specs/README.md): 기능별 명세. 001 회원 [specs/001-member-auth/](specs/001-member-auth/spec.md), 002 블로그 [specs/002-blog-management/](specs/002-blog-management/spec.md), 003 소셜·알림 [specs/003-social-notification/](specs/003-social-notification/spec.md), 004 게시판 [specs/004-board/](specs/004-board/spec.md), 005 관리자 [specs/005-admin-moderation/](specs/005-admin-moderation/spec.md)
  (API 계약 `contracts/`, 확인 방법 `quickstart.md`)
- [docs/](docs/README.md): 원본 요구사항 문서와 결정 기록
