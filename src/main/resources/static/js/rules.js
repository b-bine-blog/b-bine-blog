// 운영 규칙 화면 (D-114). 숫자는 서버 설정값(/api/site-rules, D-68)으로 채우고, 글자는 textContent로만 넣는다(D-64).
(function () {
  'use strict';

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) { node.className = className; }
    if (text != null) { node.textContent = text; }
    return node;
  }

  // 365일 단위는 "1년"처럼 읽기 쉽게 쓴다
  function period(days) {
    return days > 0 && days % 365 === 0 ? (days / 365) + '년' : days + '일';
  }

  function sections(r) {
    var a = r.account, b = r.blog, p = r.board;
    var notice = b.closingNoticeDays.map(function (d) { return d + '일 전'; }).join(', ');
    return [
      { title: '삭제와 보관', important: true, items: [
        '지운 글·댓글·사진은 바로 보이지 않고 ' + p.deletedPurgeDays + '일 동안 보관한 뒤 완전히 삭제됩니다. 그 사이에 잘못 지운 것은 관리자에게 복구를 요청할 수 있습니다.',
        '탈퇴하면 바로 로그인할 수 없고, 이메일·이름·전화번호·닉네임·프로필 사진은 ' + a.withdrawalDays + '일 뒤 삭제됩니다. 쓴 글은 "탈퇴한 회원"으로 남습니다.',
        '탈퇴하면 같은 이메일로 바로 다시 가입할 수 있지만, 예전 글과는 연결되지 않습니다.',
        '폐쇄된 블로그의 글·사진·카테고리는 ' + b.closedPurgeDays + '일 뒤 완전히 삭제되고, 블로그 주소는 다른 사람이 쓸 수 있게 됩니다.',
        '알림은 ' + r.notification.defaultDays + '일 동안 보관합니다. 내 정보에서 ' + r.notification.shortDays + '일로 줄이거나 한꺼번에 지울 수 있습니다.',
        '시스템 문의는 ' + period(r.inquiry.retentionDays) + ' 동안 보관한 뒤 삭제됩니다.',
        '완전 삭제는 매일 새벽 4시에 한꺼번에 처리합니다.'
      ] },
      { title: '블로그 폐쇄', important: true, items: [
        '블로그장이 폐쇄를 누르면 그날부터 ' + b.closingGraceDays + '일 뒤 새벽 ' + b.closingHour + '시에 폐쇄됩니다. 그동안에는 평소처럼 글을 쓸 수 있고 블로그장이 폐쇄를 취소할 수 있습니다.',
        '폐쇄가 정해지면 멤버와 구독자에게 바로, 그리고 폐쇄 ' + notice + '에 알림이 갑니다.',
        '블로그장인 블로그가 있으면 탈퇴할 수 없습니다. 블로그장을 넘기거나 폐쇄가 끝난 뒤에 탈퇴할 수 있습니다.'
      ] },
      { title: '블로그 만들기와 참여', items: [
        '한 사람이 공개 블로그는 ' + b.publicLimit + '개(일부 공개 포함), 비공개 블로그는 ' + b.privateLimit + '개까지 만들 수 있습니다.',
        '참여 신청이 거절되면 ' + b.joinReapplyDays + '일 뒤에 다시 신청할 수 있습니다.',
        '블로그장 넘기기 요청은 ' + b.delegationExpireDays + '일 안에 받지 않으면 취소됩니다.',
        '한 블로그에서 ' + period(b.sanctionWindowDays) + ' 안에 정지를 ' + b.sanctionKickCount + '번 받으면 블로그장이 강제 퇴장을 검토합니다.'
      ] },
      { title: '글과 댓글', items: [
        '글 제목은 ' + p.titleMaxLength + '자, 본문은 ' + p.contentMaxLength.toLocaleString('ko-KR') + '자(마크다운 기호 포함), 댓글은 ' + p.commentMaxLength + '자까지 쓸 수 있습니다.',
        '태그는 글 하나에 ' + p.tagMaxCount + '개까지 달 수 있습니다.',
        '사진은 한 장에 ' + p.imageMaxMegabytes + 'MB, 글 하나에 ' + p.imageMaxPerPost + '장까지 올릴 수 있습니다.',
        '쓰던 글은 이 브라우저에만 임시 저장됩니다. 다른 기기나 브라우저에서는 이어 쓸 수 없습니다.'
      ] },
      { title: '로그인과 계정 보안', items: [
        '"로그인 유지"를 체크하지 않으면 ' + a.idleMinutes + '분 동안 활동이 없거나 브라우저를 닫을 때 로그아웃됩니다. 체크하면 ' + a.rememberDays + '일 동안 유지됩니다.',
        '비밀번호를 ' + a.captchaAfter + '번 틀리면 사람 확인이 나오고, ' + a.lockAfter + '번 틀리면 ' + a.lockMinutes + '분 동안 로그인할 수 없습니다.',
        '가입 인증번호는 ' + a.signupCodeMinutes + '분, 비밀번호 재설정 인증번호는 ' + a.resetCodeMinutes + '분 뒤 만료되고, ' + a.codeMaxFail + '번 틀리면 쓸 수 없습니다.',
        '공용 컴퓨터에서는 "로그인 유지"를 체크하지 말고, 다 쓰면 로그아웃해 주세요.'
      ] },
      { title: '신고와 제재', items: [
        '같은 대상은 한 번 신고하면 ' + r.report.repeatAfterDays + '일 뒤에 다시 신고할 수 있습니다.',
        '블로그 안의 회원·글·댓글 신고는 그 블로그의 블로그장이, 블로그나 블로그장에 대한 신고는 메인 관리자가 처리합니다.',
        '블로그장이 관리자 경고를 ' + period(r.admin.warningWindowDays) + ' 안에 ' + r.admin.warningRevokeThreshold + '번 받으면 블로그장 권한을 잃습니다. 부블로그장이 있으면 넘어가고, 없으면 블로그가 폐쇄됩니다.',
        '경고·정지·신고 기록은 ' + period(r.admin.recordsRetentionDays) + ' 동안 보관합니다.'
      ] },
      { title: '시스템 문의', items: [
        '같은 곳(IP)에서 ' + (r.inquiry.rateWindowHours === 24 ? '하루' : r.inquiry.rateWindowHours + '시간') + '에 ' + r.inquiry.rateLimit + '건까지 보낼 수 있습니다.',
        '회원은 답변을 알림과 "내 문의"에서, 비회원은 적은 이메일로 받습니다.'
      ] }
    ];
  }

  function render(rules) {
    var box = document.getElementById('rules');
    box.textContent = '';
    sections(rules).forEach(function (s) {
      var section = el('section', 'panel rules-section' + (s.important ? ' is-important' : ''));
      section.appendChild(el('h2', null, s.title));
      var ul = el('ul', 'rules-list');
      s.items.forEach(function (t) { ul.appendChild(el('li', null, t)); });
      section.appendChild(ul);
      box.appendChild(section);
    });
  }

  api.get('/api/site-rules').then(render, function (err) {
    api.setText(document.getElementById('message'), err.message);
  });
})();
