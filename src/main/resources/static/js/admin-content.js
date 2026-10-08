// 관리자 글·댓글 탭 (005 T044, ADM-03, FR-015~FR-019, D-103 #9~#11).
// 블로그·제목·상태로 거르고, 본문은 마크다운 원문을 글자로만 보여 준다(textContent, D-64). 숨김·해제·삭제·복구 버튼.
(function () {
  'use strict';
  var ui = window.adminUI;
  var state = { blogId: '', q: '', status: '', page: 1 };

  function $(id) { return document.getElementById(id); }

  function statusText(item) {
    var s = ui.labels.contentStatus[item.status] || item.status;
    if (item.status === 'DELETED' && item.deletedBy) {
      s += ' (' + (ui.labels.deletedBy[item.deletedBy] || item.deletedBy) + ')';
    }
    return s;
  }

  function search(page) {
    state.page = page || 1;
    var msg = $('post-message');
    ui.message(msg, '불러오는 중…');
    api.get('/api/admin/posts' + ui.query({ blogId: state.blogId, q: state.q, status: state.status, page: state.page }),
      { activity: true }).then(function (res) {
      ui.message(msg, '모두 ' + res.totalElements + '개');
      ui.table($('post-list'), [
        { label: '번호', value: function (p) { return p.id; } },
        { label: '블로그', value: function (p) { return p.blog ? p.blog.name + ' (#' + p.blog.id + ')' : '-'; } },
        { label: '제목', value: function (p) { return p.title; } },
        { label: '작성자', value: function (p) { return ui.nick(p.author); } },
        { label: '상태', value: statusText },
        { label: '쓴 날', value: function (p) { return ui.fmt(p.createdAt); } }
      ], res.items, function (p) { openDetail(p.id); }, '찾는 글이 없습니다.');
      ui.pager($('post-pager'), res.page, res.totalPages, search);
    }, function (err) { ui.message(msg, ui.errorText(err), true); });
  }

  function openDetail(id) {
    var box = $('post-detail');
    box.hidden = false;
    ui.clear(box);
    box.appendChild(ui.el('p', 'hint', '불러오는 중…'));
    api.get('/api/admin/posts/' + id, { activity: true }).then(function (d) { renderDetail(box, d); },
      function (err) { ui.clear(box); box.appendChild(ui.el('p', 'message message--error', ui.errorText(err))); });
  }

  function run(promise, msg, after) {
    promise.then(function () { search(state.page); after(); },
      function (err) { ui.message(msg, ui.errorText(err), true); });
  }

  function renderDetail(box, d) {
    ui.clear(box);
    box.appendChild(ui.el('h3', null, d.title));
    box.appendChild(ui.dl([
      ['글 번호', d.id], ['블로그', d.blog ? d.blog.name + ' (#' + d.blog.id + ')' : '-'],
      ['작성자', ui.nick(d.author)], ['상태', statusText(d)], ['쓴 날', ui.fmt(d.createdAt)],
      ['숨긴 시각', d.hiddenAt ? ui.fmt(d.hiddenAt) : '-'], ['지운 시각', d.deletedAt ? ui.fmt(d.deletedAt) : '-']
    ]));
    box.appendChild(ui.el('pre', 'admin-pre', d.content));
    var msg = ui.el('p', 'message');
    var actions = ui.el('div', 'admin-actions');
    var reopen = function () { openDetail(d.id); };
    if (d.status === 'VISIBLE') {
      actions.appendChild(ui.button('숨기기', function () {
        if (!ui.confirm('글을 숨깁니다. 작성자에게만 "관리자가 숨긴 글"로 보이고 작성자에게 알림이 갑니다.')) { return; }
        run(api.put('/api/admin/posts/' + d.id + '/hidden', null, { activity: true }), msg, reopen);
      }, 'button-small'));
    }
    if (d.status === 'HIDDEN') {
      actions.appendChild(ui.button('숨김 해제', function () {
        if (!ui.confirm('숨김을 풀어 다시 보이게 합니다.')) { return; }
        run(api.del('/api/admin/posts/' + d.id + '/hidden', null, { activity: true }), msg, reopen);
      }, 'button-small button-light'));
    }
    if (d.status !== 'DELETED') {
      actions.appendChild(ui.button('삭제', function () {
        if (!ui.confirm('글을 지웁니다. 30일 동안 보관한 뒤 완전히 지워지고 작성자에게 알림이 갑니다.')) { return; }
        run(api.del('/api/admin/posts/' + d.id, null, { activity: true }), msg, reopen);
      }, 'button-small button-danger'));
    }
    if (d.restorable) {
      actions.appendChild(ui.button('복구', function () {
        if (!ui.confirm('글을 복구합니다. 작성자에게 알림이 갑니다.')) { return; }
        run(api.post('/api/admin/posts/' + d.id + '/restore', null, { activity: true }), msg, reopen);
      }, 'button-small'));
    } else if (d.status === 'DELETED') {
      box.appendChild(ui.el('p', 'hint', d.deletedBy === 'AUTHOR' ? '작성자가 직접 지운 글은 복구하지 않습니다.'
        : '복구할 수 없습니다(폐쇄된 블로그이거나 보관 기간이 지났습니다).'));
    }
    box.appendChild(actions);
    box.appendChild(msg);

    box.appendChild(ui.el('h3', null, '댓글'));
    var list = ui.el('div');
    box.appendChild(list);
    loadComments(list, d.id, 1);
  }

  function loadComments(container, postId, page) {
    api.get('/api/admin/comments' + ui.query({ postId: postId, page: page }), { activity: true }).then(function (res) {
      ui.clear(container);
      var msg = ui.el('p', 'message');
      var reload = function () { loadComments(container, postId, page); };
      ui.table(container, [
        { label: '번호', value: function (c) { return c.id + (c.parentId ? ' (답글)' : ''); } },
        { label: '작성자', value: function (c) { return ui.nick(c.author); } },
        { label: '내용', value: function (c) { return c.content; } },
        { label: '상태', value: statusText },
        { label: '조치', value: function (c) { return commentActions(c, msg, reload); } }
      ], res.items, null, '댓글이 없습니다.');
      container.appendChild(msg);
      var pager = ui.el('div', 'pagination');
      container.appendChild(pager);
      ui.pager(pager, res.page, res.totalPages, function (p) { loadComments(container, postId, p); });
    }, function (err) { ui.clear(container); container.appendChild(ui.el('p', 'message message--error', ui.errorText(err))); });
  }

  function commentActions(c, msg, reload) {
    var wrap = ui.el('span', 'row');
    var base = '/api/admin/comments/' + c.id;
    function add(text, confirmText, call, cls) {
      wrap.appendChild(ui.button(text, function () {
        if (!ui.confirm(confirmText)) { return; }
        call().then(reload, function (err) { ui.message(msg, ui.errorText(err), true); });
      }, cls || 'button-small button-light'));
    }
    if (c.status === 'VISIBLE') {
      add('숨기기', '댓글을 숨깁니다. 작성자에게 알리지 않습니다.', function () { return api.put(base + '/hidden', null, { activity: true }); });
    }
    if (c.status === 'HIDDEN') {
      add('해제', '숨김을 풉니다.', function () { return api.del(base + '/hidden', null, { activity: true }); });
    }
    if (c.status !== 'DELETED') {
      add('삭제', '댓글을 지웁니다. 30일 동안 보관합니다.', function () { return api.del(base, null, { activity: true }); },
        'button-small button-danger');
    }
    if (c.restorable) {
      add('복구', '댓글을 복구합니다.', function () { return api.post(base + '/restore', null, { activity: true }); });
    }
    return wrap;
  }

  document.addEventListener('DOMContentLoaded', function () {
    $('post-search').addEventListener('submit', function (e) {
      e.preventDefault();
      state.blogId = $('post-blog').value.trim();
      state.q = $('post-q').value.trim();
      state.status = $('post-status').value;
      $('post-detail').hidden = true;
      search(1);
    });
  });
  ui.registerTab('content', function () { search(state.page); });
})();
