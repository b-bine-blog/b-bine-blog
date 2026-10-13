// 메인 위쪽 띠 오른쪽의 관리 블로그 (2026-10-13 사용자 요청, BLG-06, D-117). + 버튼은 블로그 만들기, 블로그장 블로그에는 삭제(폐쇄) 링크.
// 로그인하지 않았거나 운영·참여하는 블로그가 없으면 아무것도 보여 주지 않는다. 글자는 textContent로만 넣는다(D-64).
(function () {
  'use strict';

  var MAX_ITEMS = 3;
  var box = document.getElementById('hero-my-blogs');
  if (!box) { return; }

  var ROLE_LABEL = { OWNER: '블로그장', MANAGER: '부블로그장', MEMBER: '멤버' };

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) { node.className = className; }
    if (text != null) { node.textContent = text; }
    return node;
  }

  function renderItem(blog) {
    var row = el('div', 'hero-mine__row');
    var a = el('a', 'hero-mine__item');
    a.href = '/blog/' + encodeURIComponent(blog.address);
    a.appendChild(el('span', 'hero-mine__initial', Array.from(blog.name || '?')[0]));
    var text = el('span', 'hero-mine__text');
    text.appendChild(el('span', 'hero-mine__name', blog.name));
    text.appendChild(el('span', 'hero-mine__role', ROLE_LABEL[blog.role] || ''));
    a.appendChild(text);
    row.appendChild(a);
    // 삭제는 블로그장만. 바로 지우지 않고 운영 화면의 위임·폐쇄 탭으로 보낸다(7일 뒤 폐쇄, 그 전 철회 가능 — FR-066~FR-078)
    if (blog.role === 'OWNER' && blog.status === 'ACTIVE') {
      var del = el('a', 'hero-mine__delete', '삭제');
      del.href = '/blog/' + encodeURIComponent(blog.address) + '/manage?tab=owner';
      del.setAttribute('aria-label', blog.name + ' 블로그 삭제(폐쇄)');
      row.appendChild(del);
    }
    return row;
  }

  // 로그인하지 않았으면 401이 오고, 빈 곳으로 둔다
  api.get('/api/me/blogs').then(function (res) {
    // 운영·참여 구분 없이 블로그가 만들어진 순서로 보여 준다(id가 만든 순서, 2026-10-13 사용자 요청)
    var blogs = (res.owned || []).concat(res.joined || []).sort(function (a, b) { return a.id - b.id; });
    if (!blogs.length) { return; }
    var head = el('div', 'hero-mine__head');
    // 제목을 누르면 내가 처음 만든 블로그로, 만든 블로그가 없으면 내 정보의 내 블로그로 간다
    var title = el('a', 'hero-mine__title', '관리 블로그');
    title.href = res.owned && res.owned.length
      ? '/blog/' + encodeURIComponent(res.owned[0].address) : '/me/index.html#blogs';
    head.appendChild(title);
    var tools = el('span', 'hero-mine__tools');
    if (blogs.length > MAX_ITEMS) {
      var all = el('a', 'hero-mine__all', '전체 ' + blogs.length + '개');
      all.href = '/me/index.html#blogs';
      tools.appendChild(all);
    }
    // + 버튼: 블로그 만들기. 만들 수 있는 개수는 서버가 확인한다(D-68)
    var add = el('a', 'hero-mine__add', '+');
    add.href = '/blogs/new.html';
    add.title = '블로그 만들기';
    add.setAttribute('aria-label', '블로그 만들기');
    tools.appendChild(add);
    head.appendChild(tools);
    box.appendChild(head);
    blogs.slice(0, MAX_ITEMS).forEach(function (b) { box.appendChild(renderItem(b)); });
    box.hidden = false;
  }, function () { /* 로그인 전이면 빈 곳으로 둔다 */ });
})();
