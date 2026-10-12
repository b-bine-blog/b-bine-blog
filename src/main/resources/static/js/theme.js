// 화면 밝기 (2026-10-12 사용자 요청, D-113). <head>에서 먼저 불러 그리기 전에 data-theme을 정한다(깜빡임 방지).
// 고른 값(light·dark)은 이 브라우저에만 저장하고, 고른 적이 없으면 기기 설정(prefers-color-scheme)을 따른다.
// 상단 메뉴의 버튼은 layout.js가 window.theme.toggle()로 바꾼다.
(function (global) {
  'use strict';

  var KEY = 'mb.theme';
  var root = document.documentElement;

  function saved() {
    try {
      var v = global.localStorage.getItem(KEY);
      return v === 'light' || v === 'dark' ? v : null;
    } catch (e) {
      return null;
    }
  }

  function systemDark() {
    return !!(global.matchMedia && global.matchMedia('(prefers-color-scheme: dark)').matches);
  }

  function current() {
    return saved() || (systemDark() ? 'dark' : 'light');
  }

  function apply(mode) {
    root.setAttribute('data-theme', mode);
  }

  function set(mode) {
    try { global.localStorage.setItem(KEY, mode); } catch (e) { /* 저장 못 해도 이번 화면에는 적용 */ }
    apply(mode);
    return mode;
  }

  apply(current());
  if (global.matchMedia) {
    var mq = global.matchMedia('(prefers-color-scheme: dark)');
    var follow = function () { if (!saved()) { apply(current()); } };
    if (mq.addEventListener) { mq.addEventListener('change', follow); } else if (mq.addListener) { mq.addListener(follow); }
  }

  global.theme = {
    current: current,
    toggle: function () { return set(current() === 'dark' ? 'light' : 'dark'); }
  };
})(window);
