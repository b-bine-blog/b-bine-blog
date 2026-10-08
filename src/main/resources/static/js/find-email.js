// 이메일 찾기 화면 (T071, USR-08, FR-034~FR-039). 값은 textContent로만 넣는다(D-64).
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  function show(message, isError) {
    api.setText($('message'), message);
    $('message').className = 'message ' + (isError ? 'message--error' : 'message--info');
  }

  function formatDate(iso) {
    return iso ? iso.replace(/-/g, '.') : '';
  }

  function renderAccounts(accounts) {
    var list = $('accounts');
    while (list.firstChild) {
      list.removeChild(list.firstChild);
    }
    accounts.forEach(function (account) {
      var li = document.createElement('li');
      var text = document.createElement('span');
      text.textContent = account.maskedEmail + ' (' + formatDate(account.joinedOn) + ' 가입) ';
      var button = document.createElement('a');
      button.className = 'button';
      button.textContent = '비밀번호 재설정';
      button.href = '/find-password.html?resetToken=' + encodeURIComponent(account.resetToken);
      li.appendChild(text);
      li.appendChild(button);
      list.appendChild(li);
    });
    $('result').hidden = false;
  }

  document.addEventListener('DOMContentLoaded', function () {
    $('find-form').addEventListener('submit', function (e) {
      e.preventDefault();
      show('', false);
      $('result').hidden = true;
      $('submit').disabled = true;
      api.post('/api/auth/find-email', { name: $('name').value, phone: $('phone').value }, { activity: true })
        .then(function (res) {
          $('submit').disabled = false;
          if (!res.accounts.length) {
            show('일치하는 회원 정보가 없습니다', true);
            return;
          }
          renderAccounts(res.accounts);
        }, function (err) {
          $('submit').disabled = false;
          show(err.code === 'RATE_LIMITED'
            ? '이메일 찾기는 10분에 5번까지 할 수 있습니다. 잠시 뒤 다시 시도해 주세요.' : err.message, true);
        });
    });
  });
})();
