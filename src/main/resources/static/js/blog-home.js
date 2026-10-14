// 블로그 홈 /blog/{주소} (T028, T036, T045, T071). 받은 위임 요청 응답(US6)도 여기서 한다.
// 정보 표시, 참여 신청·취소, 구독, 공유 링크, 정지 안내, 블랙리스트 문의. 값은 모두 textContent로 넣는다(D-64).
// 버튼을 보이고 숨기는 것은 편의일 뿐이고 권한은 서버가 요청마다 확인한다(FR-034).
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var VISIBILITY = { PUBLIC: '공개', LINK: '일부 공개', PRIVATE: '비공개' };
  var address = decodeURIComponent(location.pathname.split('/')[2] || '');
  var blog = null;
  var me = null;

  function say(text, isError) {
    var el = $('action-message');
    el.className = 'message ' + (isError ? 'message--error' : 'message--info');
    api.setText(el, text);
  }

  function show(id, visible) {
    $(id).hidden = !visible;
  }

  function formatTime(value) {
    return value ? value.replace('T', ' ').substring(0, 16) : '';
  }

  function render() {
    document.title = blog.name + ' - b-bine blog';
    api.setText($('name'), blog.name);
    api.setText($('description'), blog.description || '');
    api.setText($('owner'), blog.ownerNickname ? '블로그장 ' + blog.ownerNickname : '');
    api.setText($('member-count'), blog.memberCount);
    api.setText($('visibility'), VISIBILITY[blog.visibility]);
    // 대표 이미지가 있을 때만 그림 칸을 보여 준다. 파일이 없어 열리지 않으면 칸째 숨긴다(D-120)
    if (blog.coverImageUrl) {
      $('cover').onerror = function () { show('banner', false); };
      $('cover').src = blog.coverImageUrl;
      show('banner', true);
    } else {
      show('banner', false);
    }
    // 배경 이미지가 있으면 화면 뒤 전체에 깐다. 없거나 열리지 않으면 기본 배경(D-121)
    if (blog.backgroundImageUrl) {
      $('backdrop').onerror = function () { show('backdrop', false); document.body.classList.remove('has-backdrop'); };
      $('backdrop').src = blog.backgroundImageUrl;
      show('backdrop', true);
      document.body.classList.add('has-backdrop');
    } else {
      show('backdrop', false);
      document.body.classList.remove('has-backdrop');
    }
    var tags = $('tags');
    while (tags.firstChild) { tags.removeChild(tags.firstChild); }
    (blog.tags || []).forEach(function (t) {
      var li = document.createElement('li');
      li.textContent = '#' + t;
      tags.appendChild(li);
    });
    if (blog.status === 'CLOSING') {
      var reason = blog.closeReason === 'OWNER_REVOKED' ? ' (블로그장 권한 박탈로 인한 폐쇄 조치)' : '';
      api.setText($('closing'), '이 블로그는 ' + formatTime(blog.closeScheduledAt) + '에 폐쇄됩니다' + reason + '.');
      show('closing', true);
    } else {
      show('closing', false);
    }

    var loggedIn = !!me;
    var isMember = !!blog.myRole;
    var perms = blog.myPermissions || [];
    show('join', loggedIn && !isMember && !blog.myPendingJoinRequestId);
    show('cancel-join', loggedIn && !!blog.myPendingJoinRequestId);
    // 블로그장·메인 관리자에게는 구독 버튼을 보이지 않는다(003 T027, D-102 #1, D-90). 서버도 400·403으로 막는다
    var canSubscribe = loggedIn && blog.myRole !== 'OWNER' && !(me && me.admin);
    show('subscribe', canSubscribe && !blog.subscribed);
    show('unsubscribe', loggedIn && blog.subscribed);
    show('share', blog.shareable);
    show('leave', isMember && blog.myRole !== 'OWNER');
    // 블로그 신고는 메인 관리자가 받는다(003 T037, SOC-06). 내가 블로그장인 블로그와 관리자는 신고하지 않는다
    show('report', loggedIn && blog.myRole !== 'OWNER' && !(me && me.admin));
    var canManage = perms.length > 0;
    show('manage', canManage);
    if (canManage) {
      $('manage').href = '/blog/' + encodeURIComponent(blog.address) + '/manage';
    }
    show('blog', true);
    show('write', isMember && !(me && me.admin));
    $('write').href = '/blog/' + encodeURIComponent(blog.address) + '/write';
    loadDelegation();
    loadCategories();
    loadPosts(1);
  }

  // 글 목록과 카테고리 (004 T027). categoryId: '' 전체, '0' 잡담(카테고리 없음, D-132).
  var postQuery = { categoryId: '', size: 10 };

  function loadPosts(page) {
    var url = '/api/blogs/' + blog.id + '/posts?page=' + page + '&size=' + postQuery.size
      + (postQuery.categoryId !== '' ? '&categoryId=' + postQuery.categoryId : '');
    api.get(url).then(function (r) {
      board.renderPosts($('post-list'), r.items, { empty: '아직 글이 없습니다.' });
      board.renderPager($('post-pages'), r.page, r.totalPages, loadPosts);
    }, function (err) { board.sayError($('message'), err, function () { loadPosts(page); }); });
  }

  function loadCategories() {
    api.get('/api/blogs/' + blog.id + '/categories').then(function (r) {
      var box = $('categories');
      board.clear(box);
      function tab(label, value) {
        var b = board.el('button', null, label);
        b.type = 'button';
        b.setAttribute('role', 'tab');
        b.setAttribute('aria-selected', postQuery.categoryId === value ? 'true' : 'false');
        b.addEventListener('click', function () { postQuery.categoryId = value; loadCategories(); loadPosts(1); });
        box.appendChild(b);
      }
      if (r.categories.length === 0) { return; }
      tab('전체', '');
      r.categories.forEach(function (c) { tab(c.name + ' (' + c.postCount + ')', String(c.id)); });
      tab('잡담 (' + r.uncategorizedCount + ')', '0');
    }, function () { /* 카테고리를 못 읽어도 글 목록은 보인다 */ });
  }

  $('post-size').addEventListener('change', function () {
    postQuery.size = Number($('post-size').value);
    loadPosts(1);
  });

  // 나에게 온 위임 요청이 있으면 수락·거절을 보여 준다. 없으면 204(FR-040, FR-041).
  function loadDelegation() {
    show('delegation', false);
    if (!blog.myRole || blog.myRole === 'OWNER') {
      return;
    }
    api.get('/api/blogs/' + blog.id + '/delegation').then(function (d) {
      if (!d || !me || d.toUserId !== me.memberId) {
        return;
      }
      api.setText($('delegation-text'), d.fromNickname + '님이 블로그장 위임을 요청했습니다. '
        + formatTime(d.expiresAt) + '까지 응답할 수 있습니다.');
      show('delegation', true);
    }, function () { /* 요청이 없거나 볼 수 없으면 숨긴 채 둔다 */ });
  }

  function respondDelegation(action, doneText) {
    api.post('/api/blogs/' + blog.id + '/delegation/' + action, null, { activity: true })
      .then(function () { say(doneText); return load(); })
      .catch(function (err) { say(err.message, true); });
  }

  $('delegation-accept').addEventListener('click', function () {
    if (confirm('블로그장 권한을 넘겨받을까요?')) {
      respondDelegation('acceptance', '이제 이 블로그의 블로그장입니다.');
    }
  });

  $('delegation-reject').addEventListener('click', function () {
    respondDelegation('rejection', '위임 요청을 거절했습니다.');
  });

  function load() {
    return api.get('/api/blogs/address/' + encodeURIComponent(address))
      .then(function (data) { blog = data; render(); })
      .catch(function (err) {
        show('blog', false);
        if (err.code === 'BLOG_SUSPENDED') {
          var until = err.body.suspendedUntil ? formatTime(err.body.suspendedUntil) + '까지' : '영구';
          var text = '이 블로그에서 정지된 상태입니다(' + until + ').';
          if (err.body.reason) {
            text += ' 사유: ' + err.body.reason;
          }
          api.setText($('suspended'), text);
          show('suspended', true);
          return;
        }
        api.setText($('message'), err.status === 404 ? '블로그를 찾을 수 없습니다.' : err.message);
      });
  }

  // 공유 링크(?share=값)로 들어오면 서버가 확인하고 쿠키를 굽는다. 주소창에서는 값을 지운다(FR-026, FR-027).
  function enterWithShareLink() {
    var params = new URLSearchParams(location.search);
    var token = params.get('share');
    if (!token) {
      return Promise.resolve();
    }
    history.replaceState(null, '', location.pathname);
    return api.post('/api/blogs/share-access', { token: token }).catch(function () {
      api.setText($('message'), '공유 링크가 더 이상 유효하지 않습니다.');
    });
  }

  $('join').addEventListener('click', function () {
    api.post('/api/blogs/' + blog.id + '/join-requests', {}, { activity: true })
      .then(function (r) {
        if (r.status === 'APPROVED') {
          say('멤버가 되었습니다.');
        } else if (r.status === 'PENDING') {
          say('참여 신청을 보냈습니다. 블로그장의 승인을 기다려 주세요.');
        } else {
          say('참여 신청이 거절되었습니다.', true);
        }
        return load();
      })
      .catch(function (err) {
        if (err.code === 'BLOG_BLACKLISTED') {
          say(err.message, true);
          show('appeal-form', !!err.body.appealAllowed);
          return;
        }
        say(err.message, true);
      });
  });

  $('cancel-join').addEventListener('click', function () {
    api.del('/api/me/join-requests/' + blog.myPendingJoinRequestId, null, { activity: true })
      .then(function () { say('신청을 취소했습니다.'); return load(); })
      .catch(function (err) { say(err.message, true); });
  });

  $('subscribe').addEventListener('click', function () {
    api.post('/api/blogs/' + blog.id + '/subscription', null, { activity: true })
      .then(function () { say('구독했습니다.'); return load(); })
      .catch(function (err) { say(err.message, true); }); // 400·429 문구는 서버 문구 그대로
  });

  $('unsubscribe').addEventListener('click', function () {
    api.del('/api/blogs/' + blog.id + '/subscription', null, { activity: true })
      .then(load).catch(function (err) { say(err.message, true); });
  });

  $('report').addEventListener('click', function () {
    report.open({ targetType: 'BLOG', targetId: blog.id, label: blog.name });
  });

  $('share').addEventListener('click', function () {
    api.get('/api/blogs/' + blog.id + '/share-link', { activity: true })
      .then(function (link) {
        var url = location.origin + link.url;
        return navigator.clipboard.writeText(url).then(function () {
          say('공유 링크를 복사했습니다.');
        }, function () {
          say(url);
        });
      })
      .catch(function (err) { say(err.message, true); });
  });

  $('leave').addEventListener('click', function () {
    if (!confirm('이 블로그에서 나갈까요? 내가 쓴 글은 "탈퇴한 계정"으로 표시됩니다.')) {
      return;
    }
    api.del('/api/blogs/' + blog.id + '/members/me', null, { activity: true })
      .then(function () { say('블로그에서 나왔습니다.'); return load(); })
      .catch(function (err) { say(err.message, true); });
  });

  $('appeal-form').addEventListener('submit', function (e) {
    e.preventDefault();
    api.post('/api/blogs/' + blog.id + '/blacklist-appeals', { content: $('appeal-content').value },
      { activity: true })
      .then(function () { show('appeal-form', false); say('문의를 남겼습니다. 결과는 알림으로 알려 드립니다.'); })
      .catch(function (err) { say(err.message, true); });
  });

  document.addEventListener('DOMContentLoaded', function () {
    api.get('/api/auth/session').then(function (m) { me = m; }, function () { me = null; })
      .then(enterWithShareLink)
      .then(load);
  });
})();
