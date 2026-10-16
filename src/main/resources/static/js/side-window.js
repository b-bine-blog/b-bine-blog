// 우리 사이트 모든 화면 위에 떠 있는 내 창 (D-139). layout.js가 머리 메뉴를 그린 뒤 불러온다. 제목 줄(−)을 끌어 옮기고, 누르면 접혀 프로필만 남고, 프로필을 누르면 다시 펼친다.
// 위치와 접힘 상태는 이 브라우저에만 기억한다(localStorage). 글자는 모두 textContent로 넣는다(D-64).
(function () {
  'use strict';

  var KEY = 'sideWindow';
  // 어느 화면에서나 뜨도록 board-common.js 없이 쓴다
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) { e.className = cls; }
    if (text != null) { e.textContent = text; }
    return e;
  }
  var key = KEY;  // 비회원과 회원마다 따로 기억한다. 계정으로 처음 들어오면 접힌 채 로고 왼쪽에서 시작
  var state = {};
  var win, mini, dock;

  function load(suffix) {
    key = KEY + ':' + suffix;
    try { state = JSON.parse(localStorage.getItem(key)) || {}; } catch (e) { state = {}; }
  }

  function save() {
    try { localStorage.setItem(key, JSON.stringify(state)); } catch (e) { /* 저장 못 해도 창은 그대로 동작 */ }
  }

  // 프로필 자리: 머리 메뉴 로고 왼쪽에 칸을 하나 잡아 둔다(2026-10-16 사용자 요청).
  // 칸이 메뉴 안에 있으니 창이 좁아도 다른 메뉴에 밀리거나 겹치지 않는다. 접힌 프로필은 처음에 이 칸에 들어 있다
  function makeDock() {
    var logo = document.querySelector('.site-logo');
    if (dock || !logo) { return; }
    dock = el('span', 'site-header__dock');
    dock.setAttribute('aria-hidden', 'true');
    logo.parentNode.insertBefore(dock, logo);
    makeEditor();
  }

  function docked() { return !!dock && state.docked !== false; }

  // 머리 메뉴 버튼 옮기기(2026-10-16 사용자 요청). 닉네임 메뉴의 "내 정보" 아래 "편집"을 누르면 편집 모드가 되고,
  // 프로필 칸 옆에 "기본으로"·"완료"가 생긴다. 편집 중에는 밝기·알림·닉네임(비회원은 로그인·회원가입) 버튼을 끌어 옮긴다.
  // 옮긴 자리는 프로필 자리와 함께 계정마다 기억한다
  var MOVABLE = [['theme', '.site-header__theme'], ['bell', '.site-header__bell'], ['menu', '.profile-menu'],
    ['login', 'a[href="/login.html"]'], ['signup', 'a[href="/signup.html"]']];
  var editing = false, editBar;

  function movables() {
    var right = document.querySelector('.site-header__right');
    if (!right) { return []; }
    var out = [];
    MOVABLE.forEach(function (m) {
      var n = right.querySelector(':scope > ' + m[1]);
      if (n) { n.setAttribute('data-move-id', m[0]); out.push(n); }
    });
    return out;
  }

  function floatAt(node, x, y) {
    node.classList.add('is-floating');
    node.style.position = 'fixed';
    var p = place(node, x, y);
    // 화면 왼쪽에 두면 닉네임 메뉴가 오른쪽으로 펼쳐지게
    node.classList.toggle('is-left', p.x + node.offsetWidth / 2 < window.innerWidth / 2);
    return p;
  }

  function unfloat(node) {
    node.classList.remove('is-floating', 'is-left');
    node.style.position = node.style.left = node.style.top = '';
  }

  function applyLayout() {
    var saved = state.layout || {};
    movables().forEach(function (n) {
      var at = saved[n.getAttribute('data-move-id')];
      if (at) { floatAt(n, at.x, at.y); } else { unfloat(n); }
    });
  }

  function setEditing(on) {
    editing = on;
    document.body.classList.toggle('layout-editing', on);
    editBar.hidden = !on;
    homeEditing(on);
  }

  // 메인 화면 칸 편집(2026-10-16 사용자 요청, 구성안). 편집 중에는 "블로그 둘러보기"·"글" 칸에 삭제 버튼이 붙고,
  // 맨 아래 "추가"로 지운 칸을 되살리거나 URL을 넣어 그 사이트를 칸으로 띄운다. 계정마다 브라우저에 기억한다
  var URL_LIMIT = 5;
  var BUILT_IN = [['blogs', '.blog-showcase', '블로그 둘러보기'], ['feed', '#feed', '글']];
  var home, addBox;

  function homeState() {
    state.home = state.home || { hidden: [], urls: [] };
    return state.home;
  }

  function deleteButton(onClick) {
    var b = el('button', 'widget-delete', '삭제');
    b.type = 'button';
    b.addEventListener('click', onClick);
    return b;
  }

  function urlWidget(url) {
    var u = new URL(url);
    var sec = el('section', 'panel url-widget');
    sec.setAttribute('data-widget', 'url');
    var head = el('div', 'panel__head');
    head.appendChild(el('h2', null, u.hostname));
    var open = el('a', 'url-widget__open', '새 탭으로 열기');
    open.href = u.href;
    open.target = '_blank';
    open.rel = 'noopener noreferrer';
    head.appendChild(open);
    sec.appendChild(head);
    var frame = el('iframe', 'url-widget__frame');
    frame.src = u.href;
    frame.title = u.hostname;
    frame.loading = 'lazy';
    frame.referrerPolicy = 'no-referrer';
    frame.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-popups allow-forms');
    sec.appendChild(frame);
    sec.appendChild(el('p', 'url-widget__note', '사이트가 다른 곳에 띄우는 걸 막아 두면 비어 보여요. 그때는 "새 탭으로 열기"를 눌러 주세요.'));
    sec.appendChild(deleteButton(function () {
      var h = homeState();
      h.urls.splice(h.urls.indexOf(url), 1);
      sec.remove();
      save();
      refreshAdd();
    }));
    return sec;
  }

  function renderHome() {
    var h = homeState();
    BUILT_IN.forEach(function (w) {
      var sec = home.querySelector(w[1]);
      if (sec) { sec.hidden = h.hidden.indexOf(w[0]) >= 0; }
    });
    Array.prototype.forEach.call(home.querySelectorAll('.url-widget'), function (n) { n.remove(); });
    h.urls.forEach(function (url) { home.insertBefore(urlWidget(url), addBox); });
  }

  function refreshAdd() {
    var h = homeState();
    Array.prototype.forEach.call(addBox.querySelectorAll('[data-add]'), function (b) {
      b.disabled = h.hidden.indexOf(b.getAttribute('data-add')) < 0;
    });
    addBox.querySelector('.widget-add__url').disabled = h.urls.length >= URL_LIMIT;
  }

  function makeHome() {
    home = document.querySelector('main.home');
    if (!home || addBox) { return; }
    BUILT_IN.forEach(function (w) {
      var sec = home.querySelector(w[1]);
      if (!sec) { return; }
      sec.setAttribute('data-widget', w[0]);
      sec.appendChild(deleteButton(function () {
        homeState().hidden.push(w[0]);
        sec.hidden = true;
        save();
        refreshAdd();
      }));
    });

    addBox = el('section', 'panel widget-add');
    addBox.hidden = true;
    var toggle = el('button', 'widget-add__toggle', '+ 추가');
    toggle.type = 'button';
    var menu = el('div', 'widget-add__menu');
    menu.hidden = true;
    toggle.addEventListener('click', function () { menu.hidden = !menu.hidden; });
    BUILT_IN.forEach(function (w) {
      var b = el('button', null, w[2]);
      b.type = 'button';
      b.setAttribute('data-add', w[0]);
      b.title = '지운 칸만 다시 넣을 수 있어요';
      b.addEventListener('click', function () {
        var h = homeState();
        h.hidden.splice(h.hidden.indexOf(w[0]), 1);
        renderHome();
        save();
        refreshAdd();
        menu.hidden = true;
      });
      menu.appendChild(b);
    });
    var form = el('form', 'widget-add__form');
    var input = el('input');
    input.type = 'url';
    input.placeholder = 'https://';
    input.setAttribute('aria-label', '띄울 사이트 주소');
    var go = el('button', 'widget-add__url', 'URL 추가');
    go.type = 'submit';
    var msg = el('span', 'widget-add__msg');
    form.appendChild(input);
    form.appendChild(go);
    form.appendChild(msg);
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var u;
      try { u = new URL(input.value.trim()); } catch (err) { u = null; }
      if (!u || (u.protocol !== 'https:' && u.protocol !== 'http:')) { msg.textContent = 'http나 https로 시작하는 주소를 넣어 주세요'; return; }
      homeState().urls.push(u.href);
      save();
      renderHome();
      refreshAdd();
      input.value = '';
      msg.textContent = '';
      menu.hidden = true;
    });
    menu.appendChild(form);
    addBox.appendChild(toggle);
    addBox.appendChild(menu);
    home.appendChild(addBox);
    renderHome();
    refreshAdd();
  }

  function homeEditing(on) {
    if (!home) { return; }
    home.classList.toggle('is-editing', on);
    addBox.hidden = !on;
  }

  // 기본으로: 머리 메뉴 버튼은 제자리로, 프로필은 로고 왼쪽 칸으로, 메인 화면 칸은 처음 두 칸으로
  function resetLayout() {
    state.layout = {};
    applyLayout();
    if (home) { state.home = { hidden: [], urls: [] }; renderHome(); refreshAdd(); }
    state.x = state.y = null;
    if (mini) {
      if (win && !win.hidden) { show(true); }
      setDocked(true);
    }
    state.docked = true;
    save();
  }

  function movableDrag(node) {
    var start = null;
    node.addEventListener('pointerdown', function (e) {
      if (!editing || e.button !== 0) { return; }
      var r = node.getBoundingClientRect();
      start = { px: e.clientX, py: e.clientY, x: r.left, y: r.top, moved: false };
      node.setPointerCapture(e.pointerId);
    });
    node.addEventListener('pointermove', function (e) {
      if (!start) { return; }
      var dx = e.clientX - start.px, dy = e.clientY - start.py;
      if (!start.moved && Math.abs(dx) + Math.abs(dy) < 5) { return; }
      start.moved = true;
      floatAt(node, start.x + dx, start.y + dy);
    });
    node.addEventListener('pointerup', function () {
      if (!start) { return; }
      if (start.moved) {
        state.layout = state.layout || {};
        state.layout[node.getAttribute('data-move-id')] = { x: node.offsetLeft, y: node.offsetTop };
        save();
      }
      start = null;
    });
    node.addEventListener('pointercancel', function () { start = null; });
  }

  function makeEditor() {
    var list = document.getElementById('profile-menu-list');
    if (!dock || editBar || !list) { return; }  // 닉네임 메뉴가 있는 회원만 편집한다
    var item = el('button', null, '편집');
    item.type = 'button';
    item.addEventListener('click', function () {
      list.hidden = true;
      var toggle = document.querySelector('.site-header__nickname');
      if (toggle) { toggle.setAttribute('aria-expanded', 'false'); }
      setEditing(true);
    });
    var first = list.firstElementChild;  // "내 정보" 바로 아래
    list.insertBefore(item, first ? first.nextSibling : null);

    editBar = el('span', 'site-header__editbar');
    editBar.hidden = true;
    var reset = el('button', 'site-header__reset', '기본으로');
    reset.type = 'button';
    reset.title = '버튼과 프로필을 처음 자리로';
    reset.addEventListener('click', resetLayout);
    var done = el('button', 'site-header__done', '완료');
    done.type = 'button';
    done.addEventListener('click', function () { setEditing(false); });
    editBar.appendChild(reset);
    editBar.appendChild(done);
    dock.parentNode.insertBefore(editBar, dock.nextSibling);
    makeHome();

    movables().forEach(movableDrag);
    applyLayout();
    // 편집 중에는 버튼을 눌러도 메뉴·알림이 열리지 않고 옮기기만 한다
    document.addEventListener('click', function (e) {
      if (editing && e.target.closest('[data-move-id]')) { e.preventDefault(); e.stopPropagation(); }
    }, true);
    document.addEventListener('keydown', function (e) {
      if (editing && e.key === 'Escape') { setEditing(false); }
    });
    window.addEventListener('resize', applyLayout);
  }

  // 접힌 프로필을 칸 위에 맞춘다. 머리 메뉴가 줄바꿈되거나 창 크기가 바뀌면 다시 맞춘다
  function syncDock() {
    if (!mini || mini.hidden || !docked()) { return; }
    var r = dock.getBoundingClientRect();
    mini.style.left = r.left + 'px';
    mini.style.top = r.top + 'px';
  }

  function setDocked(on) {
    state.docked = on;
    mini.classList.toggle('is-docked', on);
    if (dock) { dock.classList.toggle('is-empty', !on || mini.hidden); }
    if (on) { syncDock(); }
  }

  // 끌어 온 프로필이 칸에 닿으면 다시 칸에 넣는다
  function overDock(box) {
    if (!dock) { return false; }
    var a = box.getBoundingClientRect(), b = dock.getBoundingClientRect();
    return a.left < b.right + 12 && a.right > b.left - 12 && a.top < b.bottom + 12 && a.bottom > b.top - 12;
  }

  function avatar(profile, cls) {
    var box = el('span', 'side-window__avatar ' + (cls || ''));
    if (profile.profileImageUrl) {
      var img = el('img');
      img.src = profile.profileImageUrl;
      img.alt = '';
      box.appendChild(img);
    } else {
      box.textContent = (profile.nickname || '?').charAt(0);
    }
    return box;
  }

  function row(label, value, onClick) {
    var b = el('button', 'side-window__row');
    b.type = 'button';
    b.appendChild(el('span', null, label));
    b.appendChild(el('span', 'side-window__num', String(value)));
    b.addEventListener('click', onClick);
    return b;
  }

  function section(title) {
    var s = el('div', 'side-window__sec');
    if (title) { s.appendChild(el('div', 'side-window__label', title)); }
    return s;
  }

  function openActivity(hash) {
    var href = '/me/activity.html' + hash;
    if (window.siteLayout) { window.siteLayout.openActivity(href); } else { location.href = href; }
  }

  // 화면 밖으로 나가지 않게 가장자리에서 멈춘다
  function place(box, x, y) {
    var maxX = Math.max(0, window.innerWidth - box.offsetWidth);
    var maxY = Math.max(0, window.innerHeight - box.offsetHeight);
    x = Math.min(Math.max(0, x), maxX);
    y = Math.min(Math.max(0, y), maxY);
    box.style.left = x + 'px';
    box.style.top = y + 'px';
    return { x: x, y: y };
  }

  // 끌면 옮기고, 거의 안 움직이고 떼면 onTap
  function draggable(handle, box, onTap) {
    var start = null;
    handle.addEventListener('pointerdown', function (e) {
      if (e.button !== 0) { return; }
      var r = box.getBoundingClientRect();
      start = { px: e.clientX, py: e.clientY, x: r.left, y: r.top, moved: false };
      handle.setPointerCapture(e.pointerId);
    });
    handle.addEventListener('pointermove', function (e) {
      if (!start) { return; }
      var dx = e.clientX - start.px, dy = e.clientY - start.py;
      if (!start.moved && Math.abs(dx) + Math.abs(dy) < 5) { return; }
      if (!start.moved && box === mini && docked()) { setDocked(false); }  // 칸에서 꺼낸다
      start.moved = true;
      var p = place(box, start.x + dx, start.y + dy);
      state.x = p.x;
      state.y = p.y;
    });
    handle.addEventListener('pointerup', function () {
      if (!start) { return; }
      var moved = start.moved;
      start = null;
      if (moved && box === mini && overDock(mini)) { setDocked(true); }
      if (moved) { save(); } else if (onTap) { onTap(); }
    });
    handle.addEventListener('pointercancel', function () { start = null; });
  }

  // 칸에 있던 프로필을 펼치면 창은 칸 바로 아래에 열리고, 접으면 칸으로 돌아간다
  function show(minimized) {
    state.min = minimized;
    var from = minimized ? win : mini;
    var to = minimized ? mini : win;
    var r = from.getBoundingClientRect();
    from.hidden = true;
    to.hidden = false;
    if (docked()) {
      if (minimized) { syncDock(); } else { place(win, r.left, r.bottom + 6); }
    } else {
      var p = place(to, state.x != null ? state.x : r.left, state.y != null ? state.y : r.top);
      state.x = p.x;
      state.y = p.y;
    }
    if (dock) { dock.classList.toggle('is-empty', !docked() || !minimized); }
    save();
  }

  // 창 틀(끄는 제목 줄과 −). 내용은 회원·비회원에 따라 채운다
  function frame() {
    win = el('aside', 'side-window');
    win.setAttribute('aria-label', '내 정보');
    // 제목 줄 하나가 손잡이 겸 접기 버튼이다. 끌면 옮기고, 누르면 바로 접힌다(2026-10-16 사용자 요청)
    var head = el('div', 'side-window__head');
    head.title = '끌어서 옮기기 · 눌러서 접기';
    head.setAttribute('role', 'button');
    head.setAttribute('aria-label', '접기');
    head.tabIndex = 0;
    head.appendChild(el('span', 'side-window__grip', '−'));
    head.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); show(true); }
    });
    win.appendChild(head);
    mini = el('button', 'side-window side-window--mini');
    mini.type = 'button';
    mini.setAttribute('aria-label', '내 정보 펼치기');
    return head;
  }

  function personIcon() {
    var box = el('span', 'side-window__avatar side-window__avatar--guest');
    var ns = 'http://www.w3.org/2000/svg';
    var svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('width', '20');
    svg.setAttribute('height', '20');
    svg.setAttribute('aria-hidden', 'true');
    var path = document.createElementNS(ns, 'path');
    path.setAttribute('d', 'M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9zm0 2c-4.4 0-8 2.2-8 5v2h16v-2c0-2.8-3.6-5-8-5z');
    path.setAttribute('fill', 'currentColor');
    svg.appendChild(path);
    box.appendChild(svg);
    return box;
  }

  function authButton(href, text, cls) {
    var a = el('a', 'button ' + cls, text);
    a.href = href;
    a.addEventListener('click', function (e) {
      if (!window.siteLayout || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) { return; }
      e.preventDefault();
      window.siteLayout.openAuth(href);  // 머리 메뉴처럼 지금 화면 위 창으로(D-118)
    });
    return a;
  }

  // 로그인하지 않았으면 로그인·회원가입 버튼만
  function buildGuest() {
    var head = frame();
    var s = section();
    s.appendChild(el('div', 'side-window__guest', '로그인하고 블로그를 만들거나 참여해 보세요'));
    var buttons = el('div', 'side-window__auth');
    buttons.appendChild(authButton('/login.html', '로그인', 'button-outline'));
    buttons.appendChild(authButton('/signup.html', '회원가입', ''));
    s.appendChild(buttons);
    win.appendChild(s);
    mini.title = '로그인·회원가입';
    mini.appendChild(personIcon());
    mount(head);
  }

  function build(me, profile, following, summary, unread) {
    var head = frame();

    var top = section();
    var who = el('a', 'side-window__who');
    who.href = '/profile/' + me.memberId;
    who.appendChild(avatar(profile));
    who.appendChild(el('strong', null, profile.nickname));
    top.appendChild(who);
    var follow = el('div', 'side-window__follow');
    follow.appendChild(el('span', null, '팔로워 '));
    follow.appendChild(el('span', 'side-window__num', String(profile.followerCount)));
    follow.appendChild(el('span', null, '  팔로잉 '));
    follow.appendChild(el('span', 'side-window__num', String(following)));
    top.appendChild(follow);
    win.appendChild(top);

    var blogs = (profile.ownedBlogs || []).map(function (b) { return [b, '블로그장']; })
      .concat((profile.joinedBlogs || []).map(function (b) { return [b, '멤버']; }));
    if (blogs.length) {
      var bs = section('내 블로그');
      blogs.forEach(function (pair) {
        var a = el('a', 'side-window__row');
        a.href = '/blog/' + encodeURIComponent(pair[0].address);
        a.appendChild(el('span', null, pair[0].name));
        a.appendChild(el('span', 'side-window__role', pair[1]));
        bs.appendChild(a);
      });
      win.appendChild(bs);
    }

    var act = section('내 활동');
    act.appendChild(row('작성글', summary.posts, function () { openActivity('#posts'); }));
    act.appendChild(row('작성댓글', summary.comments, function () { openActivity('#comments'); }));
    act.appendChild(row('스크랩', summary.scraps, function () { openActivity('#scraps'); }));
    act.appendChild(row('좋아요', summary.likes, function () { openActivity('#likes'); }));
    win.appendChild(act);

    var noti = section();
    var notiRow = row('안 읽은 알림', unread, function () {
      var bell = document.querySelector('.site-header__bell');
      if (bell) { bell.click(); }
    });
    noti.appendChild(notiRow);
    win.appendChild(noti);
    // 알림 수는 머리 메뉴 종 배지(30초 폴링, D-72)를 따라간다
    setInterval(function () {
      var badge = document.querySelector('.site-header__badge');
      if (badge) { notiRow.lastChild.textContent = badge.hidden ? '0' : badge.textContent; }
    }, 5000);

    mini.title = profile.nickname;
    mini.appendChild(avatar(profile));  // 접으면 프로필 사진만 남긴다
    mount(head);
  }

  function mount(head) {
    win.hidden = true;
    mini.hidden = true;
    document.body.appendChild(win);
    document.body.appendChild(mini);
    draggable(head, win, function () { show(true); });
    draggable(mini, mini, function () { show(false); });
    window.addEventListener('resize', function () {
      var box = win.hidden ? mini : win;
      if (box === mini && docked()) { syncDock(); return; }
      place(box, box.offsetLeft, box.offsetTop);
    });
    window.addEventListener('scroll', syncDock, { passive: true });
    if (window.ResizeObserver) { new ResizeObserver(syncDock).observe(document.querySelector('.site-header') || document.body); }

    // 처음에는 접힌 채로 로고 왼쪽 칸에서 시작한다
    var minimized = state.min != null ? state.min : true;
    state.min = minimized;
    var box = minimized ? mini : win;
    box.hidden = false;
    setDocked(docked());
    if (minimized && docked()) {
      syncDock();
    } else if (state.x != null) {
      place(box, state.x, state.y);
    } else {
      var r = dock ? dock.getBoundingClientRect() : { left: 8, bottom: 8 };
      place(box, r.left, r.bottom + 6);
    }
    if (dock) { dock.classList.toggle('is-empty', !docked() || !minimized); }
  }

  function count(promise, pick) {
    return promise.then(pick, function () { return 0; });
  }

  // 머리 메뉴(로고)가 그려진 뒤에 띄워야 처음 자리를 잡을 수 있다
  function whenHeader(fn) {
    var tries = 0;
    (function wait() {
      if (document.querySelector('.site-logo') || tries++ > 40) { fn(); } else { setTimeout(wait, 50); }
    })();
  }

  function guest() { load('guest'); whenHeader(function () { makeDock(); buildGuest(); }); }

  api.get('/api/auth/session').then(function (me) {
    if (!me || !me.memberId) { guest(); return; }
    if (me.admin) { return; } // 메인 관리자는 블로그 활동을 하지 않는다
    load('member:' + me.memberId);
    whenHeader(makeDock); // 내용을 불러오는 동안에도 칸부터 잡아 머리 메뉴가 나중에 밀리지 않게
    return Promise.all([
      api.get('/api/users/' + me.memberId + '/profile'),
      count(api.get('/api/users/' + me.memberId + '/following'), function (r) { return r.totalElements; }),
      api.get('/api/me/summary'),
      count(api.get('/api/notifications/unread-count'), function (r) { return r.unreadCount; })
    ]).then(function (r) { whenHeader(function () { build(me, r[0], r[1], r[2], r[3]); }); });
  }, function (err) {
    if (err && err.status === 401) { guest(); }
  }).catch(function () { /* 창은 편의 기능이라 실패해도 화면은 그대로 */ });
})();
