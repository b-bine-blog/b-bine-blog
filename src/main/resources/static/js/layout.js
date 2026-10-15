// 공통 화면 틀 (T021): 상단 메뉴와 모든 화면 아래의 개인정보 처리방침 링크(FR-017).
// 메뉴 글자는 textContent로만 넣는다(D-64).
// 로그인하면 오른쪽 내 메뉴에 종 아이콘을 넣고 알림 사이드바(notifications.js)를 시작한다(003 T012, FR-028).
// 메인과 모든 개별 블로그 화면이 이 파일을 같이 써서 어디서나 같은 종 아이콘이 보인다(4.2).
// 메인 관리자는 "관리자" 링크가 보이고 "블로그 만들기"는 보이지 않는다(005 T010, D-90). 내 블로그는 내 정보 화면에 있다(D-117).
(function () {
  'use strict';

  // 지금 배포된 버전. 새 버전을 낼 때 README 업데이트 표·release-notes와 같이 올린다(D-108)
  var SITE_VERSION = 'v1.20';

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
      right.appendChild(bellButton());
      right.appendChild(profileMenu(me));
    } else {
      // 로그인·회원가입은 지금 화면 위에 뜨는 창으로 연다(D-118). 새 탭·가운데 클릭은 예전처럼 화면으로 간다
      var login = link('/login.html', '로그인');
      openInModal(login);
      right.appendChild(login);
      var signup = link('/signup.html', '회원가입');
      signup.className = 'button-light';
      openInModal(signup);
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
    if (!me.admin) { // 작성글·작성댓글·스크랩(D-134). 회원가입처럼 지금 화면 위 창으로 연다(2026-10-14 사용자 요청)
      [['#posts', '작성글'], ['#comments', '작성댓글'], ['#scraps', '스크랩']].forEach(function (m) {
        var a = link('/me/activity.html' + m[0], m[1]);
        a.addEventListener('click', function (e) {
          if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) { return; }
          e.preventDefault();
          list.hidden = true;
          toggle.setAttribute('aria-expanded', 'false');
          authModal(a.getAttribute('href'), 'activity');
        });
        list.appendChild(a);
      });
    }
    var likesLink = null;
    if (!me.admin) {
      // 운영하는 블로그가 이미 있으면 메뉴에서는 뺀다. 더 만들 때는 내 정보의 내 블로그에 있는 링크를 쓴다(D-109, D-117)
      var create = link('/blogs/new.html', '블로그 만들기');
      create.hidden = true;
      list.appendChild(create);
      api.get('/api/me/blogs').then(function (res) {
        create.hidden = res.owned.length > 0;
      }, function () { create.hidden = false; });
    }
    if (!me.admin) { // 좋아요(D-137): 로그아웃 바로 위. 작성글처럼 창으로 연다
      likesLink = link('/me/activity.html#likes', '좋아요');
      likesLink.addEventListener('click', function (e) {
        if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) { return; }
        e.preventDefault();
        list.hidden = true;
        toggle.setAttribute('aria-expanded', 'false');
        authModal(likesLink.getAttribute('href'), 'activity');
      });
      list.appendChild(likesLink);
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

  // 로그인·회원가입 창 (2026-10-13 사용자 요청, D-118). 같은 사이트의 화면을 창 안에 그대로 띄운다.
  // 창 안의 화면은 머리 메뉴·아래 링크 없이 보이고(embedded), 로그인이 끝나면 바깥 화면을 새로 고친다(login.js).
  var embedded = window.top !== window.self;
  var AUTH_PAGES = /^\/(login|signup|find-email|find-password)\.html$/;
  // 회원 프로필도 지금 화면 위 창으로 띄운다(SOC-03, 2026-10-14 사용자 요청)
  var PROFILE_PAGE = /^\/profile\/\d+\/?$/;
  // 작성글·작성댓글·스크랩도 창으로 띄운다(D-134)
  var ACTIVITY_PAGE = /^\/me\/activity\.html$/;

  function openInModal(a) {
    a.addEventListener('click', function (e) {
      if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) { return; }
      e.preventDefault();
      authModal(a.getAttribute('href'));
    });
  }

  var modal = null;
  // kind: 없으면 로그인·회원가입, 'profile'은 회원 프로필, 'activity'는 작성글·작성댓글·스크랩. 뒤 둘은 넓은 창이다.
  function authModal(href, kind) {
    var profile = kind === true || kind === 'profile';
    var wide = profile || kind === 'activity';
    if (!modal) {
      modal = document.createElement('dialog');
      modal.className = 'auth-modal';
      var close = document.createElement('button');
      close.type = 'button';
      close.className = 'auth-modal__close';
      close.textContent = '×';
      close.setAttribute('aria-label', '닫기');
      close.addEventListener('click', function () { modal.close(); });
      var frame = document.createElement('iframe');
      frame.className = 'auth-modal__frame';
      frame.title = '로그인·회원가입';
      // 창 높이를 안의 화면 높이에 맞춘다(같은 사이트라 안을 읽을 수 있다)
      frame.addEventListener('load', function () {
        var doc = frame.contentDocument;
        if (!doc || !doc.body) { return; }
        // 프로필 창은 신고 창이 들어갈 만큼 최소 높이를 둔다
        var min = modal.classList.contains('auth-modal--wide') ? 460 : 0;
        var fit = function () { frame.style.height = Math.min(Math.max(Math.ceil(doc.body.getBoundingClientRect().height), min), window.innerHeight * 0.85) + 'px'; };
        fit();
        if (window.ResizeObserver) { new ResizeObserver(fit).observe(doc.body); }
      });
      modal.appendChild(close);
      modal.appendChild(frame);
      // 창 바깥(어두운 곳)을 누르면 닫는다
      modal.addEventListener('click', function (e) { if (e.target === modal) { modal.close(); } });
      modal.addEventListener('close', function () { frame.src = 'about:blank'; });
      document.body.appendChild(modal);
    }
    var url = new URL(href, location.href);
    var frame = modal.querySelector('iframe');
    modal.classList.toggle('auth-modal--wide', wide);
    frame.title = kind === 'activity' ? '내 활동' : (profile ? '회원 프로필' : '로그인·회원가입');
    if (!wide) { url.searchParams.set('next', location.pathname + location.search); }
    frame.src = url.pathname + url.search + url.hash;
    if (!modal.open) { modal.showModal(); }
  }

  // 화면 어디서든(글 작성자, 댓글, 팔로워 목록 등) 회원 프로필 링크를 누르면 창으로 연다
  // 떠 있는 내 창(D-139)은 우리 사이트 모든 화면에 띄운다(창 안 화면 제외). 화면마다 script 태그를 더하지 않게 여기서 부른다
  function loadSideWindow() {
    if (embedded || window.sideWindowLoaded || AUTH_PAGES.test(location.pathname)) { return; }
    window.sideWindowLoaded = true;
    var script = document.createElement('script');
    script.src = '/js/side-window.js';
    document.head.appendChild(script);
  }

  // 다른 화면 스크립트(메인 떠 있는 창, D-139)도 같은 창으로 열 수 있게 내보낸다
  window.siteLayout = {
    openActivity: function (href) { authModal(href, 'activity'); },
    openAuth: function (href) { authModal(href); }
  };

  function openProfilesInModal() {
    document.addEventListener('click', function (e) {
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) { return; }
      var a = e.target.closest && e.target.closest('a[href]');
      if (!a || a.target) { return; }
      var url = new URL(a.href, location.href);
      if (url.origin !== location.origin || !PROFILE_PAGE.test(url.pathname)) { return; }
      e.preventDefault();
      authModal(url.pathname, true);
    });
  }

  // 창 안에서는 로그인·회원가입·아이디/비밀번호 찾기 사이만 창 안에서 오가고, 다른 링크는 바깥 화면에서 연다
  function keepLinksInModal() {
    // 창 안에 초점이 있어도 Esc로 창을 닫는다
    document.addEventListener('keydown', function (e) {
      var dialog = e.key === 'Escape' && window.top.document.querySelector('dialog.auth-modal[open]');
      if (dialog) { dialog.close(); }
    });
    document.addEventListener('click', function (e) {
      var a = e.target.closest && e.target.closest('a[href]');
      if (!a || a.target) { return; }
      var url = new URL(a.href, location.href);
      if (url.origin === location.origin && (AUTH_PAGES.test(url.pathname) || PROFILE_PAGE.test(url.pathname)
          || ACTIVITY_PAGE.test(url.pathname))) { return; }
      e.preventDefault();
      window.top.location.href = a.href;
    });
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
    var source = link('https://github.com/b-bine-blog/b-bine-blog#업데이트', '변경사항 ' + SITE_VERSION);
    source.target = '_blank';
    source.rel = 'noopener noreferrer';
    footer.appendChild(document.createTextNode(' · '));
    footer.appendChild(source);
    document.body.appendChild(footer);
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (embedded) {
      document.documentElement.classList.add('is-embedded');
      keepLinksInModal();
      return;
    }
    renderFooter();
    openProfilesInModal();
    if (document.body.hasAttribute('data-no-header')) {
      return;
    }
    api.get('/api/auth/session')
      .then(function (me) {
        renderHeader(me);
        if (window.session) {
          window.session.start();
        }
        loadSideWindow();
      }, function () { renderHeader(null); loadSideWindow(); });
  });
})();
