// 개인정보 처리방침: 운영팀 이메일은 서버 설정값(/api/site-rules의 inquiry.contactEmail, D-68)으로 채운다. textContent만 쓴다(D-64).
(function () {
  'use strict';
  var box = document.getElementById('contact-email');
  if (!box) { return; }
  api.get('/api/site-rules').then(function (rules) {
    var email = rules && rules.inquiry && rules.inquiry.contactEmail;
    if (!email) { return; }
    var a = document.createElement('a');
    a.href = 'mailto:' + email;
    a.textContent = email;
    box.textContent = '이메일 ';
    box.appendChild(a);
    box.appendChild(document.createTextNode(', '));
  }, function () { /* 못 받으면 시스템 문의 링크만 둔다 */ });
})();
