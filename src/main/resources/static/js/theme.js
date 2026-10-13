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

// 시간대별 사이트 색 (시안, 2026-10-13 사용자 요청). 보는 사람 기기의 시각으로 색상(hue)을 정하고 1분마다 조금씩 옮긴다.
// 아침(5~11시) 파랑, 점심(11~17시) 초록, 저녁(17~5시) 빨강. 구간 안에서는 색을 그대로 두고, 바뀌는 시각 앞뒤 1시간 동안 서서히 넘어간다.
// 색상은 한 방향으로만 돈다: 파랑 → 초록 → (노랑·주황) → 빨강 → (보라) → 파랑. 그래서 해 질 녘·새벽처럼 자연스럽게 이어진다.
// 확인용으로 주소 뒤에 ?hour=7.5 처럼 붙이면 그 시각의 색을 보여 준다.
(function (global) {
  'use strict';

  var root = document.documentElement;
  var STOPS = [ // [시각, 색상]. 색상은 계속 줄어들게 적는다(밤을 지나 다음날 6시는 30시, 파랑 212 - 360 = -148)
    [6, 212], [10, 212],   // 아침 파랑 (5~11시, 4~6시는 빨강에서 넘어오는 중)
    [12, 148], [16, 148],  // 점심 초록 (11~17시, 지금 사이트 색)
    [18, 8], [28, 8],      // 저녁 빨강 (17~5시)
    [30, -148]             // 다음날 아침 파랑
  ];

  function previewHour() {
    var m = /[?&]hour=([0-9.]+)/.exec(global.location.search);
    return m ? parseFloat(m[1]) % 24 : null;
  }

  function hueAt(hour) {
    var h = hour < STOPS[0][0] ? hour + 24 : hour;
    for (var i = 0; i < STOPS.length - 1; i++) {
      var a = STOPS[i], b = STOPS[i + 1];
      if (h >= a[0] && h <= b[0]) {
        var t = (h - a[0]) / (b[0] - a[0]);
        var eased = t * t * (3 - 2 * t); // 구간 끝에서 부드럽게
        return ((a[1] + (b[1] - a[1]) * eased) % 360 + 360) % 360;
      }
    }
    return STOPS[0][1];
  }

  function update() {
    var fixed = previewHour();
    var now = new Date();
    var hour = fixed != null ? fixed : now.getHours() + now.getMinutes() / 60;
    root.style.setProperty('--hue', hueAt(hour).toFixed(1));
    root.setAttribute('data-daytime', '');
  }

  update();
  if (previewHour() == null) { global.setInterval(update, 60000); }
})(window);
