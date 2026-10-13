// 메인 위쪽 띠 오른쪽의 관리 블로그 (2026-10-13 사용자 요청, BLG-06, D-117). − 버튼으로 이 칸을 빼고, + 버튼으로 다시 넣는다(블로그 자체를 만들거나 지우는 버튼이 아니다).
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
    return row;
  }

  // 상자를 빼 두었는지는 이 브라우저에만 기억한다(저장이 안 되면 늘 보인다)
  var KEY = 'mb.heroMine';
  function isRemoved() { try { return localStorage.getItem(KEY) === 'off'; } catch (e) { return false; } }
  function remember(removed) {
    try { if (removed) { localStorage.setItem(KEY, 'off'); } else { localStorage.removeItem(KEY); } } catch (e) { /* 이번 화면에만 */ }
  }

  function iconButton(cls, text, label, onClick) {
    var b = el('button', cls, text);
    b.type = 'button';
    b.title = label;
    b.setAttribute('aria-label', label);
    b.addEventListener('click', onClick);
    return b;
  }

  function render(res, blogs) {
    box.textContent = '';
    box.classList.toggle('is-removed', isRemoved());
    if (isRemoved()) {
      // 뺀 상태: 다시 넣는 + 버튼만 둔다
      box.appendChild(iconButton('hero-mine__restore', '+ 관리 블로그', '관리 블로그 칸 다시 보기', function () {
        remember(false); render(res, blogs);
      }));
      return;
    }
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
    // − 버튼: 이 칸을 뺀다(블로그를 지우는 것이 아니다)
    tools.appendChild(iconButton('hero-mine__remove', '−', '관리 블로그 칸 빼기', function () {
      remember(true); render(res, blogs);
    }));
    head.appendChild(tools);
    box.appendChild(head);
    blogs.slice(0, MAX_ITEMS).forEach(function (b) { box.appendChild(renderItem(b)); });
  }

  // 로그인하지 않았으면 401이 오고, 빈 곳으로 둔다
  api.get('/api/me/blogs').then(function (res) {
    // 운영·참여 구분 없이 블로그가 만들어진 순서로 보여 준다(id가 만든 순서, 2026-10-13 사용자 요청)
    var blogs = (res.owned || []).concat(res.joined || []).sort(function (a, b) { return a.id - b.id; });
    if (!blogs.length) { return; } // 블로그가 없으면 칸도, + 버튼도 보이지 않는다
    render(res, blogs);
    box.hidden = false;
  }, function () { /* 로그인 전이면 빈 곳으로 둔다 */ });
})();
