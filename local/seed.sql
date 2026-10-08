-- 내 컴퓨터에서 써 보기용 테스트 계정과 블로그. 운영 DB에는 넣지 않는다.
-- 2026-10-08 Crowfoot DB(cf_u33_d1)에 insert_sample_data로 이미 넣었다(번호 101부터, 기존 샘플과 겹치지 않게).
-- 새 MySQL에 직접 넣을 때만 이 파일을 쓴다.
-- 비밀번호는 모두 Passw0rd! (bcrypt, D-81)
SET NAMES utf8mb4;

INSERT INTO users (id, email, password_hash, name, nickname, phone, role, status, created_at) VALUES
  (101, 'owner@local.test',  '$2a$10$lRMtjv9txzEl5fCCyr.GruLXMBtCtJ5gze9mC8FzX3GEUXPz89sbu', '김블장', '블로그장',   '01011112222', 'USER', 'ACTIVE', NOW()),
  (102, 'member@local.test', '$2a$10$lRMtjv9txzEl5fCCyr.GruLXMBtCtJ5gze9mC8FzX3GEUXPz89sbu', '이멤버', '멤버하나',   '01033334444', 'USER', 'ACTIVE', NOW()),
  (103, 'newbie@local.test', '$2a$10$lRMtjv9txzEl5fCCyr.GruLXMBtCtJ5gze9mC8FzX3GEUXPz89sbu', '박새내', '새로온회원', '01055556666', 'USER', 'ACTIVE', NOW()),
  (104, 'admin@local.test',  '$2a$10$lRMtjv9txzEl5fCCyr.GruLXMBtCtJ5gze9mC8FzX3GEUXPz89sbu', '최관리', '관리자계정', '01077778888', 'ADMIN', 'ACTIVE', NOW());

-- 블로그장의 공개·승인제 블로그(멤버 1명, 참여 신청 1건)와 멤버의 자유 참여 블로그
INSERT INTO blogs (id, name, address, description, visibility, join_policy, member_count) VALUES
  (101, '여행 기록장', 'travel-log', '전국을 다니며 남기는 여행 기록입니다.', 'PUBLIC', 'APPROVAL', 2),
  (102, '하루 한 줄', 'daily-note', '누구나 들어와 하루를 한 줄로 남기는 블로그', 'PUBLIC', 'FREE', 1);

INSERT INTO blog_members (user_id, blog_id, role, joined_at) VALUES
  (101, 101, 'OWNER', NOW()),
  (102, 101, 'MEMBER', NOW()),
  (102, 102, 'OWNER', NOW());

INSERT INTO blog_join_requests (user_id, blog_id, status, requested_at) VALUES (103, 101, 'PENDING', NOW());

-- 블로그 태그는 Crowfoot DB에 같은 이름의 태그가 이미 있어 넣지 않았다.

-- 004 게시판 예시(카테고리·글·태그·댓글·공지). Crowfoot DB에는 아직 넣지 않았다.
INSERT INTO categories (id, blog_id, name, sort_order) VALUES
  (101, 101, '국내 여행', 1),
  (102, 101, '맛집', 2);

INSERT INTO posts (id, blog_id, author_id, category_id, board_type, title, content, like_count, created_at) VALUES
  (101, 101, 101, 101, 'BLOG', '제주 3박 4일 코스', '## 첫째 날\n\n공항에서 바로 **협재 해변**으로 갔습니다.\n\n- 협재 해변\n- 한림 공원\n\n> 바람이 많이 불어요.', 3, NOW() - INTERVAL 2 DAY),
  (102, 101, 102, 102, 'BLOG', '부산 돼지국밥 세 곳 비교', '| 가게 | 국물 | 가격 |\n|---|---|---|\n| A | 진함 | 9,000원 |\n| B | 맑음 | 8,500원 |', 1, NOW() - INTERVAL 1 DAY),
  (103, 101, 101, NULL, 'BLOG', '여행 준비물 목록', '- [x] 충전기\n- [ ] 우산\n\n~~캐리어~~ 배낭 하나면 충분합니다.', 0, NOW() - INTERVAL 3 HOUR),
  (104, 102, 102, NULL, 'BLOG', '오늘의 한 줄', '날씨가 맑아서 산책을 했다.', 0, NOW() - INTERVAL 1 HOUR),
  (105, NULL, 104, NULL, 'NOTICE', '메인블로그를 열었습니다', '함께 쓰는 블로그 **메인블로그**에 오신 것을 환영합니다.', 0, NOW() - INTERVAL 5 DAY);

INSERT INTO tags (name) VALUES ('제주'), ('부산'), ('맛집'), ('준비물')
  ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO post_tags (post_id, tag_id)
  SELECT 101, id FROM tags WHERE name IN ('제주')
  UNION ALL SELECT 102, id FROM tags WHERE name IN ('부산', '맛집')
  UNION ALL SELECT 103, id FROM tags WHERE name IN ('준비물');

INSERT INTO comments (id, parent_id, author_id, post_id, reply_to_user_id, content, created_at) VALUES
  (101, NULL, 102, 101, NULL, '협재 해변 정말 예쁘죠!', NOW() - INTERVAL 1 DAY),
  (102, 101, 101, 101, 102, '네, 다음엔 같이 가요.', NOW() - INTERVAL 20 HOUR),
  (103, NULL, 101, 102, NULL, 'B 가게 가 봐야겠네요.', NOW() - INTERVAL 10 HOUR);

INSERT INTO post_likes (post_id, user_id) VALUES (101, 102), (101, 103), (102, 101);
