// 시스템 문의 화면 (ADM-08, D-112). 값은 textContent로만 넣는다(D-64).
// 비회원은 이메일 칸과 Turnstile 위젯(SEC-13, D-79)을 보이고, 회원은 아래에 내 문의와 답변을 보인다.
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var state = { member: false, captchaWidget: null, captchaToken: null, page: 1 };

  function show(text, isError) {
    api.setText($('message'), text);
    $('message').className = 'message ' + (isError ? 'message--error' : 'message--info');
  }

  function el(tag, className, text) {
    var e = document.createElement(tag);
    if (className) { e.className = className; }
    if (text !== undefined && text !== null) { e.textContent = String(text); }
    return e;
  }

  function fmt(value) {
    return value ? String(value).replace('T', ' ').substring(0, 16) : '';
  }

  function loadCaptcha() {
    return api.get('/api/public-config').then(function (config) {
      return new Promise(function (resolve, reject) {
        if (window.turnstile) { resolve(config.turnstileSiteKey); return; }
        var script = document.createElement('script');
        script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
        script.async = true;
        script.onload = function () { resolve(config.turnstileSiteKey); };
        script.onerror = reject;
        document.head.appendChild(script);
      });
    }).then(function (siteKey) {
      state.captchaWidget = window.turnstile.render('#captcha', {
        sitekey: siteKey,
        callback: function (token) { state.captchaToken = token; },
        'expired-callback': function () { state.captchaToken = null; }
      });
    }, function () {
      show('사람 확인을 불러오지 못했습니다. 새로고침해 주세요.', true);
    });
  }

  function resetCaptcha() {
    if (state.captchaWidget !== null && window.turnstile) {
      window.turnstile.reset(state.captchaWidget);
      state.captchaToken = null;
    }
  }

  function renderMine(page) {
    state.page = page || 1;
    api.get('/api/inquiries/mine?page=' + state.page).then(function (res) {
      var list = $('mine-list');
      while (list.firstChild) { list.removeChild(list.firstChild); }
      if (!res.items.length) {
        list.appendChild(el('li', 'blog-list__empty', '보낸 문의가 없습니다.'));
      }
      res.items.forEach(function (q) {
        var li = el('li', 'inquiry-item');
        var head = el('div', 'inquiry-item__head');
        head.appendChild(el('span', 'post-item__badge', q.status === 'ANSWERED' ? '답변 완료' : '답변 대기'));
        head.appendChild(el('strong', null, q.title));
        li.appendChild(head);
        li.appendChild(el('p', 'hint', fmt(q.createdAt)));
        li.appendChild(el('p', 'inquiry-item__body', q.content));
        if (q.answer) {
          var answer = el('div', 'inquiry-item__answer');
          answer.appendChild(el('p', 'hint', '관리자 답변 · ' + fmt(q.answeredAt)));
          answer.appendChild(el('p', 'inquiry-item__body', q.answer));
          li.appendChild(answer);
        }
        list.appendChild(li);
      });
      renderPager(res.page, res.totalPages);
      $('mine').hidden = false;
    }, function () { /* 내 문의를 못 불러와도 보내기는 쓸 수 있다 */ });
  }

  function renderPager(page, totalPages) {
    var nav = $('mine-pager');
    while (nav.firstChild) { nav.removeChild(nav.firstChild); }
    if (!totalPages || totalPages <= 1) { return; }
    for (var i = 1; i <= totalPages; i++) {
      (function (n) {
        var b = el('button', n === page ? 'is-current' : '', n);
        b.type = 'button';
        b.addEventListener('click', function () { renderMine(n); });
        nav.appendChild(b);
      })(i);
    }
  }

  function errorText(err) {
    switch (err.code) {
      case 'RATE_LIMITED': return '문의는 하루 5건까지 보낼 수 있습니다. 내일 다시 보내 주세요.';
      case 'CAPTCHA_REQUIRED': return '사람 확인을 해 주세요.';
      case 'CAPTCHA_FAILED': return '사람 확인에 실패했습니다. 다시 해 주세요.';
      case 'VALIDATION_FAILED':
        if (err.body && (err.body.fieldErrors || []).some(function (f) { return f.field === 'email'; })) { return '이메일 형식을 확인해 주세요.'; }
        return '제목(30자 이하)과 내용(2,000자 이하)을 적어 주세요.';
      default: return err.message || '보내지 못했습니다. 잠시 뒤 다시 시도해 주세요.';
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    $('content').addEventListener('input', function () {
      api.setText($('content-count'), $('content').value.length + ' / 2000');
    });
    api.get('/api/auth/session').then(function () {
      state.member = true;
      $('member-hint').hidden = false;
      renderMine(1);
    }, function () {
      $('guest-fields').hidden = false;
      loadCaptcha();
    });

    $('inquiry-form').addEventListener('submit', function (e) {
      e.preventDefault();
      var title = $('title').value.trim();
      var content = $('content').value.trim();
      if (!title || !content) { show('제목과 내용을 적어 주세요.', true); return; }
      var body = { title: title, content: content };
      if (!state.member) {
        body.email = $('email').value.trim();
        if (!body.email) { show('답변 받을 이메일을 적어 주세요.', true); return; }
        if (!state.captchaToken) { show('사람 확인을 해 주세요.', true); return; }
        body.captchaToken = state.captchaToken;
      }
      $('submit').disabled = true;
      api.post('/api/inquiries', body, { activity: true }).then(function () {
        $('submit').disabled = false;
        $('title').value = '';
        $('content').value = '';
        api.setText($('content-count'), '0 / 2000');
        resetCaptcha();
        show(state.member ? '문의를 보냈습니다. 답변이 달리면 알림으로 알려 드릴게요.'
          : '문의를 보냈습니다. 답변은 적어 주신 이메일로 보내 드릴게요.', false);
        if (state.member) { renderMine(1); }
      }, function (err) {
        $('submit').disabled = false;
        resetCaptcha();
        show(errorText(err), true);
      });
    });
  });
})();
