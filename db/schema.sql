-- 원천은 Crowfoot 문서 654 "메인블로그"(https://crowfoot.java21.net/workspaces/54/models/654). 손으로 고치지 않음.
-- Crowfoot export_ddl로 다시 만든다(research R-02). 2026-10-08, 문서 버전 41, 36개 테이블(shedlock 포함).
-- ADM-08(D-112): system_inquiries는 문서 버전 45에서 더하고 운영 DB에 만든 표를 그대로 옮겼다(2026-10-12).
-- 005(D-103 #11·#12): comments.deleted_by, owner_warnings.reason과 admin_actions.action_type 설명은 개발 DB에 먼저 반영된 것을 손으로 맞췄다(005 T057).

-- 메인블로그 — MySQL DDL

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '회원 번호',
    email VARCHAR(255) COMMENT '이메일(로그인 아이디)-----소문자로 저장. 탈퇴 즉시 NULL로 비워 같은 이메일로 재가입 가능',
    withdrawn_email VARCHAR(255) COMMENT '탈퇴 전 이메일-----탈퇴 후 30일 보관용. 30일 뒤 삭제',
    password_hash VARCHAR(100) COMMENT '비밀번호 해시-----bcrypt. 소셜로만 가입한 회원은 NULL이고 이메일 로그인 불가, 비밀번호 찾기로 만들 수 있음(D-125)',
    name VARCHAR(50) COMMENT '이름-----앞뒤 공백 제거 후 2~20자 한글·영문·공백(D-97). 가입 뒤 변경 불가. 탈퇴 30일 뒤 삭제',
    nickname VARCHAR(30) COMMENT '닉네임-----2~12자, 대소문자 무시 중복 불가, 예약어 포함 불가(D-97). 탈퇴 30일 동안 유지 후 삭제',
    phone VARCHAR(20) COMMENT '전화번호-----010으로 시작하는 11자리 숫자(D-97). 1차는 중복 허용',
    bio VARCHAR(500) COMMENT '소개-----길이·허용 문자 미정',
    role VARCHAR(10) NOT NULL DEFAULT 'USER' COMMENT '계정 종류',
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT '회원 상태',
    withdrawn_at DATETIME COMMENT '탈퇴 일시',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '가입 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_nickname UNIQUE (nickname),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'WITHDRAWN'))
) COMMENT='회원-----일반 회원과 메인 관리자(role=ADMIN). 탈퇴 30일 뒤 개인정보 컬럼을 비우고 행은 남긴다';
CREATE TABLE email_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    email VARCHAR(255) NOT NULL COMMENT '이메일',
    code_hash VARCHAR(100) NOT NULL COMMENT '인증번호 해시',
    sent_at DATETIME NOT NULL COMMENT '발송 시각-----다시 보내기 1분에 한 번 확인',
    expires_at DATETIME NOT NULL COMMENT '만료 시각-----발송 10분 뒤',
    fail_count INT NOT NULL DEFAULT 0 COMMENT '틀린 횟수-----5번이면 무효',
    verified_at DATETIME COMMENT '인증 완료 시각-----가입 마지막 단계에서 확인. 완료 뒤 30분 유효(D-97)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_email_verifications_email UNIQUE (email)
) COMMENT='가입 이메일 인증번호-----가입 전이라 회원 번호 없이 이메일 기준. 만료되거나 쓰이면 바로 삭제';
CREATE TABLE user_agreements (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    user_id BIGINT NOT NULL COMMENT '회원 번호',
    agreement_type VARCHAR(20) NOT NULL COMMENT '동의 항목-----TERMS 이용약관, PRIVACY 개인정보 수집·이용',
    agreed_at DATETIME NOT NULL COMMENT '동의 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_user_agreements_user_id_agreement_type UNIQUE (user_id, agreement_type),
    CONSTRAINT ck_user_agreements_type CHECK (agreement_type IN ('TERMS', 'PRIVACY'))
) COMMENT='가입 동의 기록';
CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    user_id BIGINT NOT NULL COMMENT '회원 번호',
    token_hash VARCHAR(100) NOT NULL COMMENT '토큰 해시',
    remember_me TINYINT(1) NOT NULL DEFAULT FALSE COMMENT '로그인 유지 여부-----체크 14일, 미체크 30분 무활동 만료',
    last_active_at DATETIME NOT NULL COMMENT '마지막 활동 시각-----사용자 행동 요청마다 갱신. 30초 폴링은 제외',
    expires_at DATETIME NOT NULL COMMENT '만료 시각',
    revoked TINYINT(1) NOT NULL DEFAULT FALSE COMMENT '폐기 여부',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
) COMMENT='Refresh Token-----기기(로그인)마다 한 건. 만료분은 04:00 배치가 삭제';
CREATE TABLE password_reset_codes (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    user_id BIGINT NOT NULL COMMENT '회원 번호',
    code_hash VARCHAR(100) NOT NULL COMMENT '인증번호 해시',
    sent_at DATETIME NOT NULL COMMENT '발송 시각',
    expires_at DATETIME NOT NULL COMMENT '만료 시각-----30분',
    fail_count INT NOT NULL DEFAULT 0 COMMENT '틀린 횟수',
    used TINYINT(1) NOT NULL DEFAULT FALSE COMMENT '사용 여부',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id)
) COMMENT='비밀번호 재설정 인증번호-----쓰이거나 만료되면 바로 삭제';
CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    user_id BIGINT NOT NULL COMMENT '회원 번호',
    token_hash VARCHAR(100) NOT NULL COMMENT '토큰 해시',
    expires_at DATETIME NOT NULL COMMENT '만료 시각-----10분',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_tokens_token_hash UNIQUE (token_hash)
) COMMENT='이메일 찾기 재설정 임시 토큰-----이메일 찾기 결과 계정마다 발급. 재설정은 회원 번호 대신 이 토큰으로 받는다';
CREATE TABLE blogs (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '블로그 번호',
    name VARCHAR(100) NOT NULL COMMENT '블로그 이름-----중복 허용. 글자 수 미정',
    address VARCHAR(30) COMMENT '블로그 주소-----/blog/{주소}. 영문 소문자·숫자·- 3~30자, 중복 불가. 폐쇄 30일 뒤 NULL',
    description VARCHAR(500) COMMENT '소개-----글자 수 미정',
    visibility VARCHAR(10) NOT NULL DEFAULT 'PUBLIC' COMMENT '공개 범위-----PUBLIC 공개, LINK 일부 공개, PRIVATE 비공개',
    join_policy VARCHAR(10) NOT NULL DEFAULT 'APPROVAL' COMMENT '참여 방식-----FREE 자유, APPROVAL 승인제',
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태-----ACTIVE 운영 중, CLOSING 폐쇄 예정, CLOSED 폐쇄됨',
    close_scheduled_at DATETIME COMMENT '폐쇄 예정 시각-----누른 날짜 + 7일 04:00',
    close_reason VARCHAR(20) COMMENT '폐쇄 사유-----OWNER 블로그장, OWNER_REVOKED 권한 박탈, ADMIN 관리자 강제',
    closed_at DATETIME COMMENT '폐쇄된 시각',
    member_count INT NOT NULL DEFAULT 1 COMMENT '멤버 수-----인기순 정렬 기준(D-89)',
    hidden TINYINT(1) NOT NULL DEFAULT FALSE COMMENT '숨김 여부-----관리자 숨김. 누가 숨겼는지는 admin_actions',
    hidden_at DATETIME COMMENT '숨긴 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_blogs_address UNIQUE (address),
    CONSTRAINT ck_blogs_visibility CHECK (visibility IN ('PUBLIC', 'LINK', 'PRIVATE')),
    CONSTRAINT ck_blogs_join_policy CHECK (join_policy IN ('FREE', 'APPROVAL')),
    CONSTRAINT ck_blogs_status CHECK (status IN ('ACTIVE', 'CLOSING', 'CLOSED')),
    CONSTRAINT ck_blogs_close_reason CHECK (close_reason IS NULL OR close_reason IN ('OWNER', 'OWNER_REVOKED', 'ADMIN'))
) COMMENT='블로그-----블로그장은 blog_members(role=OWNER)로 저장. 폐쇄 30일 뒤에도 행은 남기고 주소만 비운다';
CREATE TABLE blog_share_links (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    blog_id BIGINT NOT NULL COMMENT '블로그 번호',
    token VARCHAR(64) NOT NULL COMMENT '링크 값-----추측할 수 없는 무작위 값',
    active TINYINT(1) NOT NULL DEFAULT TRUE COMMENT '유효 여부',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_blog_share_links_token UNIQUE (token)
) COMMENT='일부 공개 공유 링크-----블로그당 유효한 링크는 하나. 새로 만들면 이전 링크 active=false';
CREATE TABLE blog_members (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    user_id BIGINT NOT NULL COMMENT '회원 번호',
    blog_id BIGINT NOT NULL COMMENT '블로그 번호',
    role VARCHAR(10) NOT NULL DEFAULT 'MEMBER' COMMENT '역할-----OWNER 블로그장, MANAGER 부블로그장, MEMBER 멤버',
    joined_at DATETIME NOT NULL COMMENT '참여 시각',
    manager_since DATETIME COMMENT '부블로그장이 된 시각-----권한 박탈 때 가장 먼저 된 부블로그장을 고르는 기준',
    perm_blog_info TINYINT(1) NOT NULL DEFAULT FALSE COMMENT '권한: 블로그 정보 수정-----체크박스 단위(영역 3개 또는 세부 항목) 미정. 우선 영역 3개',
    perm_member_manage TINYINT(1) NOT NULL DEFAULT FALSE COMMENT '권한: 멤버 관리',
    perm_post_manage TINYINT(1) NOT NULL DEFAULT FALSE COMMENT '권한: 글 관리',
    owner_blog_id BIGINT GENERATED ALWAYS AS (CASE WHEN role = 'OWNER' THEN blog_id END) STORED COMMENT '블로그장 유일 확인용',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_blog_members_blog_id_user_id UNIQUE (blog_id, user_id),
    CONSTRAINT uk_blog_members_owner_blog_id UNIQUE (owner_blog_id),
    CONSTRAINT ck_blog_members_role CHECK (role IN ('OWNER', 'MANAGER', 'MEMBER'))
) COMMENT='블로그 멤버십-----회원과 블로그의 역할. 한 쌍에 한 건, 블로그장은 블로그당 한 명(owner_blog_id 유일)';
CREATE TABLE blog_join_requests (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    user_id BIGINT NOT NULL COMMENT '신청 회원 번호',
    blog_id BIGINT NOT NULL COMMENT '블로그 번호',
    status VARCHAR(15) NOT NULL DEFAULT 'PENDING' COMMENT '상태-----PENDING, APPROVED, REJECTED, CANCELED, AUTO_REJECTED',
    requested_at DATETIME NOT NULL COMMENT '신청 시각',
    processed_at DATETIME COMMENT '처리 시각-----거절 7일 뒤 재신청 가능',
    processed_by BIGINT COMMENT '처리한 회원 번호-----users.id. 같은 두 테이블 사이 관계는 하나만 그릴 수 있어 FK 선 없이 둠',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT ck_blog_join_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED', 'AUTO_REJECTED'))
) COMMENT='블로그 참여 신청';
CREATE TABLE blog_delegations (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    to_user_id BIGINT NOT NULL COMMENT '받는 멤버 회원 번호',
    blog_id BIGINT NOT NULL COMMENT '블로그 번호',
    from_user_id BIGINT NOT NULL COMMENT '요청한 블로그장 회원 번호-----users.id. 관계는 받는 멤버 쪽만 그림',
    status VARCHAR(15) NOT NULL DEFAULT 'PENDING' COMMENT '상태-----PENDING, ACCEPTED, REJECTED, AUTO_CANCELED',
    requested_at DATETIME NOT NULL COMMENT '요청 시각',
    expires_at DATETIME NOT NULL COMMENT '자동 취소 시각-----요청 7일 뒤',
    responded_at DATETIME COMMENT '응답 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT ck_blog_delegations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'AUTO_CANCELED'))
) COMMENT='블로그장 위임 요청';
CREATE TABLE blog_sanctions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    blog_id BIGINT NOT NULL COMMENT '블로그 번호',
    user_id BIGINT NOT NULL COMMENT '대상 회원 번호',
    report_id BIGINT COMMENT '연결된 신고 번호',
    sanction_type VARCHAR(15) NOT NULL COMMENT '종류-----WARNING, SUSPEND, UNSUSPEND, KICK',
    suspend_period VARCHAR(10) COMMENT '정지 기간-----DAYS_3, DAYS_14, DAYS_30, PERMANENT',
    reason VARCHAR(500) COMMENT '사유',
    starts_at DATETIME NOT NULL COMMENT '시작 시각',
    ends_at DATETIME COMMENT '종료 시각-----영구 정지는 NULL',
    handled_by BIGINT NOT NULL COMMENT '처리한 회원 번호-----users.id(블로그장 또는 멤버 관리 권한 부블로그장)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT ck_blog_sanctions_type CHECK (sanction_type IN ('WARNING', 'SUSPEND', 'UNSUSPEND', 'KICK')),
    CONSTRAINT ck_blog_sanctions_period CHECK (suspend_period IS NULL OR suspend_period IN ('DAYS_3', 'DAYS_14', 'DAYS_30', 'PERMANENT'))
) COMMENT='멤버 제재 기록-----경고·정지·정지 해제·강제 퇴장. 1년 보관. 정지 횟수는 블로그별, 1년 지난 것 제외';
CREATE TABLE blog_blacklists (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    sanction_id BIGINT NOT NULL COMMENT '원인 강제 퇴장 기록 번호',
    blog_id BIGINT NOT NULL COMMENT '블로그 번호',
    name_hash VARCHAR(64) NOT NULL COMMENT '이름 해시',
    email_hash VARCHAR(64) NOT NULL COMMENT '이메일 해시',
    phone_hash VARCHAR(64) NOT NULL COMMENT '전화번호 해시',
    released TINYINT(1) NOT NULL DEFAULT FALSE COMMENT '해제 여부',
    released_at DATETIME COMMENT '해제 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '등록 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id)
) COMMENT='블로그 블랙리스트-----강제 퇴장된 사람의 이름·이메일·전화번호 해시. 고치거나 지울 수 없고 해제는 문의로만. 폐쇄 30일 뒤 삭제';
CREATE TABLE blacklist_appeals (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    blacklist_id BIGINT NOT NULL COMMENT '블랙리스트 번호-----블로그는 블랙리스트에서 찾음. 원문은 보여주지 않음',
    user_id BIGINT NOT NULL COMMENT '문의한 회원 번호',
    content VARCHAR(1000) COMMENT '문의 내용',
    name_matched TINYINT(1) NOT NULL COMMENT '이름 일치 여부',
    phone_matched TINYINT(1) NOT NULL COMMENT '전화번호 일치 여부',
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT '상태-----PENDING, RELEASED, REJECTED',
    processed_by BIGINT COMMENT '처리한 회원 번호-----users.id',
    processed_at DATETIME COMMENT '처리 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT ck_blacklist_appeals_status CHECK (status IN ('PENDING', 'RELEASED', 'REJECTED'))
) COMMENT='블랙리스트 해제 문의';
CREATE TABLE follows (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    follower_id BIGINT NOT NULL COMMENT '팔로우하는 회원 번호',
    followee_id BIGINT NOT NULL COMMENT '팔로우받는 회원 번호-----users.id. 같은 두 테이블 사이 관계는 하나만 그릴 수 있어 FK 선 없이 둠',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '팔로우한 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_follows_follower_id_followee_id UNIQUE (follower_id, followee_id)
) COMMENT='회원 팔로우-----한 쌍에 한 건. 다시 누르면 행 삭제';
CREATE TABLE blog_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    blog_id BIGINT NOT NULL COMMENT '블로그 번호',
    user_id BIGINT NOT NULL COMMENT '구독한 회원 번호',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '구독한 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_blog_subscriptions_user_id_blog_id UNIQUE (user_id, blog_id)
) COMMENT='블로그 구독-----한 쌍에 한 건. 비공개로 바뀌어도 유지';
CREATE TABLE user_blocks (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    blocker_id BIGINT NOT NULL COMMENT '차단한 회원 번호',
    blocked_id BIGINT NOT NULL COMMENT '차단당한 회원 번호-----users.id(FK 선 없음)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '차단한 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_user_blocks_blocker_id_blocked_id UNIQUE (blocker_id, blocked_id)
) COMMENT='회원 차단-----메인과 모든 블로그에 같이 적용';
CREATE TABLE reports (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '신고 번호',
    blog_id BIGINT COMMENT '대상 블로그 번호-----메인 프로필 회원 신고는 NULL',
    reporter_id BIGINT NOT NULL COMMENT '신고한 회원 번호',
    target_type VARCHAR(10) NOT NULL COMMENT '대상 종류-----USER, BLOG, POST, COMMENT',
    target_id BIGINT NOT NULL COMMENT '대상 번호',
    target_snapshot TEXT NOT NULL COMMENT '신고 당시 대상 내용',
    reason VARCHAR(30) NOT NULL COMMENT '사유-----사유 목록 미정',
    reason_detail VARCHAR(500) COMMENT '직접 입력 사유-----직접 입력 칸 여부 미정',
    route VARCHAR(10) NOT NULL COMMENT '접수처-----BLOG 블로그장, ADMIN 메인 관리자(D-93)',
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태-----PENDING, DONE',
    result VARCHAR(20) COMMENT '처리 결과-----NONE, WARNING, SUSPEND, KICK(블로그) / OWNER_WARNING, OWNER_KICK, BLOG_CLOSE(관리자)',
    handled_by BIGINT COMMENT '처리한 회원 번호-----users.id(FK 선 없음)',
    handled_at DATETIME COMMENT '처리 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '신고 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT ck_reports_target_type CHECK (target_type IN ('USER', 'BLOG', 'POST', 'COMMENT')),
    CONSTRAINT ck_reports_route CHECK (route IN ('BLOG', 'ADMIN')),
    CONSTRAINT ck_reports_status CHECK (status IN ('PENDING', 'DONE'))
) COMMENT='신고-----대상은 target_type + target_id, 당시 내용은 target_snapshot(D-85). 1년 보관. 한 신고에 결과 하나';
CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    recipient_id BIGINT NOT NULL COMMENT '받는 회원 번호',
    notification_type VARCHAR(40) NOT NULL COMMENT '알림 종류-----예: POST_COMMENT, POST_LIKE, BLOG_CLOSING, MEMBER_SUSPENDED',
    tab VARCHAR(15) NOT NULL COMMENT '탭-----COMMENT, LIKE, FOLLOW, SUBSCRIBE, BLOG, OPERATION',
    target_type VARCHAR(15) COMMENT '관련 대상 종류-----POST, COMMENT, USER, BLOG, REPORT 등',
    target_id BIGINT COMMENT '관련 대상 번호',
    actor_id BIGINT COMMENT '행동한 회원 번호-----users.id(FK 선 없음). 닉네임만 표시',
    content VARCHAR(300) NOT NULL COMMENT '내용',
    event_key VARCHAR(100) NOT NULL COMMENT '이벤트 키-----같은 이벤트로 같은 회원에게 두 번 만들지 않기 위한 값',
    is_read TINYINT(1) NOT NULL DEFAULT FALSE COMMENT '읽음 여부',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '받은 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_recipient_id_event_key UNIQUE (recipient_id, event_key)
) COMMENT='알림-----받는 사람 본인만 조회. 받은 날부터 30일(또는 회원 설정 7일) 뒤 04:00 배치가 삭제';
CREATE TABLE notification_settings (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    user_id BIGINT NOT NULL COMMENT '회원 번호',
    post_comment TINYINT(1) NOT NULL DEFAULT TRUE COMMENT '내 글에 댓글',
    comment_reply TINYINT(1) NOT NULL DEFAULT TRUE COMMENT '내 댓글에 대댓글',
    post_like TINYINT(1) NOT NULL DEFAULT TRUE COMMENT '내 글 좋아요',
    new_follower TINYINT(1) NOT NULL DEFAULT TRUE COMMENT '새 팔로워',
    blog_subscribed TINYINT(1) NOT NULL DEFAULT TRUE COMMENT '블로그 구독',
    join_request TINYINT(1) NOT NULL DEFAULT TRUE COMMENT '참여 신청',
    join_result TINYINT(1) NOT NULL DEFAULT TRUE COMMENT '참여 승인·거절',
    report_result TINYINT(1) NOT NULL DEFAULT TRUE COMMENT '신고 처리 결과',
    notice TINYINT(1) NOT NULL DEFAULT TRUE COMMENT '공지사항',
    retention_days INT NOT NULL DEFAULT 30 COMMENT '알림 보관 기간(일)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_settings_user_id UNIQUE (user_id),
    CONSTRAINT ck_notification_settings_retention CHECK (retention_days IN (7, 30))
) COMMENT='알림 설정-----회원당 한 건. 끌 수 있는 알림 9가지와 보관 기간. 기본값(모두 켜짐) 미정';
CREATE TABLE blog_closing_notices (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    blog_id BIGINT NOT NULL COMMENT '블로그 번호',
    user_id BIGINT NOT NULL COMMENT '받는 회원 번호',
    close_scheduled_at DATETIME NOT NULL COMMENT '폐쇄 예정 시각-----철회 후 다시 폐쇄하면 다른 회차로 구분',
    stage VARCHAR(10) NOT NULL COMMENT '시점-----IMMEDIATE, D3, D1, WITHDRAWN',
    sent_at DATETIME NOT NULL COMMENT '보낸 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_blog_closing_notices_blog_id_close_scheduled_at_stage_user_id UNIQUE (blog_id, close_scheduled_at, stage, user_id),
    CONSTRAINT ck_blog_closing_notices_stage CHECK (stage IN ('IMMEDIATE', 'D3', 'D1', 'WITHDRAWN'))
) COMMENT='폐쇄 알림 발송 기록-----블로그·폐쇄 예정 시각·시점·회원 조합은 한 건(멤버 겠 구독자, 배치 재실행에도 한 번)';
CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    blog_id BIGINT NOT NULL COMMENT '블로그 번호',
    name VARCHAR(30) NOT NULL COMMENT '이름',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '순서',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id)
) COMMENT='카테고리-----블로그당 최대 30개, 이름 1~30자(설정값). 지우면 그 글은 미분류(category_id NULL)로 옮김(D-100 #8)';
CREATE TABLE posts (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '글 번호',
    blog_id BIGINT COMMENT '블로그 번호',
    author_id BIGINT NOT NULL COMMENT '작성자 회원 번호',
    category_id BIGINT COMMENT '카테고리 번호',
    board_type VARCHAR(10) NOT NULL DEFAULT 'BLOG' COMMENT '게시판 종류-----BLOG 개별 블로그, NOTICE 메인 공지',
    title VARCHAR(30) NOT NULL COMMENT '제목-----1~30자',
    content TEXT NOT NULL COMMENT '본문(마크다운)-----최대 5,000자를 서버에서 검사(D-92)',
    status VARCHAR(10) NOT NULL DEFAULT 'VISIBLE' COMMENT '상태-----VISIBLE, HIDDEN, DELETED',
    author_left TINYINT(1) NOT NULL DEFAULT FALSE COMMENT '작성자 블로그 탈퇴 여부-----블로그를 떠나거나 강퇴되면 true → "탈퇴한 계정" 표시',
    view_count INT NOT NULL DEFAULT 0 COMMENT '조회수',
    like_count INT NOT NULL DEFAULT 0 COMMENT '좋아요 수-----인기순 기준(D-89)',
    hidden_at DATETIME COMMENT '숨긴 시각',
    deleted_at DATETIME COMMENT '삭제 시각',
    deleted_by BIGINT COMMENT '삭제한 회원 번호-----users.id(FK 선 없음). 작성자가 아니면 "내 글 삭제됨" 알림',
    edited_at DATETIME(6) COMMENT '고친 일시-----글을 수정하면 기록. NULL이면 고친 적 없음. 목록에 "수정됨" 표시(D-133)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '작성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT ck_posts_status CHECK (status IN ('VISIBLE', 'HIDDEN', 'DELETED')),
    CONSTRAINT ck_posts_board CHECK ((board_type = 'BLOG' AND blog_id IS NOT NULL) OR (board_type = 'NOTICE' AND blog_id IS NULL))
) COMMENT='글-----개별 블로그 글과 메인 공지사항(blog_id NULL). 삭제는 상태로, 30일 뒤 완전 삭제. 공유 수·미리보기는 저장하지 않음';
CREATE TABLE tags (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    name VARCHAR(30) NOT NULL COMMENT '태그 이름',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_tags_name UNIQUE (name)
) COMMENT='태그-----1~20자 한글·영문·숫자·_, 영문 소문자. 금칙어 없음(D-96)';
CREATE TABLE post_tags (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    tag_id BIGINT NOT NULL COMMENT '태그 번호',
    post_id BIGINT NOT NULL COMMENT '글 번호',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_post_tags UNIQUE (post_id, tag_id)
) COMMENT='글 태그-----글 하나에 최대 10개';
CREATE TABLE blog_tags (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    blog_id BIGINT NOT NULL COMMENT '블로그 번호',
    tag_id BIGINT NOT NULL COMMENT '태그 번호',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_blog_tags_blog_id_tag_id UNIQUE (blog_id, tag_id)
) COMMENT='블로그 태그-----블로그 하나에 최대 10개';
CREATE TABLE images (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    uploader_id BIGINT NOT NULL COMMENT '올린 회원 번호',
    owner_type VARCHAR(15) NOT NULL COMMENT '소속 종류-----POST, PROFILE, BLOG_COVER, BLOG_BACKGROUND',
    owner_id BIGINT COMMENT '소속 번호-----글·회원·블로그 번호. 글 저장 전에는 NULL',
    stored_name VARCHAR(64) NOT NULL COMMENT '저장 이름(UUID)',
    original_name VARCHAR(255) NOT NULL COMMENT '원래 파일 이름',
    content_type VARCHAR(30) NOT NULL COMMENT '형식-----jpg·jpeg·png·gif·webp',
    size_bytes INT NOT NULL COMMENT '크기(바이트)-----장당 3MB',
    deleted_at DATETIME COMMENT '삭제 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '올린 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_images_stored_name UNIQUE (stored_name),
    CONSTRAINT ck_images_owner_type CHECK (owner_type IN ('POST', 'PROFILE', 'BLOG_COVER', 'BLOG_BACKGROUND'))
) COMMENT='이미지-----글 이미지·프로필 사진·블로그 대표 이미지·블로그 배경 이미지. 파일은 FileStorage, DB에는 정보만. 삭제 30일 뒤 완전 삭제';
CREATE TABLE comments (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '댓글 번호',
    parent_id BIGINT COMMENT '부모 댓글 번호',
    author_id BIGINT NOT NULL COMMENT '작성자 회원 번호',
    post_id BIGINT NOT NULL COMMENT '글 번호',
    reply_to_user_id BIGINT COMMENT '답할 대상 회원 번호-----users.id(FK 선 없음). @닉네임 표시',
    content VARCHAR(500) NOT NULL COMMENT '내용',
    edited TINYINT(1) NOT NULL DEFAULT FALSE COMMENT '수정 여부',
    status VARCHAR(10) NOT NULL DEFAULT 'VISIBLE' COMMENT '상태-----VISIBLE, HIDDEN, DELETED',
    deleted_at DATETIME COMMENT '삭제 시각',
    deleted_by BIGINT COMMENT '삭제한 회원-----users.id(FK 선 없음). deleted_by = author_id면 작성자가 지운 것이라 복구 불가(D-103 #11, ADM)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '작성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT ck_comments_status CHECK (status IN ('VISIBLE', 'HIDDEN', 'DELETED'))
) COMMENT='댓글-----대댓글은 1단계(부모는 최상위 댓글). 답글 있는 댓글 삭제는 "삭제된 댓글입니다"로 남김';
CREATE TABLE post_likes (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    user_id BIGINT NOT NULL COMMENT '회원 번호',
    post_id BIGINT NOT NULL COMMENT '글 번호',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '누른 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_post_likes_user_id_post_id UNIQUE (user_id, post_id)
) COMMENT='좋아요-----한 회원이 한 글에 하나. 다시 누르면 취소';
CREATE TABLE recent_searches (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    user_id BIGINT NOT NULL COMMENT '회원 번호',
    keyword VARCHAR(20) NOT NULL COMMENT '검색어',
    searched_at DATETIME NOT NULL COMMENT '검색한 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id)
) COMMENT='최근 검색어-----본인에게만 최근 10개. 같은 검색어는 searched_at만 바꿔 맨 앞으로(중복 없음), 비회원은 저장 안 함(D-100 #17)';
CREATE TABLE post_views (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    post_id BIGINT NOT NULL COMMENT '글 번호',
    viewer_key VARCHAR(64) NOT NULL COMMENT '본 사람 구분값-----회원은 U:{회원 번호}, 비회원은 G:{무작위 쿠키 값, 1년}(D-100 #20)',
    view_date DATE NOT NULL COMMENT '본 날짜',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_post_views_post_id_viewer_key_view_date UNIQUE (post_id, viewer_key, view_date)
) COMMENT='조회 기록-----같은 글·같은 사람·같은 날은 한 건(D-77). 작성자 본인·봇은 세지 않음(D-100 #20·#21). 7일 지난 행은 04:00 정리(설정값)';
CREATE TABLE admin_actions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    admin_id BIGINT NOT NULL COMMENT '관리자 회원 번호',
    report_id BIGINT COMMENT '관련 신고 번호',
    action_type VARCHAR(30) NOT NULL COMMENT '종류-----OWNER_WARNING, OWNER_KICK, AUTO_DELEGATE, CLOSE_SCHEDULE, BLOG_HIDE, BLOG_UNHIDE, BLOG_FORCE_CLOSE, POST_HIDE, POST_UNHIDE, POST_DELETE, COMMENT_HIDE, COMMENT_UNHIDE, COMMENT_DELETE, RESTORE, REPORT_HANDLE, PRIVACY_VIEW',
    target_type VARCHAR(15) NOT NULL COMMENT '대상 종류-----USER, BLOG, POST, COMMENT, REPORT',
    target_id BIGINT NOT NULL COMMENT '대상 번호',
    memo VARCHAR(500) COMMENT '메모',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '조치 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id)
) COMMENT='관리자 활동 기록-----관리자 조치마다 한 건. 개인정보 전체 보기 포함. 1년 보관';
CREATE TABLE owner_warnings (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    blog_id BIGINT NOT NULL COMMENT '블로그 번호',
    user_id BIGINT NOT NULL COMMENT '대상 블로그장 회원 번호',
    report_id BIGINT COMMENT '관련 신고 번호',
    admin_id BIGINT NOT NULL COMMENT '경고한 관리자 회원 번호-----users.id(FK 선 없음)',
    reason VARCHAR(500) COMMENT '경고 사유-----앞뒤 공백 제거 후 1~500자(admin.warning.reason-max-length, D-103 #12)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '경고 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id)
) COMMENT='블로그장 경고-----블로그별로 세고 1년 지난 경고는 제외(D-95). 3회면 권한 박탈. 1년 보관';
CREATE TABLE owner_revocations (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    blog_id BIGINT NOT NULL COMMENT '블로그 번호',
    user_id BIGINT NOT NULL COMMENT '박탈된 회원 번호',
    cause VARCHAR(15) NOT NULL COMMENT '원인-----WARNING_3 경고 3회, DIRECT_KICK 바로 강퇴',
    new_owner_id BIGINT COMMENT '위임받은 부블로그장 회원 번호-----users.id(FK 선 없음). 부블로그장이 없으면 NULL',
    close_scheduled_at DATETIME COMMENT '폐쇄 예정 시각-----부블로그장이 없을 때 박탈일 + 7일 04:00',
    admin_id BIGINT NOT NULL COMMENT '처리한 관리자 회원 번호-----users.id(FK 선 없음)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '박탈 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT ck_owner_revocations_cause CHECK (cause IN ('WARNING_3', 'DIRECT_KICK'))
) COMMENT='블로그장 권한 박탈-----박탈·자동 위임·폐쇄 예약을 한 번의 처리로 기록. 1년 보관';
CREATE TABLE login_failures (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    email VARCHAR(255) NOT NULL COMMENT '이메일-----로그인 때 입력한 이메일(소문자)',
    fail_count INT NOT NULL DEFAULT 0 COMMENT '연속 실패 횟수-----3회부터 사람 확인, 5회면 5분 잠금',
    locked_until DATETIME COMMENT '잠금 해제 시각',
    last_failed_at DATETIME NOT NULL COMMENT '마지막 실패 시각-----오래된 행 정리 기준',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_login_failures_email UNIQUE (email)
) COMMENT='로그인 실패 기록-----입력한 이메일(소문자) 기준 연속 로그인 실패. 가입 여부와 무관하게 세어 가입 여부를 드러내지 않음(D-97). 성공·잠금 해제·재설정 때 삭제, 1일 지난 행은 04:00 배치가 삭제';
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL COMMENT '작업 이름',
    lock_until TIMESTAMP(3) NOT NULL COMMENT '잠금 끝 시각',
    locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '잠근 시각',
    locked_by VARCHAR(255) NOT NULL COMMENT '잠근 서버',
    PRIMARY KEY (name)
) COMMENT='배치 잠금-----ShedLock 잠금 테이블. 매일 04:00 정리 작업이 서버 여러 대에서도 한 번만 돌게 한다. 라이브러리가 정한 구조라 공통 컬럼 규칙의 예외';
CREATE TABLE system_inquiries (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    user_id BIGINT COMMENT '보낸 회원 번호-----users.id(FK 선 없음). 비회원은 NULL',
    email VARCHAR(255) COMMENT '답변 받을 이메일-----비회원만. 관리자 화면에는 가려서 보임(D-22)',
    title VARCHAR(30) NOT NULL COMMENT '제목',
    content VARCHAR(2000) NOT NULL COMMENT '내용',
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT '상태-----PENDING, ANSWERED',
    answer VARCHAR(2000) COMMENT '답변',
    answered_by BIGINT COMMENT '답변한 관리자 번호-----users.id(FK 선 없음)',
    answered_at DATETIME COMMENT '답변 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT ck_system_inquiries_status CHECK (status IN ('PENDING', 'ANSWERED')),
    CONSTRAINT ck_system_inquiries_sender CHECK (user_id IS NOT NULL OR email IS NOT NULL)
) COMMENT='시스템 문의-----회원·비회원이 메인 관리자에게 보내는 문의(ADM-08). 1년 보관 뒤 04:00 배치가 삭제';
CREATE TABLE social_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    user_id BIGINT NOT NULL COMMENT '회원 번호',
    provider VARCHAR(10) NOT NULL COMMENT '서비스-----GOOGLE·KAKAO·NAVER',
    provider_user_id VARCHAR(100) NOT NULL COMMENT '서비스 회원 고유 번호-----구글 sub, 카카오 회원번호, 네이버 id',
    email VARCHAR(255) COMMENT '연결할 때의 이메일-----참고용. 회원 찾기에는 쓰지 않음',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '연결 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_social_accounts_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uk_social_accounts_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_social_accounts_provider CHECK (provider IN ('GOOGLE', 'KAKAO', 'NAVER'))
) COMMENT='소셜 연결-----회원과 구글·카카오·네이버 계정의 연결(D-125). 접근 토큰은 저장하지 않음. 탈퇴하면 바로 삭제';
CREATE TABLE social_pendings (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '번호',
    token_hash VARCHAR(100) NOT NULL COMMENT '대기 토큰 해시-----브라우저에 준 무작위 토큰의 해시',
    provider VARCHAR(10) NOT NULL COMMENT '서비스-----GOOGLE·KAKAO·NAVER',
    provider_user_id VARCHAR(100) NOT NULL COMMENT '서비스 회원 고유 번호',
    email VARCHAR(255) COMMENT '확인된 이메일-----소셜이 확인한 이메일. 없으면 인증번호로 확인한 뒤 채움',
    purpose VARCHAR(10) NOT NULL COMMENT '목적-----SIGNUP(가입 ③단계) · LINK(비밀번호 확인 뒤 연결)',
    remember_me TINYINT(1) NOT NULL DEFAULT 0 COMMENT '로그인 유지',
    expires_at DATETIME NOT NULL COMMENT '만료 시각-----SIGNUP 30분, LINK 10분(설정값)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_social_pendings_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_social_pendings_provider CHECK (provider IN ('GOOGLE', 'KAKAO', 'NAVER')),
    CONSTRAINT ck_social_pendings_purpose CHECK (purpose IN ('SIGNUP', 'LINK'))
) COMMENT='소셜 가입·연결 대기-----소셜에서 돌아왔지만 가입 ③단계나 비밀번호 확인을 끝내지 않은 상태(D-125). 끝나거나 만료되면 삭제, 남은 것은 04:00 정리 작업이 삭제';

