-- ShedLock 잠금 테이블 (research R-13, REQ-005).
-- 업무 테이블이 아니라 Crowfoot ERD에는 넣지 않는다. 운영·개발 DB에는 이 파일로 따로 만든다.
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
