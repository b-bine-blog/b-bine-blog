// 내 활동: 작성글·작성댓글·스크랩 (D-134), 좋아요한 글 (D-137). 글자는 textContent로만 넣는다(D-64).
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var el = board.el;
  var TABS = {
    posts: { url: '/api/me/posts', empty: '아직 쓴 글이 없습니다.' },
    comments: { url: '/api/me/comments', empty: '아직 쓴 댓글이 없습니다.' },
    scraps: { url: '/api/me/scraps', empty: '스크랩한 글이 없습니다. 글 아래 "스크랩" 버튼으로 모을 수 있어요.' },
    likes: { url: '/api/me/likes', empty: '좋아요한 글이 없습니다.' }  // D-137
  };
  var tab = 'posts';

  function commentUrl(c) {
    return c.blogAddress
      ? '/blog/' + encodeURIComponent(c.blogAddress) + '/posts/' + c.postId
      : '/notices/' + c.postId;
  }

  function renderComments(list, items) {
    board.clear(list);
    if (items.length === 0) { list.appendChild(el('li', 'blog-list__empty', TABS.comments.empty)); return; }
    items.forEach(function (c) {
      var li = el('li', 'post-item');
      var body = el('div', 'post-item__body');
      var a = el('a', 'post-item__title', c.content);
      a.href = commentUrl(c);
      body.appendChild(a);
      var meta = (c.blogName ? c.blogName + ' · ' : '공지 · ') + c.postTitle + ' · ' + board.formatTime(c.createdAt);
      body.appendChild(el('p', 'post-item__meta', meta));
      li.appendChild(body);
      list.appendChild(li);
    });
  }

  function load(page) {
    api.get(TABS[tab].url + '?page=' + page, { activity: true }).then(function (r) {
      var list = $('activity-list');
      if (tab === 'comments') {
        renderComments(list, r.items);
      } else {
        board.renderPosts(list, r.items, { showBlog: true, empty: TABS[tab].empty });
      }
      board.renderPager($('activity-pages'), r.page, r.totalPages, load);
      $('activity').hidden = false;
    }, function (err) {
      if (err.status === 401) {
        location.href = '/login.html?next=' + encodeURIComponent(location.pathname + location.hash);
        return;
      }
      board.sayError($('message'), err, function () { load(page); });
    });
  }

  function select(name) {
    tab = TABS[name] ? name : 'posts';
    Array.prototype.forEach.call(document.querySelectorAll('#activity-tabs button'), function (b) {
      b.setAttribute('aria-selected', b.getAttribute('data-tab') === tab ? 'true' : 'false');
    });
    load(1);
  }

  Array.prototype.forEach.call(document.querySelectorAll('#activity-tabs button'), function (b) {
    b.addEventListener('click', function () { history.replaceState(null, '', '#' + b.getAttribute('data-tab')); select(b.getAttribute('data-tab')); });
  });
  // 계정 메뉴에서 다른 탭을 눌러 같은 화면으로 오면 해시만 바뀐다
  window.addEventListener('hashchange', function () { select(location.hash.substring(1)); });
  select(location.hash.substring(1));
})();
