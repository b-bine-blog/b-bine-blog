// 메인 화면 위에 떠 있는 내 창 (D-139). 제목 줄을 끌어 옮기고, − 로 접으면 프로필만 남고, 프로필을 누르면 다시 펼친다.
// 위치와 접힘 상태는 이 브라우저에만 기억한다(localStorage). 글자는 모두 textContent로 넣는다(D-64).
(function () {
  'use strict';

  var KEY = 'sideWindow';
  var MOBILE = window.matchMedia('(max-width: 720px)');
  var el = board.el;
  var state = load();
  var win, mini;

  function load() {
    try { return JSON.parse(localStorage.getItem(KEY)) || {}; } catch (e) { return {}; }
  }

  function save() {
    try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) { /* 저장 못 해도 창은 그대로 동작 */ }
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
      start = { px: e.clientX, py: e.clientY, x: box.offsetLeft, y: box.offsetTop, moved: false };
      handle.setPointerCapture(e.pointerId);
    });
    handle.addEventListener('pointermove', function (e) {
      if (!start) { return; }
      var dx = e.clientX - start.px, dy = e.clientY - start.py;
      if (!start.moved && Math.abs(dx) + Math.abs(dy) < 5) { return; }
      start.moved = true;
      var p = place(box, start.x + dx, start.y + dy);
      state.x = p.x;
      state.y = p.y;
    });
    handle.addEventListener('pointerup', function () {
      if (!start) { return; }
      var moved = start.moved;
      start = null;
      if (moved) { save(); } else if (onTap) { onTap(); }
    });
    handle.addEventListener('pointercancel', function () { start = null; });
  }

  function show(minimized) {
    state.min = minimized;
    save();
    var from = minimized ? win : mini;
    var to = minimized ? mini : win;
    from.hidden = true;
    to.hidden = false;
    var p = place(to, state.x != null ? state.x : from.offsetLeft, state.y != null ? state.y : from.offsetTop);
    state.x = p.x;
    state.y = p.y;
  }

  function build(me, profile, following, summary, unread) {
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

    mini = el('button', 'side-window side-window--mini');
    mini.type = 'button';
    mini.setAttribute('aria-label', '내 정보 펼치기');
    mini.appendChild(avatar(profile, 'side-window__avatar--small'));
    mini.appendChild(el('span', null, profile.nickname));

    win.hidden = true;
    mini.hidden = true;
    document.body.appendChild(win);
    document.body.appendChild(mini);
    draggable(head, win);
    draggable(mini, mini, function () { show(false); });
    window.addEventListener('resize', function () {
      var box = win.hidden ? mini : win;
      place(box, box.offsetLeft, box.offsetTop);
    });

    // 처음에는 오른쪽 위. 휴대폰 화면에서는 접힌 채로 시작한다
    var minimized = state.min != null ? state.min : MOBILE.matches;
    var box = minimized ? mini : win;
    box.hidden = false;
    if (state.x == null) {
      state.x = window.innerWidth - box.offsetWidth - 24;
      state.y = 88;
    }
    place(box, state.x, state.y);
    state.min = minimized;
  }

  function count(promise, pick) {
    return promise.then(pick, function () { return 0; });
  }

  api.get('/api/auth/session').then(function (me) {
    if (!me || !me.memberId || me.admin) { return; } // 메인 관리자는 블로그 활동을 하지 않는다
    return Promise.all([
      api.get('/api/users/' + me.memberId + '/profile'),
      count(api.get('/api/users/' + me.memberId + '/following'), function (r) { return r.totalElements; }),
      api.get('/api/me/summary'),
      count(api.get('/api/notifications/unread-count'), function (r) { return r.unreadCount; })
    ]).then(function (r) { build(me, r[0], r[1], r[2], r[3]); });
  }).catch(function () { /* 창은 편의 기능이라 실패해도 화면은 그대로 */ });
})();
