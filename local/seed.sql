-- 내 컴퓨터에서 써 보기용 테스트 계정과 블로그. 운영 DB에는 넣지 않는다.
-- 비밀번호는 모두 Passw0rd! (bcrypt, D-81)
SET NAMES utf8mb4;

INSERT INTO users (id, email, password_hash, name, nickname, phone, role, status, created_at) VALUES
  (1, 'owner@local.test',  '$2a$10$lRMtjv9txzEl5fCCyr.GruLXMBtCtJ5gze9mC8FzX3GEUXPz89sbu', '김블장', '블로그장',   '01011112222', 'USER', 'ACTIVE', NOW()),
  (2, 'member@local.test', '$2a$10$lRMtjv9txzEl5fCCyr.GruLXMBtCtJ5gze9mC8FzX3GEUXPz89sbu', '이멤버', '멤버하나',   '01033334444', 'USER', 'ACTIVE', NOW()),
  (3, 'newbie@local.test', '$2a$10$lRMtjv9txzEl5fCCyr.GruLXMBtCtJ5gze9mC8FzX3GEUXPz89sbu', '박새내', '새로온회원', '01055556666', 'USER', 'ACTIVE', NOW()),
  (4, 'admin@local.test',  '$2a$10$lRMtjv9txzEl5fCCyr.GruLXMBtCtJ5gze9mC8FzX3GEUXPz89sbu', '최관리', '관리자계정', '01077778888', 'ADMIN', 'ACTIVE', NOW());

-- 블로그장의 공개·승인제 블로그(멤버 1명, 참여 신청 1건)와 멤버의 자유 참여 블로그
INSERT INTO blogs (id, name, address, description, visibility, join_policy, member_count) VALUES
  (1, '여행 기록장', 'travel-log', '전국을 다니며 남기는 여행 기록입니다.', 'PUBLIC', 'APPROVAL', 2),
  (2, '하루 한 줄', 'daily-note', '누구나 들어와 하루를 한 줄로 남기는 블로그', 'PUBLIC', 'FREE', 1);

INSERT INTO blog_members (user_id, blog_id, role, joined_at) VALUES
  (1, 1, 'OWNER', NOW()),
  (2, 1, 'MEMBER', NOW()),
  (2, 2, 'OWNER', NOW());

INSERT INTO blog_join_requests (user_id, blog_id, status, requested_at) VALUES (3, 1, 'PENDING', NOW());

INSERT INTO tags (id, name) VALUES (1, '여행'), (2, '사진'), (3, '일상');
INSERT INTO blog_tags (blog_id, tag_id) VALUES (1, 1), (1, 2), (2, 3);
