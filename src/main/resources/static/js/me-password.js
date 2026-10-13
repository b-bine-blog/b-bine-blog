// 비밀번호 변경 화면 (T064, USR-07, FR-044). 이 기기는 로그인을 유지하고 다른 기기만 로그아웃된다(D-97).
(function () {
  'use strict';

  var PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!-\/:-@\[-`{-~])[A-Za-z\d!-\/:-@\[-`{-~]{8,15}$/;
  var RULE_MESSAGE = '비밀번호는 8~15자, 영문·숫자·특수문자를 모두 넣어야 합니다.';
  var $ = function (id) { return document.getElementById(id); };

  function show(message, isError) {
    api.setText($('message'), message);
    $('message').className = 'message ' + (isError ? 'message--error' : 'message--info');
  }

  function errorMessage(err) {
    if (err.code === 'CURRENT_PASSWORD_MISMATCH') {
      return '현재 비밀번호가 맞지 않습니다.';
    }
    if (err.code === 'VALIDATION_FAILED' && err.body.fieldErrors && err.body.fieldErrors.length) {
      return err.body.fieldErrors[0].field === 'newPasswordConfirm' ? '두 비밀번호가 같지 않습니다.' : RULE_MESSAGE;
    }
    if (err.status === 401) {
      location.href = '/login.html?next=' + encodeURIComponent(location.pathname);
    }
    return err.message;
  }

  var hasPassword = true;

  // 소셜로만 가입한 회원은 현재 비밀번호 없이 비밀번호를 만든다(008 FR-009, D-125)
  function loadMode() {
    api.get('/api/members/me/social', { activity: true }).then(function (res) {
      hasPassword = res.hasPassword;
      $('current-box').hidden = hasPassword;
      if (!hasPassword) {
        api.setText($('title'), '비밀번호 만들기');
        api.setText($('submit'), '만들기');
        document.title = '비밀번호 만들기 - b-bine blog';
      }
    }, function () { /* 실패하면 바꾸기 화면 그대로 둔다 */ });
  }

  document.addEventListener('DOMContentLoaded', function () {
    loadMode();
    $('password-form').addEventListener('submit', function (e) {
      e.preventDefault();
      var next = $('new-password').value;
      if (!PASSWORD_RULE.test(next)) {
        show(RULE_MESSAGE, true);
        return;
      }
      if (next !== $('new-password-confirm').value) {
        show('두 비밀번호가 같지 않습니다.', true);
        return;
      }
      $('submit').disabled = true;
      api.put('/api/members/me/password', {
        currentPassword: hasPassword ? $('current-password').value : null,
        newPassword: next,
        newPasswordConfirm: $('new-password-confirm').value
      }, { activity: true }).then(function () {
        $('password-form').reset();
        $('submit').disabled = false;
        show(hasPassword ? '비밀번호를 바꿨습니다. 다른 기기에서는 로그아웃되었습니다.'
          : '비밀번호를 만들었습니다. 이제 이메일로도 로그인할 수 있습니다.', false);
        if (!hasPassword) {
          loadMode();
        }
      }, function (err) {
        $('submit').disabled = false;
        show(errorMessage(err), true);
      });
    });
  });
})();
