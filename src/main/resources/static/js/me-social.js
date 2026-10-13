// 내 정보의 소셜 로그인 연결 관리 (008 FR-010, D-125). 글자는 textContent로만 넣는다(D-64).
// 연결은 로그인한 채로 /oauth2/authorization/{서비스}?mode=link로 다녀온다. 마지막 로그인 방법은 끊지 못한다.
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  function show(message, isError) {
    var el = $('social-message');
    api.setText(el, message);
    el.className = 'message ' + (isError ? 'message--error' : 'message--info');
  }

  function formatDate(iso) {
    return iso ? iso.substring(0, 10).replace(/-/g, '.') : '';
  }

  function connect(provider) {
    // 로그인 확인 쿠키가 만료됐으면 먼저 새로 받는다(api가 401이면 한 번 갱신한다)
    api.get('/api/members/me/social', { activity: true }).then(function () {
      socialLogin.start(provider, { mode: 'link' });
    }, function (err) { show(err.message, true); });
  }

  function disconnect(provider) {
    if (!window.confirm(socialLogin.name(provider) + ' 연결을 끊을까요?')) {
      return;
    }
    api.del('/api/members/me/social/' + encodeURIComponent(provider), null, { activity: true }).then(function () {
      show(socialLogin.name(provider) + ' 연결을 끊었습니다.', false);
      load();
    }, function (err) {
      show(err.code ? socialLogin.errorText(err.code) : err.message, true);
    });
  }

  function render(res) {
    var list = $('social-list');
    var linked = {};
    (res.accounts || []).forEach(function (a) { linked[a.provider] = a; });
    var providers = (res.available || []).slice();
    Object.keys(linked).forEach(function (p) {
      if (providers.indexOf(p) < 0) {
        providers.push(p); // 키를 뺀 서비스라도 연결된 것은 보여 주고 끊을 수 있게 한다
      }
    });
    list.replaceChildren();
    providers.forEach(function (provider) {
      var li = document.createElement('li');
      var label = document.createElement('span');
      var button = document.createElement('button');
      button.type = 'button';
      if (linked[provider]) {
        api.setText(label, socialLogin.name(provider) + ' · ' + formatDate(linked[provider].connectedAt) + ' 연결');
        api.setText(button, '연결 끊기');
        button.className = 'button-quiet button-small';
        button.addEventListener('click', function () { disconnect(provider); });
      } else {
        api.setText(label, socialLogin.name(provider) + ' · 연결 안 됨');
        api.setText(button, '연결하기');
        button.className = 'button-light button-small';
        button.addEventListener('click', function () { connect(provider); });
      }
      li.appendChild(label);
      li.appendChild(button);
      list.appendChild(li);
    });
    $('social-card').hidden = providers.length === 0;
    if (!res.hasPassword) {
      api.setText($('password-link'), '비밀번호 만들기');
    }
  }

  function load() {
    api.get('/api/members/me/social', { activity: true }).then(render, function () {
      $('social-card').hidden = true;
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    var params = new URLSearchParams(location.search);
    if (params.get('social') === 'linked') {
      show('소셜 계정을 연결했습니다.', false);
      params.delete('social');
      var rest = params.toString();
      history.replaceState(null, '', location.pathname + (rest ? '?' + rest : ''));
    }
    socialLogin.showError($('social-message'));
    load();
  });
})();
