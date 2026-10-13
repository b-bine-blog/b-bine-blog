// 통합 검색 /search (004 T045, FR-052~FR-059, D-100 #16·#17). 상태는 주소(q, type, sort, size, page)에 둔다.
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var el = board.el;
  var params = new URLSearchParams(location.search);
  var state = {
    q: (params.get('q') || '').trim(), type: params.get('type') || 'ALL', sort: params.get('sort') || 'RELEVANCE',
    size: params.get('size') || '10', page: Number(params.get('page') || 1)
  };
  var loggedIn = false;

  function go(change) {
    Object.keys(change).forEach(function (k) { state[k] = change[k]; });
    var next = new URLSearchParams();
    next.set('q', state.q);
    next.set('type', state.type);
    next.set('sort', state.sort);
    next.set('size', state.size);
    next.set('page', String(state.page));
    history.pushState(null, '', '/search?' + next.toString());
    search();
  }

  function mark(attr, value) {
    document.querySelectorAll('[' + attr + ']').forEach(function (b) {
      b.setAttribute('aria-selected', b.getAttribute(attr) === value ? 'true' : 'false');
    });
  }

  function renderBlogs(list, items) {
    board.clear(list);
    items.forEach(function (b) {
      var li = el('li', 'post-item');
      var body = el('div', 'post-item__body');
      var a = el('a', 'post-item__title', b.name);
      a.href = '/blog/' + encodeURIComponent(b.address);
      body.appendChild(a);
      body.appendChild(el('div', 'post-item__meta', (b.description || '') + ' · 멤버 ' + b.memberCount + '명'));
      li.appendChild(body);
      if (b.coverImageUrl) {
        var img = el('img', 'post-item__thumb');
        img.addEventListener('error', function () { img.remove(); }); // 파일이 없는 표지는 뺀다
        img.src = b.coverImageUrl;
        img.alt = '';
        li.appendChild(img);
      }
      list.appendChild(li);
    });
  }

  function search() {
    $('q').value = state.q;
    $('size').value = state.size;
    mark('data-type', state.type);
    mark('data-sort', state.sort);
    $('blog-result').hidden = true;
    $('post-result').hidden = true;
    if (!state.q) { loadRecent(); return; }
    var url = '/api/search?q=' + encodeURIComponent(state.q) + '&type=' + state.type + '&sort=' + state.sort
      + '&size=' + state.size + '&page=' + state.page;
    board.say($('message'), '');
    api.get(url, { activity: true }).then(function (r) {
      var empty = true;
      if (r.blogs) {
        empty = empty && r.blogs.totalElements === 0;
        $('blog-result').hidden = r.blogs.totalElements === 0 && state.type === 'ALL';
        api.setText($('blog-count'), r.blogs.totalElements + '개');
        renderBlogs($('blog-list'), r.blogs.items);
        $('more-blogs').hidden = !(state.type === 'ALL' && r.blogs.totalElements > r.blogs.items.length);
        if (state.type === 'BLOG') {
          board.renderPager($('blog-pages'), state.page, r.blogs.totalPages, function (p) { go({ page: p }); });
        } else {
          board.clear($('blog-pages'));
        }
      }
      if (r.posts) {
        empty = empty && r.posts.totalElements === 0;
        $('post-result').hidden = false;
        api.setText($('post-count'), r.posts.totalElements + '개');
        board.renderPosts($('post-list'), r.posts.items, { showBlog: true, empty: '맞는 글이 없습니다.' });
        board.renderPager($('post-pages'), r.posts.page, r.posts.totalPages, function (p) { go({ page: p }); });
      }
      if (empty) { board.say($('message'), '검색 결과가 없습니다'); }
      loadRecent();
    }, function (err) {
      board.sayError($('message'), err.code === 'SEARCH_KEYWORD_INVALID'
        ? { message: '검색어는 2~20자로, 글자나 숫자를 하나 이상 넣어 주세요.' } : err, search);
    });
  }

  function loadRecent() {
    if (!loggedIn) { return; }
    api.get('/api/me/recent-searches').then(function (items) {
      var list = $('recent-list');
      board.clear(list);
      $('recent').hidden = items.length === 0;
      items.forEach(function (r) {
        var li = el('li');
        var a = el('a', null, r.keyword);
        a.href = '/search?q=' + encodeURIComponent(r.keyword);
        li.appendChild(a);
        var x = el('button', null, '×');
        x.type = 'button';
        x.setAttribute('aria-label', r.keyword + ' 지우기');
        x.addEventListener('click', function () {
          api.del('/api/me/recent-searches/' + r.id, null, { activity: true }).then(loadRecent);
        });
        li.appendChild(x);
        list.appendChild(li);
      });
    }, function () { $('recent').hidden = true; });
  }

  $('search-form').addEventListener('submit', function (e) {
    e.preventDefault();
    go({ q: $('q').value.trim(), page: 1 });
  });
  document.querySelectorAll('[data-type]').forEach(function (b) {
    b.addEventListener('click', function () { go({ type: b.getAttribute('data-type'), page: 1 }); });
  });
  document.querySelectorAll('[data-sort]').forEach(function (b) {
    b.addEventListener('click', function () { go({ sort: b.getAttribute('data-sort'), page: 1 }); });
  });
  $('size').addEventListener('change', function () { go({ size: $('size').value, page: 1 }); });
  $('more-blogs').addEventListener('click', function () { go({ type: 'BLOG', page: 1 }); });
  $('recent-clear').addEventListener('click', function () {
    api.del('/api/me/recent-searches', null, { activity: true }).then(loadRecent);
  });
  window.addEventListener('popstate', function () { location.reload(); });

  api.get('/api/auth/session').then(function () { loggedIn = true; }, function () { loggedIn = false; })
    .then(search);
})();
