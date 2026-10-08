// 공지사항 목록 /notices (004 T053, FR-065). 쓰기 버튼은 관리자에게만 보이고, 권한은 서버가 확인한다.
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  function load(page) {
    api.get('/api/notices?page=' + page).then(function (r) {
      board.renderPosts($('post-list'), r.items, { empty: '공지사항이 없습니다.' });
      board.renderPager($('post-pages'), r.page, r.totalPages, load);
    }, function (err) { board.sayError($('message'), err, function () { load(page); }); });
  }

  api.get('/api/auth/session').then(function (me) { $('write').hidden = !me.admin; }, function () { /* 비회원 */ });
  load(1);
})();
