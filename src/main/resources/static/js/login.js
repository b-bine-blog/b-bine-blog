// 로그인 화면 (T043, USR-03, FR-018~FR-023).
// 3번 실패하면 서버가 captchaRequired를 알려 주고, 그때 Turnstile 위젯을 띄운다(SEC-13, D-79).
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var captcha = { widgetId: null, token: null, siteKey: null, loading: null };

  function loadTurnstile() {
    if (captcha.loading) {
      return captcha.loading;
    }
    captcha.loading = api.get('/api/public-config').then(function (config) {
      captcha.siteKey = config.turnstileSiteKey;
      return new Promise(function (resolve, reject) {
        var script = document.createElement('script');
        script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
        script.async = true;
        script.onload = resolve;
        script.onerror = reject;
        document.head.appendChild(script);
      });
    });
    return captcha.loading;
  }

  function showCaptcha() {
    $('captcha').hidden = false;
    loadTurnstile().then(function () {
      if (captcha.widgetId !== null) {
        window.turnstile.reset(captcha.widgetId);
        captcha.token = null;
        return;
      }
      captcha.widgetId = window.turnstile.render('#captcha', {
        sitekey: captcha.siteKey,
        callback: function (token) { captcha.token = token; },
        'expired-callback': function () { captcha.token = null; }
      });
    }, function () {
      api.setText($('message'), '사람 확인을 불러오지 못했습니다. 새로고침해 주세요.');
    });
  }

  function minutesText(seconds) {
    var minutes = Math.ceil(seconds / 60);
    return minutes + '분';
  }

  function onError(err) {
    if (err.body && err.body.captchaRequired) {
      showCaptcha();
    }
    var message;
    switch (err.code) {
      case 'LOCKED':
        message = '로그인을 5번 잘못해 잠겼습니다. ' + minutesText(err.body.retryAfterSeconds || 300)
          + ' 뒤에 다시 시도하거나 비밀번호 찾기로 잠금을 풀어 주세요.';
        break;
      case 'CAPTCHA_REQUIRED':
        message = '사람 확인을 먼저 해 주세요.';
        break;
      case 'CAPTCHA_FAILED':
        message = '사람 확인에 실패했습니다. 다시 해 주세요.';
        break;
      case 'RATE_LIMITED':
        message = '요청이 너무 많습니다. 잠시 뒤 다시 시도해 주세요.';
        break;
      default:
        message = err.message;
    }
    api.setText($('message'), message);
  }

  function returnUrl() {
    var next = new URLSearchParams(location.search).get('next');
    // 같은 사이트 경로만 허용한다(열린 리다이렉트 방지).
    return next && next.charAt(0) === '/' && next.charAt(1) !== '/' ? next : '/';
  }

  document.addEventListener('DOMContentLoaded', function () {
    $('login-form').addEventListener('submit', function (e) {
      e.preventDefault();
      api.setText($('message'), '');
      var rememberMe = $('remember-me').checked;
      $('submit').disabled = true;
      api.post('/api/auth/login', {
        email: $('email').value.trim(),
        password: $('password').value,
        rememberMe: rememberMe,
        captchaToken: captcha.token
      }, { activity: true, noRefresh: true }).then(function () {
        session.rememberLogin(rememberMe);
        // 메인 화면 위 로그인 창 안이면 바깥 화면을 로그인한 상태로 새로 고친다(D-118)
        if (window.top !== window.self) {
          window.top.location.reload();
          return;
        }
        location.href = returnUrl();
      }, function (err) {
        $('submit').disabled = false;
        onError(err);
      });
    });
  });
})();
