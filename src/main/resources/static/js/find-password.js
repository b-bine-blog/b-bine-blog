// 비밀번호 찾기·재설정 화면 (T056, USR-06, FR-030~FR-033).
// 가입 여부와 관계없이 같은 문구를 보여준다(FR-030). resetToken이 있으면 이메일 찾기에서 온 것이다(FR-038).
(function () {
  'use strict';

  var RESEND_SECONDS = 60; // 다시 보내기 1분(FR-031)
  var PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!-\/:-@\[-`{-~])[A-Za-z\d!-\/:-@\[-`{-~]{8,15}$/;
  var SENT = '입력한 이메일로 안내를 보냈습니다';

  var $ = function (id) { return document.getElementById(id); };
  var state = { email: null, resetToken: null };

  var MESSAGES = {
    CODE_INVALID: '인증번호를 더 쓸 수 없습니다. 다시 보내기로 새 번호를 받아 주세요.',
    TOKEN_EXPIRED: '시간이 지났습니다. 이메일 찾기를 다시 해 주세요.'
  };

  function show(el, message, isError) {
    api.setText(el, message);
    el.className = 'message ' + (isError ? 'message--error' : 'message--info');
  }

  function errorMessage(err) {
    if (err.code === 'CODE_MISMATCH' && err.body.remainingAttempts !== undefined) {
      return '인증번호가 맞지 않습니다. 남은 횟수: ' + err.body.remainingAttempts;
    }
    if (err.code === 'VALIDATION_FAILED' && err.body.fieldErrors && err.body.fieldErrors.length) {
      var field = err.body.fieldErrors[0].field;
      if (field === 'newPasswordConfirm') {
        return '두 비밀번호가 같지 않습니다.';
      }
      if (field === 'newPassword') {
        return '비밀번호는 8~15자, 영문·숫자·특수문자를 모두 넣어야 합니다.';
      }
    }
    return MESSAGES[err.code] || err.message;
  }

  function target(extra) {
    var body = state.resetToken ? { resetToken: state.resetToken } : { email: state.email };
    Object.keys(extra).forEach(function (k) { body[k] = extra[k]; });
    return body;
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

  function sendCode() {
    var request = state.resetToken
      ? api.post('/api/auth/find-email/reset-code', { resetToken: state.resetToken }, { activity: true })
      : api.post('/api/auth/password-reset/code', { email: state.email }, { activity: true });
    return request.then(function () {
      show($('email-message'), state.resetToken ? '인증번호를 보냈습니다' : SENT, false);
      $('code-box').hidden = false;
      lockResend();
    }, function (err) {
      show($('email-message'), errorMessage(err), true);
    });
  }

  function verifyCode() {
    api.post('/api/auth/password-reset/verify', target({ code: $('code').value.trim() }), { activity: true })
      .then(function () {
        show($('email-message'), '확인되었습니다. 새 비밀번호를 입력해 주세요.', false);
        $('code').readOnly = true;
        $('verify-code').hidden = true;
        $('resend-code').hidden = true;
        $('password-box').hidden = false;
      }, function (err) {
        show($('email-message'), errorMessage(err), true);
      });
  }

  function save(e) {
    e.preventDefault();
    var pw = $('new-password').value;
    if (!PASSWORD_RULE.test(pw)) {
      show($('message'), '비밀번호는 8~15자, 영문·숫자·특수문자를 모두 넣어야 합니다.', true);
      return;
    }
    if (pw !== $('new-password-confirm').value) {
      show($('message'), '두 비밀번호가 같지 않습니다.', true);
      return;
    }
    $('submit').disabled = true;
    api.post('/api/auth/password-reset', target({
      code: $('code').value.trim(),
      newPassword: pw,
      newPasswordConfirm: $('new-password-confirm').value
    }), { activity: true }).then(function () {
      alert('비밀번호를 바꿨습니다. 새 비밀번호로 로그인해 주세요.');
      location.href = '/login.html';
    }, function (err) {
      $('submit').disabled = false;
      show($('message'), errorMessage(err), true);
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    var token = new URLSearchParams(location.search).get('resetToken');
    if (token) {
      state.resetToken = token;
      $('email-box').hidden = true;
      // 주소창에 토큰이 남지 않게 한다.
      history.replaceState(null, '', location.pathname);
      sendCode();
    }
    $('send-code').addEventListener('click', function () {
      var email = $('email').value.trim();
      if (!email) {
        show($('email-message'), '이메일을 입력해 주세요.', true);
        return;
      }
      state.email = email;
      $('email').readOnly = true;
      $('send-code').hidden = true;
      sendCode();
    });
    $('resend-code').addEventListener('click', sendCode);
    $('verify-code').addEventListener('click', verifyCode);
    $('reset-form').addEventListener('submit', save);
  });
})();
