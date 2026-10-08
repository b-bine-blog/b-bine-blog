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

  function renderHeader(me) {
    var header = document.createElement('header');
    header.className = 'site-header';
    var nav = document.createElement('nav');
    nav.appendChild(link('/', '메인블로그'));
    var right = document.createElement('div');
    right.className = 'site-header__right';
    if (me) {
      var name = document.createElement('span');
      name.className = 'site-header__nickname';
      name.textContent = me.nickname;
      right.appendChild(name);
      right.appendChild(link('/me.html', '내 정보'));
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
      right.appendChild(link('/signup.html', '회원가입'));
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
