// 로그인 유지 화면 처리 (T044, FR-019, FR-024, FR-025, D-62, D-97).
// - "로그인 유지"를 체크하지 않은 로그인은 마지막 활동 25분 뒤 "로그인을 연장할까요?"를 묻는다(30분 만료 5분 전).
// - 글쓰기 화면은 startEditorKeepAlive()로 입력이 있으면 5분마다 연장한다.
// 마지막 활동 시각은 api.js가 활동 요청을 보낼 때 sessionStorage에 남긴다.
(function (global) {
  'use strict';

  var KEY_REMEMBER = 'mb.rememberMe';
  var KEY_LAST_ACTIVITY = 'mb.lastActivity';
  var IDLE_MS = 30 * 60 * 1000;      // 서버 설정 auth.idle-timeout과 같게 둔다
  var WARN_BEFORE_MS = 5 * 60 * 1000;
  var EDITOR_INTERVAL_MS = 5 * 60 * 1000; // auth.extend-interval

  function storage() {
    try {
      return global.sessionStorage;
    } catch (e) {
      return null;
    }
  }

  function get(key) {
    var s = storage();
    try { return s ? s.getItem(key) : null; } catch (e) { return null; }
  }

  function set(key, value) {
    var s = storage();
    try { if (s) { s.setItem(key, value); } } catch (e) { /* 저장 못 해도 동작은 계속 */ }
  }

  function markActivity() {
    set(KEY_LAST_ACTIVITY, String(Date.now()));
  }

  function extend() {
    return api.post('/api/auth/extend', null, { activity: true }).then(markActivity);
  }

  var warned = false;

  function checkIdle() {
    if (get(KEY_REMEMBER) === 'true') {
      return;
    }
    var last = Number(get(KEY_LAST_ACTIVITY) || Date.now());
    var idle = Date.now() - last;
    if (idle >= IDLE_MS) {
      return;
    }
    if (idle >= IDLE_MS - WARN_BEFORE_MS && !warned) {
      warned = true;
      if (global.confirm('오랫동안 활동이 없어 곧 로그아웃됩니다. 로그인을 연장할까요?')) {
        extend().finally(function () { warned = false; });
      }
    }
  }

  global.session = {
    rememberLogin: function (rememberMe) {
      set(KEY_REMEMBER, rememberMe ? 'true' : 'false');
      markActivity();
    },
    markActivity: markActivity,
    start: function () {
      if (!get(KEY_LAST_ACTIVITY)) {
        markActivity();
      }
      setInterval(checkIdle, 30 * 1000);
    },
    // 글쓰기 화면: 입력이 있었던 5분 구간마다 연장 요청을 보낸다(FR-025).
    startEditorKeepAlive: function (editorElement) {
      var typed = false;
      editorElement.addEventListener('input', function () { typed = true; });
      setInterval(function () {
        if (typed) {
          typed = false;
          extend();
        }
      }, EDITOR_INTERVAL_MS);
    }
  };
})(window);
