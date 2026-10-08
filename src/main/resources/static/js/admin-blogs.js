// 관리자 블로그 탭 (005 T025·T032, ADM-02, ADM-07, FR-011~FR-014, FR-020~FR-029, D-94, D-103 #6~#8·#12~#17).
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
    renderOwner(box, d);
  }


  // 블로그장 경고(사유 필수, "지금 n번")와 바로 강퇴, 경고 이력 (T032)
  function renderOwner(box, d) {
    box.appendChild(ui.el('h3', null, '블로그장 경고·강퇴'));
    if (d.status !== 'CLOSED' && d.owner) {
      var label = ui.el('label', null, '경고 사유(필수, 500자까지)');
      var reason = ui.el('textarea');
      reason.id = 'owner-warning-reason';
      reason.maxLength = 500;
      reason.rows = 3;
      label.htmlFor = reason.id;
      box.appendChild(label);
      box.appendChild(reason);
      var msg = ui.el('p', 'message');
      var actions = ui.el('div', 'admin-actions');
      actions.appendChild(ui.button('경고 (지금 ' + d.ownerActiveWarningCount + '번)', function () {
        var text = reason.value.trim();
        if (!text) { ui.message(msg, '경고 사유를 적어 주세요.', true); return; }
        var next = d.ownerActiveWarningCount + 1;
        var warnText = ui.nick(d.owner) + ' 블로그장에게 경고합니다. 1년 안 경고가 ' + next + '번이 됩니다.'
          + ' 기준 횟수(기본 3번)가 되면 같은 처리에서 블로그장 권한이 박탈됩니다.';
        if (!ui.confirm(warnText)) { return; }
        api.post('/api/admin/blogs/' + d.id + '/owner-warnings', { ownerId: d.owner.id, reason: text }, { activity: true })
          .then(function () { search(state.page); openDetail(d.id); },
            function (err) { ui.message(msg, ui.errorText(err), true); });
      }, 'button-small'));
      actions.appendChild(ui.button('바로 강퇴', function () {
        var kickText = ui.nick(d.owner) + ' 블로그장의 권한을 박탈합니다. '
          + (d.managers.length ? '가장 먼저 된 부블로그장(' + ui.nick(d.managers[0].user) + ')이 수락 없이 블로그장이 됩니다.'
            : (d.status === 'CLOSING' ? '부블로그장이 없어 지금 폐쇄 예정 시각에 폐쇄됩니다.'
              : '부블로그장이 없어 7일 뒤 04:00에 폐쇄됩니다. 아무도 철회할 수 없습니다.'));
        if (!ui.confirm(kickText)) { return; }
        var text = reason.value.trim();
        api.post('/api/admin/blogs/' + d.id + '/owner-revocation', { ownerId: d.owner.id, reason: text || null },
          { activity: true }).then(function () { search(state.page); openDetail(d.id); },
          function (err) { ui.message(msg, ui.errorText(err), true); });
      }, 'button-small button-danger'));
      box.appendChild(actions);
      box.appendChild(msg);
    } else {
      box.appendChild(ui.el('p', 'hint', d.owner ? '폐쇄된 블로그입니다.' : '지금 블로그장이 없습니다.'));
    }
    var history = ui.el('div');
    box.appendChild(history);
    api.get('/api/admin/blogs/' + d.id + '/owner-warnings', { activity: true }).then(function (res) {
      ui.table(history, [
        { label: '시각', value: function (w) { return ui.fmt(w.createdAt); } },
        { label: '블로그장', value: function (w) { return ui.nick(w.owner); } },
        { label: '사유', value: function (w) { return w.reason || '-'; } },
        { label: '관리자', value: function (w) { return ui.nick(w.admin); } },
        { label: '셈', value: function (w) { return w.counted ? '1년 안' : '1년 지남'; } }
      ], res.warnings, null, '경고 이력이 없습니다.');
    }, function (err) { history.appendChild(ui.el('p', 'message message--error', ui.errorText(err))); });
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
