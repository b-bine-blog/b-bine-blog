// 메인 위쪽 띠 오른쪽의 내 블로그 (2026-10-13 사용자 요청, BLG-06).
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
    var a = el('a', 'hero-mine__item');
    a.href = '/blog/' + encodeURIComponent(blog.address);
    a.appendChild(el('span', 'hero-mine__initial', Array.from(blog.name || '?')[0]));
    var text = el('span', 'hero-mine__text');
    text.appendChild(el('span', 'hero-mine__name', blog.name));
    text.appendChild(el('span', 'hero-mine__role', ROLE_LABEL[blog.role] || ''));
    a.appendChild(text);
    return a;
  }

  // 로그인하지 않았으면 401이 오고, 빈 곳으로 둔다
  api.get('/api/me/blogs').then(function (res) {
    var blogs = (res.owned || []).concat(res.joined || []);
    if (!blogs.length) { return; }
    var head = el('div', 'hero-mine__head');
    head.appendChild(el('span', 'hero-mine__title', '내 블로그'));
    if (blogs.length > MAX_ITEMS) {
      var all = el('a', 'hero-mine__all', '전체 ' + blogs.length + '개');
      all.href = '/me/blogs.html';
      head.appendChild(all);
    }
    box.appendChild(head);
    blogs.slice(0, MAX_ITEMS).forEach(function (b) { box.appendChild(renderItem(b)); });
    box.hidden = false;
  }, function () { /* 로그인 전이면 빈 곳으로 둔다 */ });
})();
