// 관리자 활동 기록 탭 (005 T049, ADM-06, FR-039, D-103 #20). 거르기(종류·관리자·대상·기간)와 최신순 목록.
// 고치거나 지우는 버튼은 없다. 모든 값은 textContent(D-64).
(function () {
  'use strict';
  var ui = window.adminUI;
  var state = { actionType: '', adminId: '', targetType: '', targetId: '', from: '', to: '', page: 1 };

  function $(id) { return document.getElementById(id); }

  function search(page) {
    state.page = page || 1;
    var msg = $('action-message');
    ui.message(msg, '불러오는 중…');
    api.get('/api/admin/actions' + ui.query({
      actionType: state.actionType, adminId: state.adminId, targetType: state.targetType,
      targetId: state.targetId, from: state.from, to: state.to, page: state.page
    }), { activity: true }).then(function (res) {
      ui.message(msg, '모두 ' + res.totalElements + '건');
      ui.table($('action-list'), [
        { label: '시각', value: function (a) { return ui.fmt(a.createdAt); } },
        { label: '관리자', value: function (a) { return ui.nick(a.admin); } },
        { label: '종류', value: function (a) { return ui.labels.action[a.actionType] || a.actionType; } },
        { label: '대상', value: function (a) {
          return (ui.labels.target[a.targetType] || a.targetType) + ' #' + a.targetId + ' · ' + a.targetLabel;
        } },
        { label: '신고', value: function (a) { return a.reportId ? '#' + a.reportId : '-'; } },
        { label: '메모', value: function (a) { return a.memo || '-'; } }
      ], res.items, null, '기록이 없습니다.');
      ui.pager($('action-pager'), res.page, res.totalPages, search);
    }, function (err) { ui.message(msg, ui.errorText(err), true); });
  }

  document.addEventListener('DOMContentLoaded', function () {
    var select = $('action-type');
    Object.keys(ui.labels.action).forEach(function (code) {
      var o = document.createElement('option');
      o.value = code;
      o.textContent = ui.labels.action[code];
      select.appendChild(o);
    });
    $('action-search').addEventListener('submit', function (e) {
      e.preventDefault();
      state.actionType = select.value;
      state.adminId = $('action-admin').value.trim();
      state.targetType = $('action-target-type').value;
      state.targetId = $('action-target-id').value.trim();
      state.from = $('action-from').value;
      state.to = $('action-to').value;
      search(1);
    });
  });
  ui.registerTab('actions', function () { search(state.page); });
})();
