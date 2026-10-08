// 관리자 화면을 열 때 액세스 토큰만 만료된 경우 (005 T009). 토큰을 한 번 새로 받아 다시 열고, 안 되면 로그인 화면으로 간다.
(function () {
  'use strict';
  var KEY = 'mb.adminRefreshTried';
  function toLogin() { location.replace('/login.html?next=/admin'); }
  var tried = null;
  try { tried = sessionStorage.getItem(KEY); } catch (e) { tried = '1'; }
  if (tried) {
    try { sessionStorage.removeItem(KEY); } catch (e) { /* 무시 */ }
    toLogin();
    return;
  }
  try { sessionStorage.setItem(KEY, '1'); } catch (e) { /* 무시 */ }
  api.post('/api/auth/refresh', null, { noRefresh: true })
    .then(function () { location.reload(); }, toLogin);
})();
