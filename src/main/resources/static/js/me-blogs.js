// 내 블로그 (T037, BLG-06, FR-016). 운영하는 블로그와 참여한 블로그를 나눠 보여 준다. 글자는 textContent로만 넣는다(D-64).
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

  api.get('/api/me/blogs').then(function (res) {
    fill('owned', res.owned, '운영하는 블로그가 없습니다');
    fill('joined', res.joined, '참여한 블로그가 없습니다');
  }, function (err) {
    if (err.status === 401) {
      location.href = '/login.html?next=' + encodeURIComponent('/me/blogs.html');
      return;
    }
    api.setText($('message'), err.message);
  });

  // 받은 위임 요청(US6)이 있으면 위에 보여 준다. 기능이 없거나 실패하면 칸을 숨긴 채 둔다.
  if (window.meDelegations) {
    window.meDelegations.render($('delegations-section'), $('delegations'));
  }
})();
