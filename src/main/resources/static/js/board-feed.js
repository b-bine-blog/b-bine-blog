// 메인 피드 (004 T031, FR-061~FR-064, D-100 #18). 최신·인기는 번호 페이지(10·20·30개 선택, BRD-02), 구독·팔로우는 20개씩 "더 보기".
// 팔로우 옆 + 로 태그 탭을 더한다(D-135). 태그 탭은 계정에 저장되고 그 태그 글을 번호 페이지로 보여 준다.
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var el = board.el;
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

  var TAG = 'TAG:';

  function load(page) {
    var tag = tab.indexOf(TAG) === 0 ? tab.substring(TAG.length) : null;
    var paged = !!tag || tab === 'LATEST' || tab === 'POPULAR';
    $('feed-size-box').hidden = !paged;
    var url = tag ? '/api/tags/' + encodeURIComponent(tag) + '/posts?page=' + page + '&size=' + $('feed-size').value
      : '/api/feed?tab=' + tab + (paged ? '&page=' + page + '&size=' + $('feed-size').value
      : (cursor ? '&cursor=' + encodeURIComponent(cursor) : ''));
    board.say($('feed-message'), '');
    api.get(url).then(function (r) {
      if (tag) {
        board.renderPosts($('feed-list'), r.items, { showBlog: true, empty: '#' + tag + ' 태그가 붙은 글이 없습니다.' });
        board.renderPager($('feed-pages'), r.page, r.totalPages, load);
        $('feed-more').hidden = true;
      } else if (paged) {
        // 최신 1페이지는 최근 메인 공지를 위에 고정한다(D-109)
        if (r.notices && r.notices.length) {
          board.renderPosts($('feed-list'), r.notices, { pinned: true });
          if (r.items.length) { board.renderPosts($('feed-list'), r.items, { showBlog: true, append: true }); }
        } else {
          board.renderPosts($('feed-list'), r.items, { showBlog: true });
        }
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

  // 태그 탭(D-135)
  function renderTagTabs(names) {
    var box = $('feed-tag-tabs');
    board.clear(box);
    names.forEach(function (name) {
      var group = el('span', 'tab-group');
      var b = el('button', null, '#' + name);
      b.type = 'button';
      b.setAttribute('role', 'tab');
      b.setAttribute('data-feed', TAG + name);
      b.setAttribute('aria-selected', tab === TAG + name ? 'true' : 'false');
      b.addEventListener('click', function () { select(TAG + name); });
      var x = el('button', 'tab-remove', '×');
      x.type = 'button';
      x.setAttribute('aria-label', '#' + name + ' 탭 지우기');
      x.title = '탭 지우기';
      x.addEventListener('click', function () {
        if (!confirm('#' + name + ' 탭을 지울까요?')) { return; }
        api.del('/api/me/feed-tags/' + encodeURIComponent(name), null, { activity: true }).then(function () {
          if (tab === TAG + name) { select('LATEST'); }
          loadTagTabs();
        }, function (err) { board.sayError($('feed-message'), err); });
      });
      group.appendChild(b);
      group.appendChild(x);
      box.appendChild(group);
    });
  }

  function loadTagTabs() {
    api.get('/api/me/feed-tags').then(renderTagTabs, function () { renderTagTabs([]); });
  }

  function closeTagForm() {
    $('feed-tag-form').hidden = true;
    $('feed-tag-input').value = '';
    $('feed-tag-add').setAttribute('aria-expanded', 'false');
  }

  $('feed-tag-add').setAttribute('aria-expanded', 'false');
  $('feed-tag-add').addEventListener('click', function () {
    if (!$('feed-tag-form').hidden) { closeTagForm(); return; }
    $('feed-tag-form').hidden = false;
    $('feed-tag-add').setAttribute('aria-expanded', 'true');
    $('feed-tag-input').focus();
  });
  $('feed-tag-cancel').addEventListener('click', closeTagForm);
  $('feed-tag-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var raw = $('feed-tag-input').value.trim();
    if (!raw) { $('feed-tag-input').focus(); return; }
    api.put('/api/me/feed-tags/' + encodeURIComponent(raw), null, { activity: true }).then(function (names) {
      closeTagForm();
      renderTagTabs(names);
      var added = names.filter(function (n) { return n === raw.replace(/^#+/, '').trim().replace(/\s+/g, '_').toLowerCase(); })[0];
      if (added) { select(TAG + added); }
    }, function (err) {
      board.say($('feed-message'), err.status === 400 ? '태그는 한글·영문·숫자·_ 로 1~20자까지 쓸 수 있어요.' : err.message, true);
    });
  });

  $('feed-more').addEventListener('click', function () { load(1); });
  $('feed-size').addEventListener('change', function () { load(1); });

  api.get('/api/auth/session').then(function () {
    document.querySelectorAll('[data-login]').forEach(function (b) { b.hidden = false; });
    loadTagTabs();
  }, function () { /* 비회원은 최신·인기만 */ });
  select('LATEST');
})();
