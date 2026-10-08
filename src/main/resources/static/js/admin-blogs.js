// 관리자 블로그 탭 (005 T025, ADM-02, FR-011~FR-014, D-94, D-103 #6~#8).
// 검색·상태 거르기, 목록, 상세, 숨김·해제·강제 폐쇄. 모든 값은 textContent(D-64).
(function () {
  'use strict';
  var ui = window.adminUI;
  var global = window;
  var state = { q: '', status: '', page: 1 };

  function $(id) { return document.getElementById(id); }

  function search(page) {
    state.page = page || 1;
    var msg = $('blog-message');
    ui.message(msg, '불러오는 중…');
    api.get('/api/admin/blogs' + ui.query({ q: state.q, status: state.status, page: state.page }), { activity: true })
      .then(function (res) {
        ui.message(msg, '모두 ' + res.totalElements + '개');
        ui.table($('blog-list'), [
          { label: '번호', value: function (b) { return b.id; } },
          { label: '이름', value: function (b) { return b.name; } },
          { label: '주소', value: function (b) { return b.address; } },
          { label: '블로그장', value: function (b) { return ui.nick(b.owner); } },
          { label: '상태', value: function (b) { return ui.blogStatusChip(b); } },
          { label: '멤버', value: function (b) { return b.memberCount; } },
          { label: '글', value: function (b) { return b.postCount; } }
        ], res.items, function (b) { openDetail(b.id); }, '찾는 블로그가 없습니다.');
        ui.pager($('blog-pager'), res.page, res.totalPages, search);
      }, function (err) { ui.message(msg, ui.errorText(err), true); });
  }

  function openDetail(id) {
    var box = $('blog-detail');
    box.hidden = false;
    ui.clear(box);
    box.appendChild(ui.el('p', 'hint', '불러오는 중…'));
    api.get('/api/admin/blogs/' + id, { activity: true }).then(function (d) { renderDetail(box, d); },
      function (err) { ui.clear(box); box.appendChild(ui.el('p', 'message message--error', ui.errorText(err))); });
  }

  function act(promise, msg, id) {
    return promise.then(function () { search(state.page); openDetail(id); },
      function (err) { ui.message(msg, ui.errorText(err), true); });
  }

  function renderDetail(box, d) {
    ui.clear(box);
    box.appendChild(ui.el('h3', null, d.name + ' 블로그 상세'));
    var managers = d.managers.length ? d.managers.map(function (m) { return ui.nick(m.user); }).join(', ') : '-';
    box.appendChild(ui.dl([
      ['블로그 번호', d.id], ['주소', '/blog/' + d.address], ['블로그장', ui.nick(d.owner)],
      ['공개 범위', ui.labels.visibility[d.visibility] || d.visibility], ['상태', ui.blogStatusChip(d)],
      ['폐쇄 예정', d.closeScheduledAt ? ui.fmt(d.closeScheduledAt) : '-'],
      ['숨긴 시각', d.hiddenAt ? ui.fmt(d.hiddenAt) : '-'],
      ['부블로그장', managers], ['멤버 수', d.memberCount], ['글 수', d.postCount],
      ['블로그장 1년 안 경고', d.ownerActiveWarningCount + '번'],
      ['대기 중 위임', d.pendingDelegation ? '있음' : '없음'], ['만든 날', ui.fmt(d.createdAt)]
    ]));
    var msg = ui.el('p', 'message');
    var actions = ui.el('div', 'admin-actions');
    if (d.status !== 'CLOSED') {
      if (d.hidden) {
        actions.appendChild(ui.button('숨김 해제', function () {
          if (!ui.confirm('숨김을 풀면 목록·검색·피드에 다시 보입니다.')) { return; }
          act(api.del('/api/admin/blogs/' + d.id + '/hidden', null, { activity: true }), msg, d.id);
        }, 'button-small button-light'));
      } else {
        actions.appendChild(ui.button('숨기기', function () {
          if (!ui.confirm('블로그를 숨깁니다. 블로그장·멤버만 볼 수 있고 블로그장에게 알림이 갑니다.')) { return; }
          act(api.put('/api/admin/blogs/' + d.id + '/hidden', null, { activity: true }), msg, d.id);
        }, 'button-small'));
      }
      actions.appendChild(ui.button('강제 폐쇄', function () {
        var text = d.status === 'CLOSING'
          ? '이미 폐쇄 예정입니다. 예정 시각은 그대로 두고 관리자 폐쇄로 기록합니다.'
          : '7일 뒤 04:00에 폐쇄됩니다. 멤버와 구독자에게 바로 알림이 갑니다. 블로그장은 그 전까지 철회할 수 있습니다.';
        if (!ui.confirm(text)) { return; }
        api.post('/api/admin/blogs/' + d.id + '/force-close', null, { activity: true }).then(function (r) {
          search(state.page);
          openDetail(d.id);
          global.alert('폐쇄 예정: ' + ui.fmt(r.closeScheduledAt));
        }, function (err) { ui.message(msg, ui.errorText(err), true); });
      }, 'button-small button-danger'));
    }
    box.appendChild(actions);
    box.appendChild(msg);
    if (global.adminOwnerActions) { global.adminOwnerActions(box, d, function () { openDetail(d.id); }); }
  }


  document.addEventListener('DOMContentLoaded', function () {
    $('blog-search').addEventListener('submit', function (e) {
      e.preventDefault();
      state.q = $('blog-q').value.trim();
      state.status = $('blog-status').value;
      $('blog-detail').hidden = true;
      search(1);
    });
  });
  ui.registerTab('blogs', function () { search(state.page); });
  window.adminBlogs = { openDetail: openDetail };
})();
