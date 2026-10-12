// 회원 프로필 /profile/{회원 번호} (003 T019·T026·T037·T040·T044, SOC-01~03·05·06).
// 프로필·팔로우·차단·신고·팔로워/팔로잉 목록. 모든 값은 textContent로 넣는다(D-64). 소개 줄바꿈은 CSS(pre-line)로 보인다.
// 버튼을 보이고 숨기는 것은 편의이고, 관리자·자기 자신·차단 확인은 서버가 요청마다 한다.
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var el = board.el;
  var userId = location.pathname.split('/')[2];
  var me = null;
  var profile = null;
  var listKind = 'followers';

  function say(text, isError) { board.say($('action-message'), text, isError); }

  function load() {
    return api.get('/api/users/' + encodeURIComponent(userId) + '/profile').then(function (p) {
      profile = p;
      render();
    }, function (err) {
      $('profile').hidden = true;
      board.sayError($('message'), err.status === 404 ? { message: '회원을 찾을 수 없습니다.' } : err, load);
    });
  }

  function render() {
    document.title = profile.nickname + ' - b-bine blog';
    api.setText($('nickname'), profile.nickname);
    api.setText($('bio'), profile.bio || '');
    $('bio').hidden = !profile.bio;
    if (profile.profileImageUrl) {
      $('avatar').src = profile.profileImageUrl;
      $('avatar').alt = profile.nickname + '의 프로필 사진';
      $('avatar').hidden = false;
      $('avatar-empty').hidden = true;
    }
    api.setText($('follower-count'), '팔로워 ' + profile.followerCount + '명');
    renderBlogs($('owned-blogs'), profile.ownedBlogs, '운영하는 블로그가 없습니다.');
    renderBlogs($('joined-blogs'), profile.joinedBlogs, '참여한 블로그가 없습니다.');

    var v = profile.viewer;
    var self = !!(v && v.self);
    var canAct = !!(me && !me.admin && v && !self); // 관리자는 팔로우·차단·신고를 하지 않는다(D-90)
    $('follow').hidden = !(canAct && !v.following && !v.blocking);
    $('unfollow').hidden = !(canAct && v.following);
    $('block').hidden = !(canAct && !v.blocking);
    $('unblock').hidden = !(canAct && v.blocking);
    $('report').hidden = !canAct;
    $('edit-profile').hidden = !self;
    $('profile').hidden = false;
  }

  function renderBlogs(list, blogs, empty) {
    board.clear(list);
    if (!blogs || blogs.length === 0) {
      list.appendChild(el('li', 'blog-list__empty', empty));
      return;
    }
    blogs.forEach(function (b) {
      var li = el('li');
      var a = el('a', 'blog-card__name', b.name);
      a.href = '/blog/' + encodeURIComponent(b.address);
      li.appendChild(a);
      var meta = [];
      if (b.visibility === 'LINK') { meta.push('일부 공개'); }
      if (b.visibility === 'PRIVATE') { meta.push('비공개'); }
      if (b.status === 'CLOSING') { meta.push('폐쇄 예정'); }
      if (meta.length) { li.appendChild(el('div', 'blog-list__meta', meta.join(' · '))); }
      list.appendChild(li);
    });
  }

  function followRequest(method) {
    var url = '/api/users/' + profile.id + '/follow';
    var req = method === 'POST' ? api.post(url, null, { activity: true }) : api.del(url, null, { activity: true });
    req.then(function (r) {
      profile.viewer.following = r.following;
      profile.followerCount = r.followerCount;
      render();
      say(r.following ? '팔로우했습니다.' : '팔로우를 취소했습니다.');
    }, function (err) { say(err.message, true); }); // 429·403 문구는 서버 문구 그대로
  }

  $('follow').addEventListener('click', function () { followRequest('POST'); });
  $('unfollow').addEventListener('click', function () { followRequest('DELETE'); });

  $('block').addEventListener('click', function () {
    if (!confirm(profile.nickname + '님을 차단할까요? 서로의 팔로우가 끊기고, 이 회원의 글과 댓글이 보이지 않습니다. '
      + '같은 블로그 멤버라면 그 블로그 안에서는 보입니다.')) { return; }
    api.post('/api/users/' + profile.id + '/block', null, { activity: true }).then(function () {
      say('차단했습니다. 내 정보 > 차단 목록에서 풀 수 있습니다.');
      return load();
    }, function (err) { say(err.message, true); });
  });

  $('unblock').addEventListener('click', function () {
    if (!confirm('차단을 풀까요? 끊긴 팔로우는 다시 생기지 않습니다.')) { return; }
    api.del('/api/users/' + profile.id + '/block', null, { activity: true }).then(function () {
      say('차단을 풀었습니다.');
      return load();
    }, function (err) { say(err.message, true); });
  });

  // 메인 프로필 회원 신고는 메인 관리자에게 접수된다(blogId 없음, D-93)
  $('report').addEventListener('click', function () {
    report.open({ targetType: 'USER', targetId: profile.id, label: profile.nickname + '님' });
  });

  // 팔로워·팔로잉 목록
  function openList(kind) {
    listKind = kind;
    $('follow-lists').hidden = false;
    document.querySelectorAll('[data-list]').forEach(function (b) {
      b.setAttribute('aria-selected', b.getAttribute('data-list') === kind ? 'true' : 'false');
    });
    loadList(1);
  }

  function loadList(page) {
    api.get('/api/users/' + profile.id + '/' + listKind + '?page=' + page, { activity: true }).then(function (r) {
      var list = $('user-list');
      board.clear(list);
      if (r.items.length === 0) {
        list.appendChild(el('li', 'blog-list__empty', listKind === 'followers' ? '팔로워가 없습니다.' : '팔로우한 회원이 없습니다.'));
      }
      r.items.forEach(function (u) { list.appendChild(userItem(u)); });
      board.renderPager($('user-pages'), r.page, r.totalPages, loadList);
    }, function (err) { board.sayError($('message'), err, function () { loadList(page); }); });
  }

  function userItem(u) {
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
    return li;
  }

  $('follower-count').addEventListener('click', function () { openList('followers'); });
  document.querySelectorAll('[data-list]').forEach(function (b) {
    b.addEventListener('click', function () { openList(b.getAttribute('data-list')); });
  });

  api.get('/api/auth/session').then(function (s) { me = s; }, function () { me = null; }).then(load);
})();
