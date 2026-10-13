// 공통 화면 틀 (T021): 상단 메뉴와 모든 화면 아래의 개인정보 처리방침 링크(FR-017).
// 메뉴 글자는 textContent로만 넣는다(D-64).
// 로그인하면 오른쪽 내 메뉴에 종 아이콘을 넣고 알림 사이드바(notifications.js)를 시작한다(003 T012, FR-028).
// 메인과 모든 개별 블로그 화면이 이 파일을 같이 써서 어디서나 같은 종 아이콘이 보인다(4.2).
// 메인 관리자는 "관리자" 링크가 보이고 "내 블로그·블로그 만들기"는 보이지 않는다(005 T010, D-90).
(function () {
  'use strict';

  // 지금 배포된 버전. 새 버전을 낼 때 README 업데이트 표·release-notes와 같이 올린다(D-108)
  var SITE_VERSION = 'v1.07';

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
    var logo = link('/', 'b-bine blog');
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
    right.appendChild(themeButton()); // 다크 모드(D-113)
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

  // 닉네임을 누르면 여는 프로필 메뉴(2026-10-08 사용자 요청): 내 정보, 블로그 만들기(BLG-01, 블로그가 없을 때만), 로그아웃.
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
    if (!me.admin) { list.appendChild(link('/inquiry.html#mine', '내 문의')); } // ADM-08, D-112
    if (!me.admin) {
      // 운영하는 블로그가 이미 있으면 메뉴에서는 뺀다. 더 만들 때는 내 블로그 화면의 링크를 쓴다(2026-10-12 사용자 결정, D-109)
      var create = link('/blogs/new.html', '블로그 만들기');
      create.hidden = true;
      list.appendChild(create);
      api.get('/api/me/blogs').then(function (res) {
        create.hidden = res.owned.length > 0;
      }, function () { create.hidden = false; });
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

  // 밝기 버튼(D-113). 지금 어두우면 해, 밝으면 달 그림을 보이고 누르면 바꾼다. 값은 theme.js가 저장한다.
  var SUN = 'M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10zm0-5 1.2 3h-2.4L12 2zm0 20-1.2-3h2.4L12 22zM2 12l3-1.2v2.4L2 12zm20 0-3 1.2v-2.4L22 12zM4.9 4.9l3 1.3-1.7 1.7-1.3-3zm14.2 14.2-3-1.3 1.7-1.7 1.3 3zM4.9 19.1l1.3-3 1.7 1.7-3 1.3zM19.1 4.9l-1.3 3-1.7-1.7 3-1.3z';
  var MOON = 'M21 14.5A8.5 8.5 0 0 1 9.5 3a8.5 8.5 0 1 0 11.5 11.5z';

  function themeButton() {
    var ns = 'http://www.w3.org/2000/svg';
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'site-header__theme';
    var svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('width', '18');
    svg.setAttribute('height', '18');
    svg.setAttribute('aria-hidden', 'true');
    var path = document.createElementNS(ns, 'path');
    path.setAttribute('fill', 'currentColor');
    svg.appendChild(path);
    button.appendChild(svg);
    function paint() {
      var dark = window.theme && window.theme.current() === 'dark';
      path.setAttribute('d', dark ? SUN : MOON);
      var label = dark ? '밝은 화면으로 보기' : '어두운 화면으로 보기';
      button.setAttribute('aria-label', label);
      button.title = label;
    }
    button.addEventListener('click', function () {
      if (window.theme) { window.theme.toggle(); }
      paint();
    });
    paint();
    return button;
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
    footer.appendChild(document.createTextNode(' · '));
    footer.appendChild(link('/rules.html', '운영 규칙')); // 삭제 시점·제한 같은 주요 규칙(D-114)
    footer.appendChild(document.createTextNode(' · '));
    footer.appendChild(link('/inquiry.html', '시스템 문의')); // ADM-08, D-112
    // 변경사항: 저장소 README의 업데이트 표로 간다. 지금 버전을 옆에 적는다(2026-10-13 사용자 요청, D-108)
    var source = link('https://github.com/p-minsu/b-bine-blog#업데이트', '변경사항 ' + SITE_VERSION);
    source.target = '_blank';
    source.rel = 'noopener noreferrer';
    footer.appendChild(document.createTextNode(' · '));
    footer.appendChild(source);
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
