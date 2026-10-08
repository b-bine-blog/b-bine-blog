// 관리자 회원 탭 (005 T017, ADM-01, FR-005~FR-009, D-103 #2~#5).
// 이메일·전화번호는 서버가 가린 값만 온다. "전체 보기"는 상세 화면에서만 누를 수 있고 그 상세에만 원문을 보여 준다.
// 상세를 다시 열면 API를 새로 불러 다시 가린 값으로 돌아간다. 모든 값은 textContent(D-64).
(function () {
  'use strict';
  var ui = window.adminUI;
  var state = { type: 'NICKNAME', q: '', page: 1 };

  function $(id) { return document.getElementById(id); }

  function search(page) {
    state.page = page || 1;
    var msg = $('member-message');
    ui.message(msg, '불러오는 중…');
    api.get('/api/admin/members' + ui.query({ type: state.type, q: state.q, page: state.page }), { activity: true })
      .then(function (res) {
        ui.message(msg, '모두 ' + res.totalElements + '명');
        ui.table($('member-list'), [
          { label: '번호', value: function (m) { return m.id; } },
          { label: '닉네임', value: function (m) { return m.nickname; } },
          { label: '이름', value: function (m) { return m.name; } },
          { label: '이메일', value: function (m) { return m.maskedEmail; } },
          { label: '가입일', value: function (m) { return ui.fmt(m.joinedAt); } }
        ], res.items, function (m) { openDetail(m.id); }, '찾는 회원이 없습니다.');
        ui.pager($('member-pager'), res.page, res.totalPages, search);
      }, function (err) { ui.message(msg, ui.errorText(err), true); });
  }

  function openDetail(id) {
    var box = $('member-detail');
    box.hidden = false;
    ui.clear(box);
    box.appendChild(ui.el('p', 'hint', '불러오는 중…'));
    api.get('/api/admin/members/' + id, { activity: true }).then(function (d) { renderDetail(box, d); },
      function (err) { ui.clear(box); box.appendChild(ui.el('p', 'message message--error', ui.errorText(err))); });
  }

  function renderDetail(box, d) {
    ui.clear(box);
    box.appendChild(ui.el('h3', null, d.nickname + ' 회원 상세'));
    var email = ui.el('span', null, d.maskedEmail);
    var phone = ui.el('span', null, d.maskedPhone || '-');
    box.appendChild(ui.dl([
      ['회원 번호', d.id], ['닉네임', d.nickname], ['이름', d.name], ['이메일', email], ['전화번호', phone],
      ['가입일', ui.fmt(d.joinedAt)], ['상태', d.status === 'ACTIVE' ? '활동 중' : d.status]
    ]));
    var msg = ui.el('p', 'message');
    var view = ui.button('전체 보기', function () {
      if (!ui.confirm('원래 이메일과 전화번호를 봅니다. 누른 기록이 관리자 활동 기록에 남습니다.')) { return; }
      view.disabled = true;
      api.post('/api/admin/members/' + d.id + '/privacy-view', null, { activity: true }).then(function (p) {
        email.textContent = p.email;
        phone.textContent = p.phone || '-';
        ui.message(msg, '원래 값을 보여 줍니다. 상세를 다시 열면 다시 가려집니다.');
      }, function (err) { view.disabled = false; ui.message(msg, ui.errorText(err), true); });
    }, 'button-small button-light');
    var actions = ui.el('div', 'admin-actions');
    actions.appendChild(view);
    actions.appendChild(ui.button('다시 열기', function () { openDetail(d.id); }, 'button-small button-light'));
    box.appendChild(actions);
    box.appendChild(msg);

    box.appendChild(ui.el('h3', null, '운영·참여 블로그'));
    var blogs = ui.el('div');
    ui.table(blogs, [
      { label: '블로그', value: function (b) { return b.name + ' (#' + b.blogId + ')'; } },
      { label: '주소', value: function (b) { return b.address || '-'; } },
      { label: '역할', value: function (b) { return ui.labels.role[b.role] || b.role; } },
      { label: '공개 범위', value: function (b) { return ui.labels.visibility[b.visibility] || b.visibility; } },
      { label: '상태', value: function (b) { return ui.blogStatusChip(b); } }
    ], d.blogs, null, '참여한 블로그가 없습니다.');
    box.appendChild(blogs);

    box.appendChild(ui.el('h3', null, '블로그장 경고 이력(블로그별)'));
    if (!d.ownerWarnings.length) {
      box.appendChild(ui.el('p', 'hint', '받은 경고가 없습니다.'));
    }
    d.ownerWarnings.forEach(function (g) {
      box.appendChild(ui.el('p', null, g.blogName + ' (#' + g.blogId + ') — 1년 안 경고 ' + g.activeCount + '번'));
      var t = ui.el('div');
      ui.table(t, [
        { label: '시각', value: function (w) { return ui.fmt(w.createdAt); } },
        { label: '사유', value: function (w) { return w.reason || '-'; } },
        { label: '관리자', value: function (w) { return ui.nick(w.admin); } },
        { label: '셈', value: function (w) { return w.counted ? '1년 안' : '1년 지남'; } }
      ], g.warnings);
      box.appendChild(t);
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    $('member-search').addEventListener('submit', function (e) {
      e.preventDefault();
      state.type = $('member-type').value;
      state.q = $('member-q').value.trim();
      $('member-detail').hidden = true;
      search(1);
    });
  });
  ui.registerTab('members', function () { /* 검색어를 넣어야 찾는다 */ });
})();