ALTER TABLE user_agreements ADD CONSTRAINT fk_user_agreements_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE refresh_tokens ADD CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE password_reset_codes ADD CONSTRAINT fk_password_reset_codes_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE password_reset_tokens ADD CONSTRAINT fk_password_reset_tokens_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE blog_members ADD CONSTRAINT fk_blog_members_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE blog_members ADD CONSTRAINT fk_blog_members_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blog_share_links ADD CONSTRAINT fk_blog_share_links_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blog_join_requests ADD CONSTRAINT fk_blog_join_requests_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE blog_join_requests ADD CONSTRAINT fk_blog_join_requests_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blog_delegations ADD CONSTRAINT fk_blog_delegations_users FOREIGN KEY (to_user_id) REFERENCES users (id);
ALTER TABLE blog_delegations ADD CONSTRAINT fk_blog_delegations_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blog_sanctions ADD CONSTRAINT fk_blog_sanctions_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE blog_sanctions ADD CONSTRAINT fk_blog_sanctions_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blog_sanctions ADD CONSTRAINT fk_blog_sanctions_reports FOREIGN KEY (report_id) REFERENCES reports (id);
ALTER TABLE blog_blacklists ADD CONSTRAINT fk_blog_blacklists_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blog_blacklists ADD CONSTRAINT fk_blog_blacklists_blog_sanctions FOREIGN KEY (sanction_id) REFERENCES blog_sanctions (id);
ALTER TABLE blacklist_appeals ADD CONSTRAINT fk_blacklist_appeals_blog_blacklists FOREIGN KEY (blacklist_id) REFERENCES blog_blacklists (id);
ALTER TABLE blacklist_appeals ADD CONSTRAINT fk_blacklist_appeals_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE blog_tags ADD CONSTRAINT fk_blog_tags_tags FOREIGN KEY (tag_id) REFERENCES tags (id);
ALTER TABLE blog_tags ADD CONSTRAINT fk_blog_tags_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id) ON DELETE CASCADE;
ALTER TABLE follows ADD CONSTRAINT fk_follows_users FOREIGN KEY (follower_id) REFERENCES users (id);
ALTER TABLE blog_subscriptions ADD CONSTRAINT fk_blog_subscriptions_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE blog_subscriptions ADD CONSTRAINT fk_blog_subscriptions_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE user_blocks ADD CONSTRAINT fk_user_blocks_users FOREIGN KEY (blocker_id) REFERENCES users (id);
ALTER TABLE reports ADD CONSTRAINT fk_reports_users FOREIGN KEY (reporter_id) REFERENCES users (id);
ALTER TABLE reports ADD CONSTRAINT fk_reports_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_users FOREIGN KEY (recipient_id) REFERENCES users (id);
ALTER TABLE notification_settings ADD CONSTRAINT fk_notification_settings_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE blog_closing_notices ADD CONSTRAINT fk_blog_closing_notices_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blog_closing_notices ADD CONSTRAINT fk_blog_closing_notices_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE categories ADD CONSTRAINT fk_categories_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE posts ADD CONSTRAINT fk_posts_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE posts ADD CONSTRAINT fk_posts_users FOREIGN KEY (author_id) REFERENCES users (id);
ALTER TABLE posts ADD CONSTRAINT fk_posts_categories FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL;
ALTER TABLE post_tags ADD CONSTRAINT fk_post_tags_tags FOREIGN KEY (tag_id) REFERENCES tags (id);
ALTER TABLE post_tags ADD CONSTRAINT fk_post_tags_posts FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
ALTER TABLE images ADD CONSTRAINT fk_images_users FOREIGN KEY (uploader_id) REFERENCES users (id);
ALTER TABLE comments ADD CONSTRAINT fk_comments_posts FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
ALTER TABLE comments ADD CONSTRAINT fk_comments_users FOREIGN KEY (author_id) REFERENCES users (id);
ALTER TABLE comments ADD CONSTRAINT fk_comments_comments FOREIGN KEY (parent_id) REFERENCES comments (id);
ALTER TABLE post_likes ADD CONSTRAINT fk_post_likes_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE post_likes ADD CONSTRAINT fk_post_likes_posts FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
ALTER TABLE recent_searches ADD CONSTRAINT fk_recent_searches_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE post_views ADD CONSTRAINT fk_post_views_posts FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
ALTER TABLE admin_actions ADD CONSTRAINT fk_admin_actions_users FOREIGN KEY (admin_id) REFERENCES users (id);
ALTER TABLE admin_actions ADD CONSTRAINT fk_admin_actions_reports FOREIGN KEY (report_id) REFERENCES reports (id);
ALTER TABLE owner_warnings ADD CONSTRAINT fk_owner_warnings_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE owner_warnings ADD CONSTRAINT fk_owner_warnings_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE owner_warnings ADD CONSTRAINT fk_owner_warnings_reports FOREIGN KEY (report_id) REFERENCES reports (id);
ALTER TABLE owner_revocations ADD CONSTRAINT fk_owner_revocations_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE owner_revocations ADD CONSTRAINT fk_owner_revocations_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE social_accounts ADD CONSTRAINT fk_social_accounts_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

