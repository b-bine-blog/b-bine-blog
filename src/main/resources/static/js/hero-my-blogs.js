// 메인 위쪽 띠 오른쪽의 관리 블로그 (2026-10-13 사용자 요청, BLG-06, D-117).
// 내가 운영(블로그장)하거나 관리(부블로그장)하는 블로그 가운데 고른 것을 최대 MAX_ITEMS개 보여 준다.
// + 버튼을 누르면 고치는 상태가 된다: 각 줄에 "삭제"(그 블로그를 이 칸에서만 뺀다, 블로그는 그대로)가 나오고,
// 칸이 남아 있으면 운영·관리하는 블로그 목록이 펼쳐져 하나를 골라 넣는다. 평소에는 삭제 버튼을 숨긴다(2026-10-13 사용자 요청, D-119).
// 고른 목록은 이 브라우저에만 기억한다. 고른 적이 없으면 만든 순서로 앞의 MAX_ITEMS개를 보여 준다.
// 로그인하지 않았거나 운영·관리하는 블로그가 없으면 아무것도 보여 주지 않는다. 글자는 textContent로만 넣는다(D-64).
(function () {
  'use strict';

  var MAX_ITEMS = 3;
  var KEY = 'mb.heroMine.pinned';
  var box = document.getElementById('hero-my-blogs');
  if (!box) { return; }

  var ROLE_LABEL = { OWNER: '블로그장', MANAGER: '부블로그장' };

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) { node.className = className; }
    if (text != null) { node.textContent = text; }
    return node;
  }

  function button(cls, text, label, onClick) {
    var b = el('button', cls, text);
    b.type = 'button';
    if (label) { b.title = label; b.setAttribute('aria-label', label); }
    b.addEventListener('click', onClick);
    return b;
  }

  function loadPinned() {
    try {
      var v = JSON.parse(localStorage.getItem(KEY));
      return Array.isArray(v) ? v.filter(function (id) { return typeof id === 'number'; }) : null;
    } catch (e) { return null; }
  }
  function savePinned(ids) {
    try { localStorage.setItem(KEY, JSON.stringify(ids)); } catch (e) { /* 이번 화면에만 */ }
  }

  function blogLink(blog, cls) {
    var a = el('a', cls);
    a.href = '/blog/' + encodeURIComponent(blog.address);
    a.appendChild(el('span', 'hero-mine__initial', Array.from(blog.name || '?')[0]));
    var text = el('span', 'hero-mine__text');
    text.appendChild(el('span', 'hero-mine__name', blog.name));
    text.appendChild(el('span', 'hero-mine__role', ROLE_LABEL[blog.role] || ''));
    a.appendChild(text);
    return a;
  }

  function start(blogs) {
    var byId = {};
    blogs.forEach(function (b) { byId[b.id] = b; });
    var stored = loadPinned();
    // 고른 목록 가운데 지금도 운영·관리하는 블로그만 남긴다
    var pinned = stored ? stored.filter(function (id) { return byId[id]; }).slice(0, MAX_ITEMS)
      : blogs.slice(0, MAX_ITEMS).map(function (b) { return b.id; });
    var editing = false; // + 를 눌러 고치는 중(삭제 버튼과 고르는 목록이 보인다)
    var firstOwned = blogs.filter(function (b) { return b.role === 'OWNER'; })[0];

    function set(ids) { pinned = ids; savePinned(ids); render(); }

    function render() {
      box.textContent = '';
      var head = el('div', 'hero-mine__head');
      var title = el('a', 'hero-mine__title', '관리 블로그');
      // 제목을 누르면 내가 처음 만든 블로그로, 만든 블로그가 없으면 내 정보의 내 블로그로 간다
      title.href = firstOwned ? '/blog/' + encodeURIComponent(firstOwned.address) : '/me/index.html#blogs';
      head.appendChild(title);
      var full = pinned.length >= MAX_ITEMS;
      var add = button('hero-mine__add', '+', editing ? '고치기 끝내기' : '블로그 넣고 빼기', function (e) {
        e.stopPropagation();
        editing = !editing;
        render();
      });
      add.setAttribute('aria-expanded', String(editing));
      box.classList.toggle('is-editing', editing);
      head.appendChild(add);
      box.appendChild(head);

      if (editing && full) {
        box.appendChild(el('p', 'hero-mine__empty', '최대 ' + MAX_ITEMS + '개까지예요. 삭제로 빼고 넣으세요.'));
      }
      if (editing && !full) {
        var picker = el('div', 'hero-mine__picker');
        picker.setAttribute('role', 'listbox');
        picker.setAttribute('aria-label', '넣을 블로그 고르기');
        // 고르는 목록은 최신순(나중에 만든 블로그가 위, 2026-10-13 사용자 요청)
        var choices = blogs.filter(function (b) { return pinned.indexOf(b.id) < 0; }).reverse();
        if (!choices.length) { picker.appendChild(el('p', 'hero-mine__empty', '더 넣을 블로그가 없어요.')); }
        choices.forEach(function (b) {
          var opt = button('hero-mine__choice', null, null, function (e) {
            e.stopPropagation(); // 넣은 뒤에도 고치는 상태를 그대로 둔다
            set(pinned.concat(b.id));
          });
          opt.setAttribute('role', 'option');
          opt.appendChild(el('span', 'hero-mine__initial', Array.from(b.name || '?')[0]));
          opt.appendChild(el('span', 'hero-mine__name', b.name));
          opt.appendChild(el('span', 'hero-mine__role', ROLE_LABEL[b.role] || ''));
          picker.appendChild(opt);
        });
        box.appendChild(picker);
      }

      if (!pinned.length) {
        box.appendChild(el('p', 'hero-mine__empty', '+를 눌러 블로그를 골라 넣으세요.'));
      }
      pinned.forEach(function (id) {
        var blog = byId[id];
        var row = el('div', 'hero-mine__row');
        row.appendChild(blogLink(blog, 'hero-mine__item'));
        if (editing) {
          row.appendChild(button('hero-mine__delete', '삭제', blog.name + ' 칸에서 빼기', function (e) {
            e.stopPropagation(); // 다시 그려져 사라진 버튼을 바깥 클릭으로 보지 않게
            set(pinned.filter(function (x) { return x !== id; }));
          }));
        }
        box.appendChild(row);
      });
    }

    document.addEventListener('click', function (e) {
      if (editing && !box.contains(e.target)) { editing = false; render(); }
    });
    document.addEventListener('keydown', function (e) {
      if (editing && e.key === 'Escape') { editing = false; render(); }
    });
    render();
    box.hidden = false;
  }

  // 로그인하지 않았으면 401이 오고, 빈 곳으로 둔다
  api.get('/api/me/blogs').then(function (res) {
    // 운영(블로그장)·관리(부블로그장)하는 블로그만, 만든 순서로(id가 만든 순서)
    var blogs = (res.owned || []).concat((res.joined || []).filter(function (b) { return b.role === 'MANAGER'; }))
      .sort(function (a, b) { return a.id - b.id; });
    if (!blogs.length) { return; } // 운영·관리하는 블로그가 없으면 칸도 + 버튼도 보이지 않는다
    start(blogs);
  }, function () { /* 로그인 전이면 빈 곳으로 둔다 */ });
})();
