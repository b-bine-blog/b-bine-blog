// 관리자 통계 탭 (005 T052, ADM-05, FR-040, D-103 #21). 숫자 카드 2개(회원·블로그)와 일별 가입·글 수 표. 모든 값은 textContent.
(function () {
  'use strict';
  var ui = window.adminUI;

  function $(id) { return document.getElementById(id); }

  function card(label, value, hint) {
    var c = ui.el('div', 'stat-card');
    c.appendChild(ui.el('p', 'stat-card__label', label));
    c.appendChild(ui.el('p', 'stat-card__value', value));
    c.appendChild(ui.el('p', 'hint', hint));
    return c;
  }

  function load() {
    var msg = $('stats-message');
    ui.message(msg, '불러오는 중…');
    api.get('/api/admin/stats', { activity: true }).then(function (s) {
      ui.message(msg, '');
      var cards = $('stats-cards');
      ui.clear(cards);
      cards.appendChild(card('회원 수', s.memberCount, '탈퇴·관리자 제외'));
      cards.appendChild(card('블로그 수', s.blogCount, '운영 중·폐쇄 예정(숨김 포함)'));
      var rows = s.dailySignups.map(function (d, i) {
        return { date: d.date, signups: d.count, posts: s.dailyPosts[i] ? s.dailyPosts[i].count : 0 };
      }).reverse();
      ui.table($('stats-daily'), [
        { label: '날짜', value: function (r) { return r.date; } },
        { label: '가입', value: function (r) { return r.signups; } },
        { label: '블로그 글', value: function (r) { return r.posts; } }
      ], rows, null, '자료가 없습니다.');
    }, function (err) { ui.message(msg, ui.errorText(err), true); });
  }

  ui.registerTab('stats', load);
})();
