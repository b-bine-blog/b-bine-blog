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

  document.addEventListener('DOMContentLoaded', function () {
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
        currentPassword: $('current-password').value,
        newPassword: next,
        newPasswordConfirm: $('new-password-confirm').value
      }, { activity: true }).then(function () {
        $('password-form').reset();
        $('submit').disabled = false;
        show('비밀번호를 바꿨습니다. 다른 기기에서는 로그아웃되었습니다.', false);
      }, function (err) {
        $('submit').disabled = false;
        show(errorMessage(err), true);
      });
    });
  });
})();
