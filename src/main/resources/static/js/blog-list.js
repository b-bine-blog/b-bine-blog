// 메인 블로그 목록·검색 (T035, BLG-02, BLG-03, FR-014, FR-015).
// 상태는 주소창 쿼리(q, sort, page, size)에 두어 새로고침·뒤로 가기에도 같은 화면을 보여 준다. 글자는 textContent로만 넣는다(D-64).
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var SIZES = ['10', '20', '30'];
  var params = new URLSearchParams(location.search);
  var state = {
    q: (params.get('q') || '').trim(),
    sort: params.get('sort') === 'popular' ? 'popular' : 'latest',
    page: Math.max(1, parseInt(params.get('page'), 10) || 1),
    size: SIZES.indexOf(params.get('size')) >= 0 ? params.get('size') : '10'
  };

  function go(changes) {
    Object.keys(changes).forEach(function (k) { state[k] = changes[k]; });
    var next = new URLSearchParams();
    if (state.q) { next.set('q', state.q); }
    if (state.sort !== 'latest') { next.set('sort', state.sort); }
    if (state.page > 1) { next.set('page', String(state.page)); }
    if (state.size !== '10') { next.set('size', state.size); }
    var qs = next.toString();
    history.pushState(null, '', qs ? '/?' + qs : '/');
    load();
  }

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) { node.className = className; }
    if (text != null) { node.textContent = text; }
    return node;
  }

  function renderItem(blog) {
    var li = el('li', 'blog-card');
    if (blog.coverImageUrl) {
      var img = el('img', 'blog-card__cover');
      img.src = blog.coverImageUrl;
      img.alt = '';
      img.loading = 'lazy';
      li.appendChild(img);
    }
    var body = el('div', 'blog-card__body');
    var a = el('a', 'blog-card__name', blog.name);
    a.href = '/blog/' + encodeURIComponent(blog.address);
    body.appendChild(a);
    if (blog.description) {
      body.appendChild(el('p', 'blog-card__desc', blog.description));
    }
    if (blog.tags && blog.tags.length) {
      var tags = el('p', 'blog-card__tags');
      blog.tags.forEach(function (t) { tags.appendChild(el('span', 'tag', '#' + t)); });
      body.appendChild(tags);
    }
    body.appendChild(el('p', 'blog-list__meta', '멤버 ' + blog.memberCount + '명'));
    li.appendChild(body);
    return li;
  }

  function renderPages(totalCount) {
    var nav = $('pages');
    nav.textContent = '';
    var last = Math.max(1, Math.ceil(totalCount / Number(state.size)));
    var start = Math.max(1, state.page - 4);
    var end = Math.min(last, start + 9);
    function button(label, page, current) {
      var b = el('button', current ? 'is-current' : '', label);
      b.type = 'button';
      b.disabled = current;
      if (current) { b.setAttribute('aria-current', 'page'); }
      b.addEventListener('click', function () { go({ page: page }); });
      nav.appendChild(b);
    }
    if (start > 1) { button('이전', start - 1, false); }
    for (var p = start; p <= end; p++) { button(String(p), p, p === state.page); }
    if (end < last) { button('다음', end + 1, false); }
  }

  function syncControls() {
    $('q').value = state.q;
    if ($('size')) { $('size').value = state.size; } // 메인 화면에는 블로그 개수 선택이 없다(10개 고정)
    document.querySelectorAll('[data-sort]').forEach(function (b) {
      var on = b.getAttribute('data-sort') === state.sort;
      b.classList.toggle('is-active', on);
      b.setAttribute('aria-selected', on ? 'true' : 'false');
    });
    api.setText($('result-title'), state.q ? '"' + state.q + '" 검색 결과' : '');
  }

  function load() {
    syncControls();
    api.setText($('message'), '');
    var qs = 'sort=' + state.sort + '&page=' + state.page + '&size=' + state.size;
    var url = state.q ? '/api/blogs/search?q=' + encodeURIComponent(state.q) + '&' + qs : '/api/blogs?' + qs;
    api.get(url).then(function (res) {
      var list = $('blogs');
      list.textContent = '';
      if (!res.items.length) {
        list.appendChild(el('li', 'blog-list__empty', state.q ? '검색 결과가 없습니다' : '아직 블로그가 없습니다'));
      }
      res.items.forEach(function (b) { list.appendChild(renderItem(b)); });
      renderPages(res.totalCount);
    }, function (err) {
      api.setText($('message'), err.status === 400 && state.q
        ? '검색어는 2~20자로, 글자나 숫자를 하나 이상 넣어 주세요.' : err.message);
    });
  }

  $('search-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var q = $('q').value.trim();
    if (q && (Array.from(q).length < 2 || Array.from(q).length > 20 || !/[\p{L}\p{N}]/u.test(q))) {
      api.setText($('message'), '검색어는 2~20자로, 글자나 숫자를 하나 이상 넣어 주세요.');
      return;
    }
    if (q) {
      location.href = '/search?q=' + encodeURIComponent(q); // 메인 검색창은 통합 검색으로(004 T045)
      return;
    }
    go({ q: q, page: 1 });
  });
  document.querySelectorAll('[data-sort]').forEach(function (b) {
    b.addEventListener('click', function () { go({ sort: b.getAttribute('data-sort'), page: 1 }); });
  });
  if ($('size')) {
    $('size').addEventListener('change', function () { go({ size: $('size').value, page: 1 }); });
  }
  window.addEventListener('popstate', function () { location.reload(); });

  load();
})();
