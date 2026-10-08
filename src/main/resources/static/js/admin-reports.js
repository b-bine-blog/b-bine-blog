// 관리자 신고 탭 (005 T036, ADM-04, FR-030~FR-034, D-85, D-102 #15·#16, D-103 #18).
// 대기·처리 탭, 상세(신고 당시 내용은 글자 그대로 textContent), 결과 선택·사유 입력·확인 창. 결과는 바꿀 수 없다.
(function () {
  'use strict';
  var ui = window.adminUI;
  var state = { status: 'PENDING', page: 1 };
  var reasonLabels = {};

  function $(id) { return document.getElementById(id); }

  function loadReasons() {
    return api.get('/api/reports/reasons').then(function (res) {
      (res.reasons || []).forEach(function (r) { reasonLabels[r.code] = r.label; });
    }, function () { /* 이름을 못 받으면 코드를 그대로 보여 준다 */ });
  }

  function reasonText(code) { return reasonLabels[code] || code; }

  function blogText(b) {
    if (!b) { return '메인 프로필'; }
    return (b.name || '정리된 블로그') + ' (#' + b.id + ')';
  }

  function search(page) {
    state.page = page || 1;
    var msg = $('report-message');
    ui.message(msg, '불러오는 중…');
    api.get('/api/admin/reports' + ui.query({ status: state.status, page: state.page }), { activity: true })
      .then(function (res) {
        ui.message(msg, '모두 ' + res.totalElements + '건');
        var columns = [
          { label: '번호', value: function (r) { return r.id; } },
          { label: '대상', value: function (r) { return (ui.labels.target[r.targetType] || r.targetType) + ' #' + r.targetId; } },
          { label: '블로그', value: function (r) { return blogText(r.blog); } },
          { label: '사유', value: function (r) { return reasonText(r.reason); } },
          { label: '접수', value: function (r) { return ui.fmt(r.createdAt); } }
        ];
        if (state.status === 'DONE') {
          columns.push({ label: '결과', value: function (r) { return ui.labels.reportResult[r.result] || r.result; } });
          columns.push({ label: '처리', value: function (r) { return ui.fmt(r.handledAt); } });
        }
        ui.table($('report-list'), columns, res.items, function (r) { openDetail(r.id); },
          state.status === 'PENDING' ? '대기 중인 신고가 없습니다.' : '처리한 신고가 없습니다.');
        ui.pager($('report-pager'), res.page, res.totalPages, search);
      }, function (err) { ui.message(msg, ui.errorText(err), true); });
  }

  function openDetail(id) {
    var box = $('report-detail');
    box.hidden = false;
    ui.clear(box);
    box.appendChild(ui.el('p', 'hint', '불러오는 중…'));
    api.get('/api/admin/reports/' + id, { activity: true }).then(function (d) { renderDetail(box, d); },
      function (err) { ui.clear(box); box.appendChild(ui.el('p', 'message message--error', ui.errorText(err))); });
  }

  function renderDetail(box, d) {
    ui.clear(box);
    box.appendChild(ui.el('h3', null, '신고 #' + d.id));
    box.appendChild(ui.dl([
      ['대상', (ui.labels.target[d.targetType] || d.targetType) + ' #' + d.targetId],
      ['블로그', d.blog ? blogText(d.blog) + ' · ' + (ui.labels.blogStatus[d.blog.status] || d.blog.status || '-') : '메인 프로필'],
      ['처리 대상', ui.nick(d.targetOwner) + (d.blog ? (d.targetOwnerIsCurrentOwner ? ' (지금 블로그장)' : ' (지금 블로그장 아님)') : '')],
      ['사유', reasonText(d.reason)], ['추가 설명', d.reasonDetail || '-'],
      ['신고자', ui.nick(d.reporter)], ['접수', ui.fmt(d.createdAt)],
      ['상태', d.status === 'DONE' ? '처리됨' : '대기 중']
    ]));
    box.appendChild(ui.el('h3', null, '신고 당시 내용'));
    box.appendChild(ui.el('pre', 'admin-pre', d.targetSnapshot));

    if (d.status === 'DONE') {
      box.appendChild(ui.el('p', null, '결과: ' + (ui.labels.reportResult[d.result] || d.result) + ' · '
        + ui.nick(d.handledBy) + ' · ' + ui.fmt(d.handledAt)));
      box.appendChild(ui.el('p', 'hint', '처리 결과는 바꿀 수 없습니다. 같은 대상의 새 신고는 따로 처리합니다.'));
      return;
    }
    box.appendChild(ui.el('h3', null, '처리'));
    var select = ui.el('select');
    select.id = 'report-result';
    var results = d.blog ? ['NONE', 'OWNER_WARNING', 'OWNER_KICK', 'BLOG_CLOSE'] : ['NONE'];
    results.forEach(function (r) {
      var o = ui.el('option', null, ui.labels.reportResult[r]);
      o.value = r;
      select.appendChild(o);
    });
    var selectLabel = ui.el('label', null, '결과');
    selectLabel.htmlFor = select.id;
    var reason = ui.el('textarea');
    reason.id = 'report-reason';
    reason.maxLength = 500;
    reason.rows = 3;
    var reasonLabel = ui.el('label', null, '사유(블로그장 경고는 필수, 강퇴는 선택)');
    reasonLabel.htmlFor = reason.id;
    box.appendChild(selectLabel);
    box.appendChild(select);
    box.appendChild(reasonLabel);
    box.appendChild(reason);
    if (!d.blog) { box.appendChild(ui.el('p', 'hint', '메인 프로필 회원 신고는 "문제 없음"으로만 닫습니다.')); }
    var msg = ui.el('p', 'message');
    var actions = ui.el('div', 'admin-actions');
    actions.appendChild(ui.button('처리하기', function () {
      var result = select.value;
      var text = reason.value.trim();
      if (result === 'OWNER_WARNING' && !text) { ui.message(msg, '경고 사유를 적어 주세요.', true); return; }
      var confirmText = {
        NONE: '문제 없음으로 닫습니다.',
        OWNER_WARNING: ui.nick(d.targetOwner) + ' 블로그장에게 경고합니다. 기준 횟수가 되면 권한이 박탈됩니다.',
        OWNER_KICK: ui.nick(d.targetOwner) + ' 블로그장의 권한을 박탈합니다.',
        BLOG_CLOSE: '블로그를 폐쇄합니다. 7일 뒤 04:00에 폐쇄되고(이미 폐쇄 예정이면 그 시각) 멤버·구독자에게 알림이 갑니다.'
      }[result] + ' 결과는 바꿀 수 없고 신고자에게 알림이 갑니다.';
      if (!ui.confirm(confirmText)) { return; }
      api.post('/api/admin/reports/' + d.id + '/resolution', { result: result, reason: text || null }, { activity: true })
        .then(function (res) { search(state.page); renderDetail(box, res); },
          function (err) { ui.message(msg, ui.errorText(err), true); });
    }, 'button-small'));
    box.appendChild(actions);
    box.appendChild(msg);
  }

  document.addEventListener('DOMContentLoaded', function () {
    var tabs = $('report-status-tabs').querySelectorAll('button');
    Array.prototype.forEach.call(tabs, function (t) {
      t.addEventListener('click', function () {
        Array.prototype.forEach.call(tabs, function (o) { o.setAttribute('aria-selected', String(o === t)); });
        state.status = t.getAttribute('data-status');
        $('report-detail').hidden = true;
        search(1);
      });
    });
  });
  ui.registerTab('reports', function () { loadReasons().then(function () { search(state.page); }); });
})();
