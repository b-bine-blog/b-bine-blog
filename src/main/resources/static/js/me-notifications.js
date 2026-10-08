// 알림 설정 화면 (003 T032, FR-054, FR-057, D-102 #26·#27). 끌 수 있는 9가지·보관 기간·일괄 삭제.
// 끌 수 없는 알림은 체크박스 없이 안내 문구로만 보인다. 글자는 textContent로만 넣는다(D-64).
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var KEYS = ['postComment', 'commentReply', 'postLike', 'newFollower', 'blogSubscribed', 'joinRequest',
    'joinResult', 'reportResult', 'notice'];
  var current = null;

  function show(el, text, isError) {
    el.className = 'message ' + (isError ? 'message--error' : 'message--info');
    api.setText(el, text);
  }

  function render(s) {
    current = s;
    var form = $('settings');
    KEYS.forEach(function (k) { form.elements[k].checked = !!s[k]; });
    form.querySelectorAll('input[name="retentionDays"]').forEach(function (r) {
      r.checked = Number(r.value) === s.retentionDays;
    });
    form.hidden = false;
    $('bulk').hidden = false;
  }

  function load() {
    api.get('/api/me/notification-settings', { activity: true }).then(render, function (err) {
      if (err.status === 401) {
        location.href = '/login.html?next=' + encodeURIComponent(location.pathname);
        return;
      }
      show($('message'), err.message, true);
    });
  }

  function save(e) {
    e.preventDefault();
    var form = $('settings');
    var body = {};
    KEYS.forEach(function (k) { body[k] = form.elements[k].checked; });
    var picked = form.querySelector('input[name="retentionDays"]:checked');
    body.retentionDays = picked ? Number(picked.value) : current.retentionDays;
    if (body.retentionDays < current.retentionDays
      && !confirm(body.retentionDays + '일이 지난 알림은 다음 정리 때(새벽 4시) 지워집니다. 바꿀까요?')) {
      return;
    }
    $('save').disabled = true;
    api.put('/api/me/notification-settings', body, { activity: true }).then(function (s) {
      $('save').disabled = false;
      render(s);
      show($('save-message'), '저장했습니다.', false);
    }, function (err) {
      $('save').disabled = false;
      show($('save-message'), err.message, true);
    });
  }

  function deleteAll() {
    if (!confirm('받은 알림을 모두 지울까요? 지운 알림은 되돌릴 수 없습니다.')) { return; }
    api.del('/api/notifications?tab=ALL', null, { activity: true }).then(function (r) {
      show($('bulk-message'), '알림 ' + r.deleted + '건을 지웠습니다.', false);
    }, function (err) { show($('bulk-message'), err.message, true); });
  }

  document.addEventListener('DOMContentLoaded', function () {
    $('settings').addEventListener('submit', save);
    $('delete-all').addEventListener('click', deleteAll);
    load();
  });
})();
