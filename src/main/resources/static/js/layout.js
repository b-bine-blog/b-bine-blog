// 공통 화면 틀 (T021): 상단 메뉴와 모든 화면 아래의 개인정보 처리방침 링크(FR-017).
// 메뉴 글자는 textContent로만 넣는다(D-64).
// 로그인하면 오른쪽 내 메뉴에 종 아이콘을 넣고 알림 사이드바(notifications.js)를 시작한다(003 T012, FR-028).
// 메인과 모든 개별 블로그 화면이 이 파일을 같이 써서 어디서나 같은 종 아이콘이 보인다(4.2).
// 메인 관리자는 "관리자" 링크가 보이고 "내 블로그·블로그 만들기"는 보이지 않는다(005 T010, D-90).
(function () {
  'use strict';

  function link(href, text) {
    var a = document.createElement('a');
    a.href = href;
    a.textContent = text;
    return a;
  }

  // 왼쪽 로고·공지, 가운데 검색, 오른쪽 내 메뉴(2026-10-08 배치 변경). "회원가입"은 버튼처럼 보이고, "블로그 만들기"는 닉네임 메뉴 안에 있다.
  function renderHeader(me) {
    var header = document.createElement('header');
    header.className = 'site-header';
    var nav = document.createElement('nav');
    nav.setAttribute('aria-label', '상단 메뉴');
    var logo = link('/', '블바인 블로그');
    logo.className = 'site-logo';
    nav.appendChild(logo);

    var menu = document.createElement('div');
    menu.className = 'site-header__menu';
    menu.appendChild(link('/notices', '공지사항')); // 004 US10
    nav.appendChild(menu);

    // 어느 화면에서나 바로 검색(통합 검색 화면으로 이동, 004 US6)
    var search = document.createElement('form');
    search.className = 'site-header__search';
    search.setAttribute('role', 'search');
    search.action = '/search';
    search.method = 'get';
    var q = document.createElement('input');
    q.type = 'search';
    q.name = 'q';
    q.maxLength = 20;
    q.placeholder = '검색';
    q.setAttribute('aria-label', '통합 검색');
    search.appendChild(q);
    if (!document.getElementById('search-form')) { // 검색창이 있는 화면에는 두 개를 두지 않는다
      nav.appendChild(search);
    }

    var right = document.createElement('div');
    right.className = 'site-header__right';
    if (me && me.admin) {
      // 메인 관리자는 블로그 활동을 하지 않으므로 "내 블로그·블로그 만들기" 대신 관리자 화면 링크(005 T010, D-90)
      right.appendChild(link('/admin', '관리자'));
      right.appendChild(bellButton());
      right.appendChild(profileMenu(me));
    } else if (me) {
      right.appendChild(link('/me/blogs.html', '내 블로그'));
      right.appendChild(bellButton());
      right.appendChild(profileMenu(me));
    } else {
      right.appendChild(link('/login.html', '로그인'));
      var signup = link('/signup.html', '회원가입');
      signup.className = 'button-light';
      right.appendChild(signup);
    }
    nav.appendChild(right);
    header.appendChild(nav);
    document.body.insertBefore(header, document.body.firstChild);
  }

  // 닉네임을 누르면 여는 프로필 메뉴(2026-10-08 사용자 요청): 내 정보, 블로그 만들기(BLG-01), 로그아웃.
  function profileMenu(me) {
    var box = document.createElement('div');
    box.className = 'profile-menu';
    var toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'site-header__nickname';
    toggle.textContent = me.nickname;
    toggle.setAttribute('aria-haspopup', 'true');
    toggle.setAttribute('aria-expanded', 'false');
    var list = document.createElement('div');
    list.className = 'profile-menu__list';
    list.id = 'profile-menu-list';
    list.hidden = true;
    toggle.setAttribute('aria-controls', list.id);
    list.appendChild(link('/me/index.html', '내 정보'));
    if (!me.admin) {
      list.appendChild(link('/blogs/new.html', '블로그 만들기'));
    }
    var logout = document.createElement('button');
    logout.type = 'button';
    logout.textContent = '로그아웃';
    logout.addEventListener('click', function () {
      api.post('/api/auth/logout', null, { activity: true, noRefresh: true })
        .finally(function () { location.href = '/'; });
    });
    list.appendChild(logout);

    function setOpen(open) {
      list.hidden = !open;
      toggle.setAttribute('aria-expanded', String(open));
    }
    toggle.addEventListener('click', function (e) {
      e.stopPropagation();
      setOpen(list.hidden);
    });
    document.addEventListener('click', function (e) {
      if (!box.contains(e.target)) { setOpen(false); }
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !list.hidden) { setOpen(false); toggle.focus(); }
    });
    box.appendChild(toggle);
    box.appendChild(list);
    return box;
  }

  // 종 아이콘 버튼. 그림은 SVG 요소로 만들고 안 읽은 개수는 배지에 textContent로 넣는다.
  function bellButton() {
    var ns = 'http://www.w3.org/2000/svg';
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'site-header__bell';
    button.setAttribute('aria-label', '알림');
    var svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('width', '20');
    svg.setAttribute('height', '20');
    svg.setAttribute('aria-hidden', 'true');
    var path = document.createElementNS(ns, 'path');
    path.setAttribute('d', 'M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22zm7-6V11a7 7 0 0 0-5.5-6.84V3.5a1.5 1.5 0 0 0-3 0v.66A7 7 0 0 0 5 11v5l-2 2v1h18v-1l-2-2z');
    path.setAttribute('fill', 'currentColor');
    svg.appendChild(path);
    button.appendChild(svg);
    var badge = document.createElement('span');
    badge.className = 'site-header__badge';
    badge.hidden = true;
    button.appendChild(badge);
    loadNotifications(button, badge);
    return button;
  }

  // 알림 스크립트는 로그인했을 때만 불러온다(화면마다 script 태그를 더하지 않게).
  function loadNotifications(button, badge) {
    if (window.notifications) {
      window.notifications.start(button, badge);
      return;
    }
    var script = document.createElement('script');
    script.src = '/js/notifications.js';
    script.onload = function () { window.notifications.start(button, badge); };
    document.head.appendChild(script);
  }

  function renderFooter() {
    var footer = document.createElement('footer');
    footer.className = 'site-footer';
    footer.appendChild(link('/privacy.html', '개인정보 처리방침'));
    document.body.appendChild(footer);
  }

  document.addEventListener('DOMContentLoaded', function () {
    renderFooter();
    if (document.body.hasAttribute('data-no-header')) {
      return;
    }
    api.get('/api/auth/session')
      .then(function (me) {
        renderHeader(me);
        if (window.session) {
          window.session.start();
        }
      }, function () { renderHeader(null); });
  });
})();
