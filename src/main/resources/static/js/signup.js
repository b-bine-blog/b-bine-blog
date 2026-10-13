// 회원가입 화면 (T032, USR-01, USR-02). 세 단계를 한 화면에서 진행한다.
// 화면에 글자를 넣을 때는 api.setText(textContent)만 쓴다(D-64).
(function () {
  'use strict';

  var RESEND_SECONDS = 60; // 다시 보내기 1분(FR-005). 서버도 1분 안에는 메일을 보내지 않는다.
  var PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!-\/:-@\[-`{-~])[A-Za-z\d!-\/:-@\[-`{-~]{8,15}$/;

  var $ = function (id) { return document.getElementById(id); };
  var state = { email: null };

  var MESSAGES = {
    CODE_MISMATCH: '인증번호가 맞지 않습니다.',
    CODE_INVALID: '인증번호를 더 쓸 수 없습니다. 다시 보내기로 새 번호를 받아 주세요.',
    EMAIL_NOT_VERIFIED: '이메일 인증 시간이 지났습니다. 처음부터 다시 해 주세요.',
    NICKNAME_DUPLICATE: '이미 쓰고 있는 닉네임입니다.',
    SIGNUP_UNAVAILABLE: '가입을 끝낼 수 없습니다. 처음부터 다시 해 주세요.'
  };
  var NICKNAME_REASONS = {
    DUPLICATE: '이미 쓰고 있는 닉네임입니다.',
    RESERVED: '쓸 수 없는 단어가 들어 있습니다.',
    INVALID_FORMAT: '2~12자 한글·영문·숫자로 입력해 주세요.'
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
      return fieldMessage(err.body.fieldErrors[0].field);
    }
    return MESSAGES[err.code] || err.message;
  }

  function fieldMessage(field) {
    return {
      email: '이메일을 확인해 주세요.',
      password: '비밀번호는 8~15자, 영문·숫자·특수문자를 모두 넣어야 합니다.',
      name: '이름은 2~20자 한글·영문으로 입력해 주세요.',
      nickname: '닉네임을 확인해 주세요.',
      phone: '010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요.',
      agreeTerms: '이용약관에 동의해 주세요.',
      agreePrivacy: '개인정보 수집·이용에 동의해 주세요.'
    }[field] || '입력값을 다시 확인해 주세요.';
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
    var email = $('email').value.trim();
    if (!email) {
      show($('email-message'), '이메일을 입력해 주세요.', true);
      return;
    }
    api.post('/api/auth/signup/email-code', { email: email }, { activity: true }).then(function () {
      state.email = email;
      $('email').readOnly = true;
      $('send-code').hidden = true;
      $('code-box').hidden = false;
      show($('email-message'), '인증번호를 보냈습니다', false);
      lockResend();
    }, function (err) {
      show($('email-message'), errorMessage(err), true);
    });
  }

  function verifyCode() {
    api.post('/api/auth/signup/email-code/verify', { email: state.email, code: $('code').value.trim() },
      { activity: true }).then(function () {
      show($('email-message'), '인증되었습니다. 30분 안에 가입을 끝내 주세요.', false);
      $('code-box').hidden = true;
      $('step-password').hidden = false;
    }, function (err) {
      show($('email-message'), errorMessage(err), true);
    });
  }

  function toProfile() {
    var pw = $('password').value;
    if (!PASSWORD_RULE.test(pw)) {
      show($('password-message'), fieldMessage('password'), true);
      return;
    }
    if (pw !== $('password-confirm').value) {
      show($('password-message'), '두 비밀번호가 같지 않습니다.', true);
      return;
    }
    show($('password-message'), '', false);
    $('step-profile').hidden = false;
  }

  function checkNickname() {
    var nickname = $('nickname').value.trim();
    api.get('/api/members/nickname-availability?nickname=' + encodeURIComponent(nickname), { activity: true })
      .then(function (res) {
        show($('nickname-message'), res.available ? '쓸 수 있는 닉네임입니다.' : NICKNAME_REASONS[res.reason],
          !res.available);
      }, function (err) {
        show($('nickname-message'), errorMessage(err), true);
      });
  }

  function submit() {
    var body = {
      email: state.email,
      password: $('password').value,
      name: $('name').value,
      nickname: $('nickname').value.trim(),
      phone: $('phone').value,
      agreeTerms: $('agree-terms').checked,
      agreePrivacy: $('agree-privacy').checked
    };
    if (!body.agreeTerms || !body.agreePrivacy) {
      show($('profile-message'), '필수 항목에 모두 동의해 주세요.', true);
      return;
    }
    $('submit-signup').disabled = true;
    api.post('/api/auth/signup', body, { activity: true }).then(function () {
      ['step-email', 'step-password', 'step-profile'].forEach(function (id) { $(id).hidden = true; });
      $('step-done').hidden = false;
    }, function (err) {
      $('submit-signup').disabled = false;
      show($('profile-message'), errorMessage(err), true);
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    socialLogin.render($('social-buttons'), { label: '가입' });
    $('send-code').addEventListener('click', sendCode);
    $('resend-code').addEventListener('click', function () {
      api.post('/api/auth/signup/email-code', { email: state.email }, { activity: true }).then(function () {
        show($('email-message'), '인증번호를 보냈습니다', false);
        lockResend();
      }, function (err) { show($('email-message'), errorMessage(err), true); });
    });
    $('verify-code').addEventListener('click', verifyCode);
    $('to-profile').addEventListener('click', toProfile);
    $('check-nickname').addEventListener('click', checkNickname);
    $('submit-signup').addEventListener('click', submit);
  });
})();
