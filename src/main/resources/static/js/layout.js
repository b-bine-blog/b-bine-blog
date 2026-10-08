// 공통 화면 틀 (T021): 상단 메뉴와 모든 화면 아래의 개인정보 처리방침 링크(FR-017).
// 메뉴 글자는 textContent로만 넣는다(D-64).
(function () {
  'use strict';

  function link(href, text) {
    var a = document.createElement('a');
    a.href = href;
    a.textContent = text;
    return a;
  }

  // 왼쪽 로고·공지, 가운데 검색, 오른쪽 내 메뉴(2026-10-08 배치 변경). 자주 쓰는 "블로그 만들기"·"회원가입"은 버튼처럼 보인다.
  function renderHeader(me) {
    var header = document.createElement('header');
    header.className = 'site-header';
    var nav = document.createElement('nav');
    nav.setAttribute('aria-label', '상단 메뉴');
    var logo = link('/', '메인블로그');
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
    if (me) {
      var create = link('/blogs/new.html', '블로그 만들기');
      create.className = 'button-light';
      right.appendChild(create);
      right.appendChild(link('/me/blogs.html', '내 블로그'));
      var name = link('/me/index.html', me.nickname);
      name.className = 'site-header__nickname';
      name.title = '내 정보';
      right.appendChild(name);
      var logout = document.createElement('button');
      logout.type = 'button';
      logout.textContent = '로그아웃';
      logout.addEventListener('click', function () {
        api.post('/api/auth/logout', null, { activity: true, noRefresh: true })
          .finally(function () { location.href = '/'; });
      });
      right.appendChild(logout);
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
