// 소셜 로그인 버튼과 실패 안내 (008 FR-001, FR-014, D-125).
// 앱 키가 있는 서비스만 서버가 알려 주고, 그 버튼만 보인다. 글자는 textContent로만 넣는다(D-64).
(function (global) {
  'use strict';

  var NAMES = { google: '구글', kakao: '카카오', naver: '네이버' };
  var ERRORS = {
    CANCELLED: '소셜 로그인을 취소했습니다.',
    SOCIAL_LOGIN_FAILED: '소셜 로그인에 실패했습니다. 잠시 뒤 다시 시도해 주세요.',
    SOCIAL_ALREADY_LINKED: '이 소셜 계정은 이미 다른 회원과 연결되어 있습니다.',
    SOCIAL_NOT_ALLOWED: '관리자 계정은 소셜 로그인을 쓸 수 없습니다.',
    SOCIAL_LINK_FROM_ME: '같은 이메일로 가입한 계정이 있습니다. 그 계정으로 로그인한 뒤 내 정보에서 연결해 주세요.',
    SOCIAL_PENDING_INVALID: '시간이 지났습니다. 소셜 로그인을 처음부터 다시 해 주세요.',
    SOCIAL_LAST_LOGIN_METHOD: '마지막 로그인 방법은 끊을 수 없습니다. 비밀번호를 먼저 만들어 주세요.',
    UNAUTHENTICATED: '로그인이 풀렸습니다. 다시 로그인한 뒤 연결해 주세요.',
    RATE_LIMITED: '요청이 너무 많습니다. 잠시 뒤 다시 시도해 주세요.'
  };

  function name(provider) {
    return NAMES[String(provider).toLowerCase()] || provider;
  }

  function errorText(code) {
    return ERRORS[code] || ERRORS.SOCIAL_LOGIN_FAILED;
  }

  /** 소셜 로그인 화면으로 간다. 메인 화면 위 로그인 창 안이면 바깥 화면을 옮긴다(D-118). */
  function start(provider, params) {
    var url = '/oauth2/authorization/' + encodeURIComponent(provider);
    var query = new URLSearchParams(params || {}).toString();
    (window.top || window).location.href = query ? url + '?' + query : url;
  }

  /** container 안에 버튼을 그린다. options.remember()가 true면 로그인 유지로 시작한다. */
  function render(container, options) {
    if (!container) {
      return Promise.resolve([]);
    }
    options = options || {};
    return api.get('/api/social/providers').then(function (res) {
      var providers = res.providers || [];
      container.replaceChildren();
      providers.forEach(function (provider) {
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'social-button social-button--' + provider;
        api.setText(button, name(provider) + '로 ' + (options.label || '로그인'));
        button.addEventListener('click', function () {
          var remember = !!(options.remember && options.remember());
          // 돌아와도 같은 탭이라 sessionStorage가 남는다. 로그인 유지가 아니면 25분 뒤 연장을 묻는다(D-62)
          if (global.session) {
            global.session.rememberLogin(remember);
          }
          start(provider, remember ? { remember: '1' } : null);
        });
        container.appendChild(button);
      });
      container.hidden = providers.length === 0;
      return providers;
    }, function () {
      container.hidden = true;
      return [];
    });
  }

  /** 주소의 ?socialError=를 읽어 안내하고 주소에서 지운다. */
  function showError(el) {
    var params = new URLSearchParams(location.search);
    var code = params.get('socialError');
    if (!code || !el) {
      return;
    }
    api.setText(el, errorText(code));
    el.className = 'message message--error';
    params.delete('socialError');
    var rest = params.toString();
    history.replaceState(null, '', location.pathname + (rest ? '?' + rest : '') + location.hash);
  }

  global.socialLogin = { render: render, start: start, showError: showError, name: name, errorText: errorText };
})(window);
