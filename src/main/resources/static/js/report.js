// 신고 창 (003 T037, SOC-06, FR-022~FR-025, D-93, D-102 #14). 글·댓글·블로그·프로필 화면이 같이 쓴다.
// 사유는 서버 설정값(/api/reports/reasons)을 그대로 쓴다. 모든 글자는 textContent로 넣는다(D-64).
// 접수처(블로그장·메인 관리자)는 서버가 신고하는 순간의 역할로 정하고, 응답의 route로 안내한다.
(function (global) {
  'use strict';

  var reasons = null;
  var dialog, form, title, reasonBox, detail, counter, message, submit;
  var target = null;

  function el(tag, className, text) {
    var e = document.createElement(tag);
    if (className) { e.className = className; }
    if (text !== undefined && text !== null) { e.textContent = String(text); }
    return e;
  }

  function say(text, isError) {
    message.className = 'message ' + (isError ? 'message--error' : 'message--info');
    message.textContent = text || '';
  }

  function formatDate(value) {
    return value ? String(value).replace('T', ' ').substring(0, 16) : '';
  }

  function build() {
    dialog = el('div', 'report-dialog');
    dialog.setAttribute('role', 'dialog');
    dialog.setAttribute('aria-modal', 'true');
    dialog.setAttribute('aria-labelledby', 'report-title');
    dialog.hidden = true;
    form = el('form', 'panel report-dialog__box');
    form.noValidate = true;
    title = el('h2', null, '신고하기');
    title.id = 'report-title';
    form.appendChild(title);
    form.appendChild(el('p', 'hint', '블로그 안의 글·댓글·멤버는 그 블로그의 블로그장에게, 블로그·블로그장·메인 프로필 신고는 '
      + '메인 관리자에게 접수됩니다. 신고한 사람은 알리지 않습니다.'));
    var fieldset = el('fieldset', 'report-dialog__reasons');
    fieldset.appendChild(el('legend', null, '사유'));
    reasonBox = el('div');
    fieldset.appendChild(reasonBox);
    form.appendChild(fieldset);
    var label = el('label', null, '설명');
    label.htmlFor = 'report-detail';
    form.appendChild(label);
    detail = el('textarea');
    detail.id = 'report-detail';
    detail.rows = 3;
    form.appendChild(detail);
    counter = el('div', 'counter');
    form.appendChild(counter);
    message = el('p', 'message');
    message.setAttribute('role', 'alert');
    form.appendChild(message);
    var row = el('div', 'row');
    submit = el('button', null, '신고');
    submit.type = 'submit';
    var cancel = el('button', 'button-quiet', '닫기');
    cancel.type = 'button';
    cancel.addEventListener('click', close);
    row.appendChild(submit);
    row.appendChild(cancel);
    form.appendChild(row);
    dialog.appendChild(form);
    document.body.appendChild(dialog);

    detail.addEventListener('input', updateCounter);
    form.addEventListener('submit', send);
    dialog.addEventListener('keydown', function (e) { if (e.key === 'Escape') { close(); } });
  }

  function updateCounter() {
    var max = reasons ? reasons.detailMaxLength : 500;
    counter.textContent = Array.from(detail.value).length + '/' + max;
  }

  function renderReasons() {
    while (reasonBox.firstChild) { reasonBox.removeChild(reasonBox.firstChild); }
    reasons.reasons.forEach(function (r, i) {
      var label = el('label', 'inline report-dialog__reason');
      var input = el('input');
      input.type = 'radio';
      input.name = 'report-reason';
      input.value = r.code;
      input.checked = i === 0;
      label.appendChild(input);
      label.appendChild(document.createTextNode(' ' + r.label + (r.detailRequired ? ' (설명 필수)' : '')));
      reasonBox.appendChild(label);
    });
    detail.maxLength = reasons.detailMaxLength;
    updateCounter();
  }

  function loadReasons() {
    if (reasons) { return Promise.resolve(reasons); }
    return api.get('/api/reports/reasons', { activity: true }).then(function (r) { reasons = r; return r; });
  }

  function open(t) {
    if (!dialog) { build(); }
    target = t;
    title.textContent = (t.label ? t.label + ' ' : '') + '신고하기';
    detail.value = '';
    say('');
    submit.disabled = false;
    dialog.hidden = false;
    loadReasons().then(function () {
      renderReasons();
      var first = reasonBox.querySelector('input');
      if (first) { first.focus(); }
    }, function (err) {
      if (err.status === 401) {
        location.href = '/login.html?next=' + encodeURIComponent(location.pathname);
        return;
      }
      say(err.message, true);
    });
  }

  function close() {
    if (dialog) { dialog.hidden = true; }
  }

  function send(e) {
    e.preventDefault();
    var checked = reasonBox.querySelector('input:checked');
    if (!checked) { say('사유를 골라 주세요.', true); return; }
    var body = { targetType: target.targetType, targetId: target.targetId, reason: checked.value,
      detail: detail.value.trim() || null };
    if (target.blogId) { body.blogId = target.blogId; }
    submit.disabled = true;
    api.post('/api/reports', body, { activity: true }).then(function (r) {
      say(r.route === 'BLOG' ? '블로그장에게 접수되었습니다.' : '메인 관리자에게 접수되었습니다.');
      setTimeout(close, 1500);
    }, function (err) {
      submit.disabled = false;
      if (err.code === 'REPORT_TOO_SOON') {
        say(err.message + ' ' + formatDate(err.body.reportableAt) + '부터 다시 신고할 수 있습니다.', true);
      } else if (err.code === 'VALIDATION_FAILED' && err.body.fieldErrors && err.body.fieldErrors.length) {
        var f = err.body.fieldErrors[0].field;
        say(f === 'detail' ? '설명을 확인해 주세요. 기타 사유는 설명이 필요하고 '
          + (reasons ? reasons.detailMaxLength : 500) + '자까지 쓸 수 있습니다.' : '신고할 수 없는 대상입니다.', true);
      } else {
        say(err.message, true);
      }
    });
  }

  global.report = { open: open, close: close };
})(window);