CREATE INDEX idx_users_name_phone ON users (name ASC, phone ASC);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at ASC);
CREATE FULLTEXT INDEX idx_blogs_name_description ON blogs (name, description) WITH PARSER ngram;
CREATE INDEX idx_blogs_status_visibility_created_at ON blogs (status ASC, visibility ASC, created_at DESC);
CREATE INDEX idx_blogs_status_visibility_member_count ON blogs (status ASC, visibility ASC, member_count DESC);
CREATE INDEX idx_blogs_close_scheduled_at ON blogs (close_scheduled_at ASC);
CREATE INDEX idx_blog_join_requests_blog_id_user_id_status ON blog_join_requests (blog_id ASC, user_id ASC, status ASC);
CREATE INDEX idx_blog_delegations_status_expires_at ON blog_delegations (status ASC, expires_at ASC);
CREATE INDEX idx_blog_sanctions_blog_id_user_id_sanction_type_starts_at ON blog_sanctions (blog_id ASC, user_id ASC, sanction_type ASC, starts_at ASC);
CREATE INDEX idx_blog_blacklists_blog_id_email_hash ON blog_blacklists (blog_id ASC, email_hash ASC);
CREATE INDEX idx_blog_blacklists_blog_id_phone_hash ON blog_blacklists (blog_id ASC, phone_hash ASC);
CREATE INDEX idx_follows_followee_id ON follows (followee_id ASC);
CREATE INDEX idx_user_blocks_blocked_id ON user_blocks (blocked_id ASC);
CREATE INDEX idx_reports_reporter_id_target_type_target_id_created_at ON reports (reporter_id ASC, target_type ASC, target_id ASC, created_at ASC);
CREATE INDEX idx_reports_route_status_created_at ON reports (route ASC, status ASC, created_at ASC);
CREATE INDEX idx_notifications_recipient_id_tab_created_at ON notifications (recipient_id ASC, tab ASC, created_at DESC);
CREATE INDEX idx_notifications_created_at ON notifications (created_at ASC);
CREATE FULLTEXT INDEX idx_posts_title ON posts (title) WITH PARSER ngram;
CREATE INDEX idx_posts_blog_id_status_created_at ON posts (blog_id ASC, status ASC, created_at DESC);
CREATE INDEX idx_posts_status_created_at ON posts (status ASC, created_at DESC);
CREATE INDEX idx_posts_status_like_count_created_at ON posts (status ASC, like_count DESC, created_at DESC);
CREATE INDEX idx_posts_deleted_at ON posts (deleted_at ASC);
CREATE INDEX idx_images_owner_type_owner_id ON images (owner_type ASC, owner_id ASC);
CREATE INDEX idx_comments_post_id_created_at ON comments (post_id ASC, created_at ASC);
CREATE INDEX idx_recent_searches_user_id_searched_at ON recent_searches (user_id ASC, searched_at DESC);
CREATE INDEX idx_admin_actions_created_at ON admin_actions (created_at DESC);
CREATE INDEX idx_admin_actions_target_type_target_id ON admin_actions (target_type ASC, target_id ASC);
CREATE INDEX idx_owner_warnings_blog_id_created_at ON owner_warnings (blog_id ASC, created_at ASC);
CREATE INDEX idx_login_failures_last_failed_at ON login_failures (last_failed_at ASC);
CREATE INDEX idx_system_inquiries_status_created_at ON system_inquiries (status ASC, created_at ASC);
CREATE INDEX idx_system_inquiries_user_id ON system_inquiries (user_id ASC);
CREATE INDEX idx_social_pendings_expires_at ON social_pendings (expires_at ASC);
