# 001 회원 기능 확인 가이드 (Quickstart)

구현이 끝난 뒤 이 기능이 명세대로 동작하는지 손으로, 그리고 테스트로 확인하는 방법이다.
API 형식은 [contracts/member-auth-api.yaml](contracts/member-auth-api.yaml), 테이블 규칙은 [data-model.md](data-model.md)를 본다.

## 준비

| 항목 | 값 |
|---|---|
| JDK | 21 |
| DB | 로컬: Docker MySQL 8.0에 `docs/04-design/schema.sql`. 공유 개발 DB: Crowfoot 매니지드 MySQL `cf_u33_d1`(접속 정보는 Crowfoot 화면의 데이터베이스 탭) |
| 메일 | 로컬은 GreenMail(SMTP 3025, 웹 없음) 또는 MailHog. 실제 발송은 Gmail 앱 비밀번호 |
| Turnstile | 로컬·테스트는 Cloudflare 테스트 키(항상 통과 / 항상 실패) |

환경변수(`.env.example`로 제공):

```text
DB_URL, DB_USERNAME, DB_PASSWORD
JWT_SECRET                      # 32바이트 이상
CODE_HASH_SECRET                # 인증번호 HMAC 키
MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD
TURNSTILE_SITE_KEY, TURNSTILE_SECRET_KEY
FILE_STORAGE_ROOT               # 프로필 사진 저장 폴더
```

## 실행

```bash
./gradlew test                 # 단위·통합 테스트 (Testcontainers MySQL, GreenMail)
./gradlew bootRun              # http://localhost:8080
```

`ddl-auto=validate`라 엔티티와 DB가 다르면 시작에 실패한다. 실패하면 Crowfoot 문서와 DB가 같은지 먼저 본다(research R-02).

## 확인 시나리오

각 시나리오는 명세의 사용자 스토리 인수 시나리오와 성공 기준(SC)에 대응한다. 시간 경계는 테스트에서 `Clock`을 고정해 확인한다.

| # | 확인할 것 | 방법 | 기대 결과 | 근거 |
|---|---|---|---|---|
| 1 | 가입 여부가 드러나지 않음 | 새 이메일과 가입된 이메일(`minji@example.com`)로 `POST /api/auth/signup/email-code` | 둘 다 202, 본문·헤더 같음. 메일함에는 인증번호 / "이미 가입" 안내 | SC-001 |
| 2 | 인증번호 경계 | 10분 경과, 5번째 오류, 재발송 뒤 이전 번호 | 모두 400 CODE_INVALID | SC-002 |
| 3 | 인증 뒤 30분 | 인증 후 31분에 `POST /api/auth/signup` | 403 EMAIL_NOT_VERIFIED | FR-007 |
| 4 | 입력 규칙 | 비밀번호 `abcd1234`, 닉네임 `Admin1`·`abc`(이미 `ABC` 있음), 전화 `0212345678`, 이름 `김` | 모두 400 또는 409 | SC-011 |
| 5 | 동시 가입 | 같은 닉네임으로 가입 요청 2개 동시 전송 | 계정 1개만 생김 | SC-009 |
| 6 | 로그인 실패 | 같은 이메일 3번 실패 → 4번째 captchaToken 없이 | 400 CAPTCHA_REQUIRED | SC-003 |
| 7 | 잠금 | 5번 실패 → 올바른 비밀번호 | 423 LOCKED. 5분 뒤 성공 | SC-003 |
| 8 | 없는 이메일 | 없는 이메일로 5번 실패 | 6·7번과 같은 응답 | D-97 |
| 9 | 30분 무활동 | 유지 미체크 로그인 → 폴링만 31분 → `POST /api/auth/refresh` | 401 | SC-005 |
| 10 | 14일 고정 | 유지 체크 로그인 → 13일째 refresh 성공 → 14일 1분 뒤 401 | | SC-005, D-97 |
| 11 | 로그아웃 뒤 RT | 로그아웃 전 RT 값을 복사해 refresh | 401 | SC-004 |
| 12 | 비밀번호 변경 | 기기 A·B 로그인 → A에서 변경 | A는 계속 로그인, B의 refresh는 401 | FR-044, D-97 |
| 13 | 재설정 | 잠긴 계정에서 재설정 | 모든 기기 로그아웃, 잠금 풀림 | FR-033 |
| 14 | 재설정 메일 | 없는 이메일로 재설정 요청 | 202, 메일 없음 | FR-030, D-97 |
| 15 | 이메일 찾기 | 샘플 회원 `김민지`·`010-0000-0002` | `mi***@example.com`, 응답 어디에도 원문 없음 | SC-006 |
| 16 | 이메일 찾기 제한 | 같은 IP 6번째 | 429 | SC-007 |
| 17 | 탈퇴 막기 | 블로그장 `minji@example.com`(개발 일지)으로 탈퇴 | 409 OWNS_BLOGS, 블로그 목록 표시 | SC-008 |
| 18 | 탈퇴·재가입 | 블로그장이 아닌 회원 탈퇴 → 같은 이메일로 바로 가입 | 가입 성공, 이전 닉네임은 30일 동안 쓸 수 없음 | SC-008, D-97 |
| 19 | 프로필 사진 | 3MB 초과, svg, 확장자만 jpg인 파일 | 모두 400. 정상 jpg는 EXIF 없이 저장 | FR-043 |
| 20 | 응답 시간 | 위 API 각각 | 1초 목표, 2초 이내 | SC-010 |

샘플 데이터: Crowfoot DB에 회원 5명(1 관리자, 2 민지·블로그장, 3 준호, 4 소라, 5 태양)이 있다. 비밀번호 해시는 가짜 값이라 로그인 시나리오는 가입으로 만든 계정이나 테스트 픽스처를 쓴다.
