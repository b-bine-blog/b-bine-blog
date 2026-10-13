// 내 차단 목록 (003 T044, SOC-05, FR-016, D-102 #10). 닉네임·사진·차단한 날짜, 해제, 번호 페이지.
// 글자는 textContent로만 넣는다(D-64).
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var el = board.el;
  var current = 1;

  function say(text, isError) { board.say($('message'), text, isError); }

  function item(u) {
    var li = el('li', 'user-item');
    if (u.profileImageUrl) {
      var img = el('img', 'user-item__avatar');
      img.src = u.profileImageUrl;
      img.alt = '';
      li.appendChild(img);
    } else {
      li.appendChild(el('span', 'user-item__avatar user-item__avatar--empty'));
    }
    var a = el('a', 'user-item__name', u.nickname);
    a.href = '/profile/' + u.id;
    li.appendChild(a);
    li.appendChild(el('span', 'hint', String(u.blockedAt || '').substring(0, 10) + ' 차단'));
    var b = el('button', 'button-quiet', '차단 해제');
    b.type = 'button';
    b.addEventListener('click', function () {
      if (!confirm(u.nickname + '님 차단을 풀까요? 끊긴 팔로우는 다시 생기지 않습니다.')) { return; }
      api.del('/api/users/' + u.id + '/block', null, { activity: true }).then(function () {
        say('차단을 풀었습니다.');
        load(current);
      }, function (err) { say(err.message, true); });
    });
    li.appendChild(b);
    return li;
  }

  function load(page) {
    api.get('/api/me/blocks?page=' + page, { activity: true }).then(function (r) {
      if (r.items.length === 0 && page > 1) { load(page - 1); return; }
      current = page;
      var list = $('block-list');
      board.clear(list);
      if (r.items.length === 0) { list.appendChild(el('li', 'blog-list__empty', '차단한 회원이 없습니다.')); }
      r.items.forEach(function (u) { list.appendChild(item(u)); });
      board.renderPager($('block-pages'), r.page, r.totalPages, load);
      $('blocks').hidden = false;
    }, function (err) {
      if (err.status === 401) {
        location.href = '/login.html?next=' + encodeURIComponent(location.pathname);
        return;
      }
      board.sayError($('message'), err, function () { load(page); });
    });
  }

  load(1);
})();
