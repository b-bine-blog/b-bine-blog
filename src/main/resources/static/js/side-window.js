// 우리 사이트 모든 화면 위에 떠 있는 내 창 (D-139). layout.js가 머리 메뉴를 그린 뒤 불러온다. 제목 줄을 끌어 옮기고, − 로 접으면 프로필만 남고, 프로필을 누르면 다시 펼친다.
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
  }

  function docked() { return !!dock && state.docked !== false; }

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
      if (e.button !== 0 || e.target.closest('.side-window__min')) { return; }
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
    var head = el('div', 'side-window__head');
    head.appendChild(el('span', 'side-window__grip', '⠿'));
    var min = el('button', 'side-window__min', '−');
    min.type = 'button';
    min.setAttribute('aria-label', '접기');
    min.title = '접기';
    min.addEventListener('click', function () { show(true); });
    head.appendChild(min);
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
    draggable(head, win);
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
