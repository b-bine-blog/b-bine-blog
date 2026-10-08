// 태그 글 목록 /tags/{이름} (004 T045, FR-023). 공개 블로그의 보이는 글만 최신순.
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var name = decodeURIComponent(location.pathname.split('/')[2] || '');

  function load(page) {
    api.get('/api/tags/' + encodeURIComponent(name) + '/posts?page=' + page).then(function (r) {
      board.renderPosts($('post-list'), r.items, { showBlog: true, empty: '이 태그의 글이 없습니다.' });
      board.renderPager($('post-pages'), r.page, r.totalPages, load);
    }, function (err) {
      board.sayError($('message'), err.status === 400 ? { message: '태그 이름이 올바르지 않습니다.' } : err,
        function () { load(page); });
    });
  }

  api.setText($('tag-name'), '#' + name);
  document.title = '#' + name + ' - 메인블로그';
  load(1);
})();
