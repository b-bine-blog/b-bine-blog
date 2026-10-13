// 소셜 로그인 마무리 화면 (008 FR-003~FR-007, D-125). 글자는 textContent로만 넣는다(D-64).
// 대기 토큰은 주소 #t=로 받아 바로 주소에서 지우고 이 화면 메모리에만 둔다.
// 새로 고치거나 다시 들어오면 이어 하지 않고 로그인 화면에서 처음부터 다시 한다(2026-10-13 사용자 요청).
(function () {
  'use strict';

  var RESEND_SECONDS = 60; // 서버도 1분 안에는 다시 보내지 않는다(USR-02)
  var $ = function (id) { return document.getElementById(id); };
  var state = { token: null, email: null, captchaToken: null, widgetId: null };

  var MESSAGES = {
    CODE_MISMATCH: '인증번호가 맞지 않습니다.',
    CODE_INVALID: '인증번호를 더 쓸 수 없습니다. 다시 보내기로 새 번호를 받아 주세요.',
    NICKNAME_DUPLICATE: '이미 쓰고 있는 닉네임입니다.',
    SIGNUP_UNAVAILABLE: '가입을 끝낼 수 없습니다. 처음부터 다시 해 주세요.',
    PASSWORD_MISMATCH: '비밀번호가 맞지 않습니다.',
    INVALID_CREDENTIALS: '비밀번호가 맞지 않습니다.',
    LOCKED: '비밀번호를 5번 잘못해 잠겼습니다. 잠시 뒤 다시 시도하거나 비밀번호 찾기로 잠금을 풀어 주세요.',
    CAPTCHA_REQUIRED: '사람 확인을 먼저 해 주세요.',
    CAPTCHA_FAILED: '사람 확인에 실패했습니다. 다시 해 주세요.'
  };
  var NICKNAME_REASONS = {
    DUPLICATE: '이미 쓰고 있는 닉네임입니다.',
    RESERVED: '쓸 수 없는 단어가 들어 있습니다.',
    INVALID_FORMAT: '2~12자 한글·영문·숫자로 입력해 주세요.'
  };
  var FIELDS = {
    email: '이메일을 확인해 주세요.',
    name: '이름은 2~20자 한글·영문으로 입력해 주세요.',
    nickname: '닉네임을 확인해 주세요.',
    phone: '010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요.',
    agreeTerms: '이용약관에 동의해 주세요.',
    agreePrivacy: '개인정보 수집·이용에 동의해 주세요.'
  };

  function readToken() {
    var token = new URLSearchParams(location.hash.replace(/^#/, '')).get('t');
    if (token) {
      history.replaceState(null, '', location.pathname); // 주소와 방문 기록에 토큰을 남기지 않는다
    }
    return token;
  }

  function restart() {
    location.replace('/login.html?socialError=SOCIAL_RESTART');
  }

  function show(el, message, isError) {
    api.setText(el, message);
    el.className = 'message ' + (isError ? 'message--error' : 'message--info');
  }

  function errorMessage(err) {
    if (err.code === 'SOCIAL_PENDING_INVALID') {
      return socialLogin.errorText(err.code);
    }
    if (err.code === 'CODE_MISMATCH' && err.body && err.body.remainingAttempts !== undefined) {
      return '인증번호가 맞지 않습니다. 남은 횟수: ' + err.body.remainingAttempts;
    }
    if (err.code === 'VALIDATION_FAILED' && err.body.fieldErrors && err.body.fieldErrors.length) {
      return FIELDS[err.body.fieldErrors[0].field] || '입력값을 다시 확인해 주세요.';
    }
    return MESSAGES[err.code] || (err.code && err.code.indexOf('SOCIAL_') === 0
      ? socialLogin.errorText(err.code) : err.message);
  }

  function fail(err) {
    ['step-email', 'step-signup', 'step-link'].forEach(function (id) { $(id).hidden = true; });
    show($('message'), errorMessage(err), true);
  }

  function render(view) {
    $('loading').hidden = true;
    api.setText($('title'), socialLogin.name(String(view.provider).toLowerCase()) + ' 로그인 마무리');
    $('step-email').hidden = view.step !== 'EMAIL';
    $('step-signup').hidden = view.step !== 'SIGNUP';
    $('step-link').hidden = view.step !== 'LINK';
    if (view.step === 'EMAIL') {
      api.setText($('email-hint'), '첫 가입 시 ' + socialLogin.name(String(view.provider).toLowerCase())
        + ' 이메일 인증 필요');
    }
    if (view.step === 'SIGNUP') {
      api.setText($('signup-email'), view.maskedEmail ? '(이메일 ' + view.maskedEmail + ')' : '');
    }
    if (view.step === 'LINK') {
      api.setText($('link-email'), view.maskedEmail || '같은 이메일');
    }
  }

  function done() {
    location.replace('/');
  }

  // 사람 확인(SEC-13, D-79): 비밀번호를 3번 틀리면 서버가 captchaRequired를 알려 준다
  function showCaptcha() {
    $('captcha').hidden = false;
    var ready = window.turnstile ? Promise.resolve() : api.get('/api/public-config').then(function (config) {
      state.siteKey = config.turnstileSiteKey;
      return new Promise(function (resolve, reject) {
        var script = document.createElement('script');
        script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
        script.async = true;
        script.onload = resolve;
        script.onerror = reject;
        document.head.appendChild(script);
      });
    });
    ready.then(function () {
      if (state.widgetId !== null) {
        window.turnstile.reset(state.widgetId);
        state.captchaToken = null;
        return;
      }
      state.widgetId = window.turnstile.render('#captcha', {
        sitekey: state.siteKey,
        callback: function (token) { state.captchaToken = token; },
        'expired-callback': function () { state.captchaToken = null; }
      });
    }, function () {
      show($('link-message'), '사람 확인을 불러오지 못했습니다. 새로고침해 주세요.', true);
    });
  }

  function lockResend() {
    var button = $('resend-code');
    var left = RESEND_SECONDS;
    button.disabled = true;
    api.setText(button, '다시 보내기 (' + left + '초)');
    var timer = setInterval(function () {
      left -= 1;
      if (left <= 0) {
        clearInterval(timer);
        button.disabled = false;
        api.setText(button, '다시 보내기');
      } else {
        api.setText(button, '다시 보내기 (' + left + '초)');
      }
    }, 1000);
  }

  function sendCode(email) {
    return api.post('/api/social/email-code', { token: state.token, email: email }, { activity: true })
      .then(function () {
        state.email = email;
        $('email').readOnly = true;
        $('send-code').hidden = true;
        $('code-box').hidden = false;
        show($('email-message'), '인증번호를 보냈습니다', false);
        lockResend();
      }, function (err) { show($('email-message'), errorMessage(err), true); });
  }

  function verifyCode() {
    api.post('/api/social/email-verify', { token: state.token, email: state.email, code: $('code').value.trim() },
      { activity: true }).then(render, function (err) { show($('email-message'), errorMessage(err), true); });
  }

  function checkNickname() {
    var nickname = $('nickname').value.trim();
    api.get('/api/members/nickname-availability?nickname=' + encodeURIComponent(nickname), { activity: true })
      .then(function (res) {
        show($('nickname-message'), res.available ? '쓸 수 있는 닉네임입니다.' : NICKNAME_REASONS[res.reason],
          !res.available);
      }, function (err) { show($('nickname-message'), errorMessage(err), true); });
  }

  function signUp() {
    var body = {
      token: state.token,
      name: $('name').value,
      nickname: $('nickname').value.trim(),
      phone: $('phone').value,
      agreeTerms: $('agree-terms').checked,
      agreePrivacy: $('agree-privacy').checked
    };
    if (!body.agreeTerms || !body.agreePrivacy) {
      show($('signup-message'), '필수 항목에 모두 동의해 주세요.', true);
      return;
    }
    $('submit-signup').disabled = true;
    api.post('/api/social/signup', body, { activity: true, noRefresh: true }).then(done, function (err) {
      $('submit-signup').disabled = false;
      show($('signup-message'), errorMessage(err), true);
    });
  }

  function link() {
    $('submit-link').disabled = true;
    api.post('/api/social/link', { token: state.token, password: $('password').value,
      captchaToken: state.captchaToken }, { activity: true, noRefresh: true }).then(done, function (err) {
      $('submit-link').disabled = false;
      if (err.body && err.body.captchaRequired) {
        showCaptcha();
      }
      show($('link-message'), errorMessage(err), true);
    });
  }

  // 뒤로 가기로 돌아와 이전 화면이 그대로 보이는 경우도 처음부터 다시 한다
  window.addEventListener('pageshow', function (e) {
    if (e.persisted) {
      restart();
    }
  });

  document.addEventListener('DOMContentLoaded', function () {
    state.token = readToken();
    if (!state.token) {
      restart();
      return;
    }
    api.post('/api/social/pending', { token: state.token }, { noRefresh: true }).then(render, function (err) {
      $('loading').hidden = true;
      fail(err);
    });
    $('send-code').addEventListener('click', function () {
      var email = $('email').value.trim();
      if (!email) {
        show($('email-message'), '이메일을 입력해 주세요.', true);
        return;
      }
      sendCode(email);
    });
    $('resend-code').addEventListener('click', function () { sendCode(state.email); });
    $('verify-code').addEventListener('click', verifyCode);
    $('check-nickname').addEventListener('click', checkNickname);
    $('submit-signup').addEventListener('click', signUp);
    $('submit-link').addEventListener('click', link);
  });
})();
