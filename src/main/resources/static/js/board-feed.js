// 메인 피드 (004 T031, FR-061~FR-064, D-100 #18). 최신·인기는 번호 페이지(10·20·30개 선택, BRD-02), 구독·팔로우는 20개씩 "더 보기".
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var tab = 'LATEST';
  var cursor = null;
  var shown = [];

  function select(next) {
    tab = next;
    document.querySelectorAll('[data-feed]').forEach(function (b) {
      b.setAttribute('aria-selected', b.getAttribute('data-feed') === tab ? 'true' : 'false');
    });
    cursor = null;
    shown = [];
    load(1);
  }

  function load(page) {
    var paged = tab === 'LATEST' || tab === 'POPULAR';
    $('feed-size-box').hidden = !paged;
    var url = '/api/feed?tab=' + tab + (paged ? '&page=' + page + '&size=' + $('feed-size').value
      : (cursor ? '&cursor=' + encodeURIComponent(cursor) : ''));
    board.say($('feed-message'), '');
    api.get(url).then(function (r) {
      if (paged) {
        board.renderPosts($('feed-list'), r.items, { showBlog: true });
        board.renderPager($('feed-pages'), r.page, r.totalPages, load);
        $('feed-more').hidden = true;
      } else {
        shown = shown.concat(r.items);
        board.renderPosts($('feed-list'), shown, {
          showBlog: true,
          empty: tab === 'SUBSCRIBED' ? '구독한 블로그의 최근 30일 글이 없습니다.' : '팔로우한 회원의 최근 30일 글이 없습니다.'
        });
        board.clear($('feed-pages'));
        cursor = r.nextCursor;
        $('feed-more').hidden = !cursor;
      }
    }, function (err) { board.sayError($('feed-message'), err, function () { load(page); }); });
  }

  document.querySelectorAll('[data-feed]').forEach(function (b) {
    b.addEventListener('click', function () { select(b.getAttribute('data-feed')); });
  });
  $('feed-more').addEventListener('click', function () { load(1); });
  $('feed-size').addEventListener('change', function () { load(1); });

  api.get('/api/auth/session').then(function () {
    document.querySelectorAll('[data-login]').forEach(function (b) { b.hidden = false; });
  }, function () { /* 비회원은 최신·인기만 */ });
  select('LATEST');
})();
