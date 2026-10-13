// 내 블로그 (T037, BLG-06, FR-016). 운영하는 블로그와 참여한 블로그를 나눠 보여 준다. 글자는 textContent로만 넣는다(D-64).
// 내 정보 화면 안에 함께 있다(D-117). 메인 관리자는 블로그 활동을 하지 않으므로 보여 주지 않는다(D-90).
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) { node.className = className; }
    if (text != null) { node.textContent = text; }
    return node;
  }

  function formatDate(iso) {
    var d = new Date(iso);
    return d.getFullYear() + '. ' + (d.getMonth() + 1) + '. ' + d.getDate() + '. '
      + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  function renderItem(blog) {
    var li = el('li');
    var a = el('a', 'blog-card__name', blog.name);
    a.href = '/blog/' + encodeURIComponent(blog.address);
    li.appendChild(a);
    if (blog.role === 'MANAGER') {
      li.appendChild(el('span', 'badge', '부블로그장'));
    }
    if (blog.status === 'CLOSING' && blog.closeScheduledAt) {
      li.appendChild(el('p', 'blog-list__meta', '폐쇄 예정 ' + formatDate(blog.closeScheduledAt)));
    }
    if (blog.role === 'OWNER' || blog.role === 'MANAGER') {
      var manage = el('a', 'blog-list__meta', '운영');
      manage.href = '/blog/' + encodeURIComponent(blog.address) + '/manage';
      li.appendChild(document.createTextNode(' '));
      li.appendChild(manage);
    }
    return li;
  }

  function fill(listId, items, emptyText) {
    var list = $(listId);
    list.textContent = '';
    if (!items.length) {
      list.appendChild(el('li', 'blog-list__empty', emptyText));
    }
    items.forEach(function (b) { list.appendChild(renderItem(b)); });
  }

  // 참여한 블로그는 개수 제한이 없어 한 번에 JOINED_PAGE_SIZE개씩 넘겨 본다(2026-10-13 사용자 요청)
  var JOINED_PAGE_SIZE = 5;
  function pageJoined(items) {
    var pages = Math.max(1, Math.ceil(items.length / JOINED_PAGE_SIZE));
    var page = 1;
    function show() {
      fill('joined', items.slice((page - 1) * JOINED_PAGE_SIZE, page * JOINED_PAGE_SIZE), '참여한 블로그가 없습니다');
      $('joined-page').textContent = page + ' / ' + pages;
      $('joined-prev').disabled = page === 1;
      $('joined-next').disabled = page === pages;
    }
    $('joined-pager').hidden = pages === 1;
    $('joined-prev').addEventListener('click', function () { if (page > 1) { page--; show(); } });
    $('joined-next').addEventListener('click', function () { if (page < pages) { page++; show(); } });
    show();
  }

  api.get('/api/auth/session').then(function (me) {
    if (me.admin) { return; }
    return api.get('/api/me/blogs').then(function (res) {
      fill('owned', res.owned, '운영하는 블로그가 없습니다');
      pageJoined(res.joined);
      $('blogs').hidden = false;
      if (location.hash === '#blogs') { $('blogs').scrollIntoView(); }
    });
  }).catch(function (err) {
    if (err.status === 401) { return; } // 로그인 안내는 내 정보(me.js)가 한다
    $('blogs').hidden = false;
    api.setText($('blogs-message'), err.message);
  });
})();
