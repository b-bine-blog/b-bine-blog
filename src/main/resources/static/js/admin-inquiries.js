// 관리자 시스템 문의 탭 (ADM-08, D-112). 답변 대기·답변 완료 탭, 상세(내용은 textContent), 답변 입력·확인 창.
// 답변은 한 번만 달고 바꿀 수 없다. 회원에게는 알림, 비회원에게는 메일로 간다. 비회원 이메일은 서버가 가려서 준다(D-22).
(function () {
  'use strict';
  var ui = window.adminUI;
  var state = { status: 'PENDING', page: 1 };

  function $(id) { return document.getElementById(id); }

  function senderText(q) {
    if (q.sender) { return ui.nick(q.sender) + ' (회원)'; }
    return (q.maskedEmail || '-') + ' (비회원)';
  }

  // 탭 이름 옆 대기 건수. 어느 탭을 열어 두어도 보이게 처음에 한 번 부른다.
  function refreshCount() {
    api.get('/api/admin/inquiries' + ui.query({ status: 'PENDING', page: 1, size: 10 })).then(function (res) {
      var badge = $('inquiry-count');
      badge.hidden = res.totalElements === 0;
      badge.textContent = String(res.totalElements);
    }, function () { /* 건수를 못 받아도 탭은 쓸 수 있다 */ });
  }

  function search(page) {
    state.page = page || 1;
    var msg = $('inquiry-message');
    ui.message(msg, '불러오는 중…');
    api.get('/api/admin/inquiries' + ui.query({ status: state.status, page: state.page }), { activity: true })
      .then(function (res) {
        ui.message(msg, '모두 ' + res.totalElements + '건');
        var columns = [
          { label: '번호', value: function (q) { return q.id; } },
          { label: '제목', value: function (q) { return q.title; } },
          { label: '보낸 사람', value: senderText },
          { label: '접수', value: function (q) { return ui.fmt(q.createdAt); } }
        ];
        if (state.status === 'ANSWERED') {
          columns.push({ label: '답변', value: function (q) { return ui.fmt(q.answeredAt); } });
        }
        ui.table($('inquiry-list'), columns, res.items, function (q) { openDetail(q.id); },
          state.status === 'PENDING' ? '답변을 기다리는 문의가 없습니다.' : '답변한 문의가 없습니다.');
        ui.pager($('inquiry-pager'), res.page, res.totalPages, search);
      }, function (err) { ui.message(msg, ui.errorText(err), true); });
  }

  function openDetail(id) {
    var box = $('inquiry-detail');
    box.hidden = false;
    ui.clear(box);
    box.appendChild(ui.el('p', 'hint', '불러오는 중…'));
    api.get('/api/admin/inquiries/' + id, { activity: true }).then(function (d) { renderDetail(box, d); },
      function (err) { ui.clear(box); box.appendChild(ui.el('p', 'message message--error', ui.errorText(err))); });
  }

  function renderDetail(box, d) {
    ui.clear(box);
    box.appendChild(ui.el('h3', null, '문의 #' + d.id + ' ' + d.title));
    box.appendChild(ui.dl([
      ['보낸 사람', senderText(d)], ['접수', ui.fmt(d.createdAt)],
      ['상태', d.status === 'ANSWERED' ? '답변 완료' : '답변 대기']
    ]));
    box.appendChild(ui.el('pre', 'admin-pre', d.content));

    if (d.status === 'ANSWERED') {
      box.appendChild(ui.el('h3', null, '답변'));
      box.appendChild(ui.el('pre', 'admin-pre', d.answer));
      box.appendChild(ui.el('p', 'hint', ui.nick(d.answeredBy) + ' · ' + ui.fmt(d.answeredAt)
        + ' · 답변은 바꿀 수 없습니다.'));
      return;
    }
    var answer = ui.el('textarea');
    answer.id = 'inquiry-answer';
    answer.maxLength = 2000;
    answer.rows = 6;
    var label = ui.el('label', null, '답변 (2,000자 이하)');
    label.htmlFor = answer.id;
    box.appendChild(label);
    box.appendChild(answer);
    var msg = ui.el('p', 'message');
    var actions = ui.el('div', 'admin-actions');
    actions.appendChild(ui.button('답변 보내기', function () {
      var text = answer.value.trim();
      if (!text) { ui.message(msg, '답변을 적어 주세요.', true); return; }
      var where = d.sender ? '회원에게 알림으로 알립니다.' : '적어 준 이메일로 메일을 보냅니다.';
      if (!ui.confirm('답변은 바꿀 수 없습니다. ' + where)) { return; }
      api.post('/api/admin/inquiries/' + d.id + '/answer', { answer: text }, { activity: true })
        .then(function (res) { search(state.page); refreshCount(); renderDetail(box, res); },
          function (err) { ui.message(msg, ui.errorText(err), true); });
    }, 'button-small'));
    box.appendChild(actions);
    box.appendChild(msg);
  }

  document.addEventListener('DOMContentLoaded', function () {
    var tabs = $('inquiry-status-tabs').querySelectorAll('button');
    Array.prototype.forEach.call(tabs, function (t) {
      t.addEventListener('click', function () {
        Array.prototype.forEach.call(tabs, function (o) { o.setAttribute('aria-selected', String(o === t)); });
        state.status = t.getAttribute('data-status');
        $('inquiry-detail').hidden = true;
        search(1);
      });
    });
    refreshCount();
  });
  ui.registerTab('inquiries', function () { search(state.page); });
})();
