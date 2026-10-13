// 회원탈퇴 화면 (T051, USR-05, FR-045~FR-048).
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  function formatDateTime(iso) {
    var d = new Date(iso);
    var pad = function (n) { return (n < 10 ? '0' : '') + n; };
    return d.getFullYear() + '.' + pad(d.getMonth() + 1) + '.' + pad(d.getDate()) + ' '
      + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function renderBlogs(blogs) {
    var list = $('owned-blogs');
    blogs.forEach(function (blog) {
      var li = document.createElement('li');
      var text = blog.name + ' (/blog/' + blog.address + ')';
      if (blog.status === 'CLOSING' && blog.closeScheduledAt) {
        text += ' - ' + formatDateTime(blog.closeScheduledAt) + ' 폐쇄 예정';
      }
      li.textContent = text;
      list.appendChild(li);
    });
  }

  function load() {
    api.get('/api/members/me/withdrawal', { activity: true }).then(function (res) {
      if (res.canWithdraw) {
        $('form-box').hidden = false;
      } else {
        renderBlogs(res.ownedBlogs);
        $('blocked').hidden = false;
      }
    }, function (err) {
      if (err.status === 401) {
        location.href = '/login.html?next=' + encodeURIComponent(location.pathname);
        return;
      }
      api.setText($('message'), err.message);
    });
  }

  var hasPassword = true;

  document.addEventListener('DOMContentLoaded', function () {
    load();
    api.get('/api/members/me/social', { activity: true }).then(function (res) {
      hasPassword = res.hasPassword;
      $('password-box').hidden = !hasPassword;
    }, function () { /* 실패하면 비밀번호 칸을 그대로 둔다 */ });
    $('withdraw').addEventListener('click', function () {
      if (!window.confirm('정말 탈퇴할까요?')) {
        return;
      }
      api.post('/api/members/me/withdrawal', { password: hasPassword ? $('password').value : null }, { activity: true })
        .then(function () {
          location.href = '/';
        }, function (err) {
          var messages = {
            PASSWORD_MISMATCH: '비밀번호가 맞지 않습니다.',
            OWNS_BLOGS: '블로그장인 블로그가 있어 탈퇴할 수 없습니다.'
          };
          api.setText($('message'), messages[err.code] || err.message);
          if (err.code === 'OWNS_BLOGS') {
            location.reload();
          }
        });
    });
  });
})();
