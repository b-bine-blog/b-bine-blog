// 글 상세 /blog/{주소}/posts/{번호}, 공지 상세 /notices/{번호} (004 T022·T039·T050).
// 본문(contentHtml)은 서버가 걸러 준 HTML이라 innerHTML로 넣고, 그 밖의 값은 모두 textContent로 넣는다(FR-006, D-64).
// 버튼은 편의일 뿐이고 권한은 서버가 요청마다 확인한다(FR-010).
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var el = board.el;
  var parts = location.pathname.split('/');
  var isNotice = parts[1] === 'notices';
  var address = isNotice ? null : decodeURIComponent(parts[2] || '');
  var postId = isNotice ? parts[2] : parts[4];
  var share = new URLSearchParams(location.search).get('share');
  var post = null;
  var me = null;
  var commentPage = 1;

  function postSay(text, isError) { board.say($('post-message'), text, isError); }

  // 주소에 share 값이 있으면 먼저 공유 쿠키를 받는다(R-12, 002 share-access).
  function prepare() {
    if (!share || isNotice) { return Promise.resolve(); }
    return api.post('/api/blogs/share-access', { token: share }).catch(function () { /* 틀린 값이면 볼 수 없음 안내 */ });
  }

  function load() {
    return api.get('/api/posts/' + encodeURIComponent(postId)).then(function (p) {
      post = p;
      if (!isNotice && p.blog && p.blog.address !== address) {
        location.replace(board.postUrl(p));
        return;
      }
      render();
      if (!p.hiddenByAdmin) { loadComments(1); } // 관리자가 숨긴 글은 댓글을 열지 않는다(005 T044, D-103 #10)
    }, function (err) {
      board.sayError($('message'), err.status === 404 ? { message: '볼 수 없거나 삭제된 글입니다.' } : err, load);
    });
  }

  function render() {
    document.title = post.title + ' - 블바인 블로그';
    if (!isNotice && post.blog) {
      var link = $('blog-link');
      link.href = '/blog/' + encodeURIComponent(post.blog.address);
      link.textContent = post.blog.name;
    }
    api.setText($('title'), post.title);
    var metaBox = $('meta');
    board.clear(metaBox);
    if (post.categoryName) { metaBox.appendChild(document.createTextNode(post.categoryName + ' · ')); }
    metaBox.appendChild(board.authorNode(post.author)); // 활동 중인 작성자는 프로필 링크(003 T021)
    metaBox.appendChild(document.createTextNode(' · ' + board.formatTime(post.createdAt) + ' · 조회 ' + post.viewCount));
    $('content').innerHTML = post.contentHtml; // 서버에서 걸러낸 HTML(R-01)
    var tags = $('tags');
    board.clear(tags);
    (post.tags || []).forEach(function (t) {
      var li = el('li');
      var a = el('a', null, '#' + t);
      a.href = '/tags/' + encodeURIComponent(t);
      li.appendChild(a);
      tags.appendChild(li);
    });
    renderLike();
    $('share').hidden = isNotice || !post.shareable;
    $('edit').hidden = !post.canEdit;
    if (post.canEdit) {
      $('edit').href = isNotice ? '/notices/' + post.id + '/edit'
        : '/blog/' + encodeURIComponent(address) + '/posts/' + post.id + '/edit';
    }
    $('delete').hidden = !post.canDelete;
    renderReport();
    $('post').hidden = false;
    $('comments').hidden = isNotice || !!post.hiddenByAdmin;
    $('comment-form').hidden = !post.canComment;
    if (post.hiddenByAdmin) {
      board.say($('message'), '관리자가 숨긴 글입니다. 작성자인 나에게만 보이고 고치거나 댓글을 달 수 없습니다.', true);
    }
    $('comment-login').hidden = !!me || isNotice;
  }

  // 신고(003 T037, SOC-06): 로그인한 일반 회원이 남의 글·작성자·댓글만. 공지는 신고 대상이 아니다.
  // 접수처는 서버가 정하고, 블로그 안 작성자 신고는 blogId를 함께 보낸다(블로그장이면 메인 관리자에게 간다).
  function canReport(author) {
    return !!(me && me.memberId && !me.admin && !isNotice && author && author.id && author.id !== me.memberId);
  }

  function renderReport() {
    if (!$('report')) { return; }
    var ok = canReport(post.author);
    $('report').hidden = !ok;
    $('report-author').hidden = !(ok && post.author.state === 'ACTIVE');
  }

  function renderLike() {
    var canLike = !isNotice && post.canComment;
    $('like').hidden = !canLike;
    $('like-static').hidden = canLike || isNotice;
    api.setText($('like-count'), post.likeCount);
    api.setText($('like-static'), '좋아요 ' + post.likeCount);
    $('like').setAttribute('aria-pressed', post.liked ? 'true' : 'false');
    $('like').style.fontWeight = post.liked ? '700' : '400';
  }

  $('like').addEventListener('click', function () {
    api.put('/api/posts/' + post.id + '/like', null, { activity: true }).then(function (r) {
      post.liked = r.liked;
      post.likeCount = r.likeCount;
      renderLike();
    }, function (err) { postSay(err.message, true); });
  });

  $('delete').addEventListener('click', function () {
    if (!confirm('글을 정말 삭제하시겠습니까?')) { return; }
    api.del('/api/posts/' + post.id, null, { activity: true }).then(function () {
      location.href = isNotice ? '/notices' : '/blog/' + encodeURIComponent(address);
    }, function (err) { postSay(err.message, true); });
  });

  // 공유: 공개 글은 주소, 일부 공개 글은 주소 + 유효한 공유 값, 비공개는 버튼 없음(D-100 #5, FR-047).
  function shareUrl() {
    var base = location.origin + '/blog/' + encodeURIComponent(address) + '/posts/' + post.id;
    if (post.blog.visibility !== 'LINK') { return Promise.resolve(base); }
    return api.get('/api/blogs/' + post.blog.id + '/share-link').then(function (r) {
      return base + '?share=' + encodeURIComponent(r.token);
    }, function () {
      return share ? base + '?share=' + encodeURIComponent(share) : null;
    });
  }

  $('share').addEventListener('click', function () {
    shareUrl().then(function (url) {
      if (!url) { postSay('공유할 수 있는 링크가 없습니다.', true); return; }
      var done = function () { postSay('링크를 복사했습니다.'); };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(url).then(done, function () { fallbackCopy(url); });
      } else {
        fallbackCopy(url);
      }
    });
  });

  function fallbackCopy(url) {
    var input = $('share-url');
    input.value = url;
    input.classList.remove('sr-only');
    input.select();
    postSay('주소를 선택해 두었습니다. 복사해 주세요.');
  }

  // 댓글 (FR-036~FR-041)
  function loadComments(page) {
    if (isNotice) { return; }
    commentPage = page;
    api.get('/api/posts/' + post.id + '/comments?page=' + page).then(function (r) {
      api.setText($('comment-count'), r.totalElements);
      var list = $('comment-list');
      board.clear(list);
      r.items.forEach(function (c) {
        list.appendChild(commentItem(c, false));
        (c.replies || []).forEach(function (reply) { list.appendChild(commentItem(reply, true)); });
      });
      board.renderPager($('comment-pages'), r.page, r.totalPages, loadComments);
    }, function (err) { board.sayError($('message'), err, function () { loadComments(page); }); });
  }

  function commentItem(c, isReply) {
    var li = el('li', 'comment' + (isReply ? ' comment--reply' : ''));
    if (c.hidden) {
      li.appendChild(el('p', 'comment__deleted', '관리자가 숨긴 댓글입니다')); // 005 T042
      return li;
    }
    if (c.deleted) {
      li.appendChild(el('p', 'comment__deleted', '삭제된 댓글입니다'));
      return li;
    }
    var meta = el('div', 'comment__meta');
    meta.appendChild(board.authorNode(c.author));
    meta.appendChild(document.createTextNode(' · ' + board.formatTime(c.createdAt) + (c.edited ? ' · 수정됨' : '')));
    li.appendChild(meta);
    var text = el('p', 'comment__text');
    if (c.replyToNickname) {
      var to = el('strong', null, '@' + c.replyToNickname + ' ');
      text.appendChild(to);
    }
    text.appendChild(document.createTextNode(c.content));
    li.appendChild(text);
    var actions = el('div', 'comment__actions');
    if (post.canComment) { actions.appendChild(action('답글', function () { openForm(li, c, 'reply'); })); }
    if (c.canEdit) { actions.appendChild(action('수정', function () { openForm(li, c, 'edit'); })); }
    if (c.canDelete) {
      actions.appendChild(action('삭제', function () {
        if (!confirm('댓글을 삭제할까요?')) { return; }
        api.del('/api/comments/' + c.id, null, { activity: true })
          .then(function () { loadComments(commentPage); }, function (err) { board.say($('message'), err.message, true); });
      }));
    }
    if (canReport(c.author) && window.report) {
      actions.appendChild(action('신고', function () {
        report.open({ targetType: 'COMMENT', targetId: c.id, blogId: post.blog && post.blog.id, label: '댓글' });
      }));
    }
    li.appendChild(actions);
    return li;
  }

  function action(label, onClick) {
    var b = el('button', null, label);
    b.type = 'button';
    b.addEventListener('click', onClick);
    return b;
  }

  function openForm(li, c, mode) {
    var existing = li.querySelector('form');
    if (existing) { li.removeChild(existing); return; }
    var form = el('form');
    var ta = el('textarea');
    ta.maxLength = 500;
    ta.rows = 2;
    if (mode === 'edit') { ta.value = c.content; }
    form.appendChild(ta);
    var submit = el('button', null, mode === 'edit' ? '수정' : '답글 쓰기');
    submit.type = 'submit';
    form.appendChild(submit);
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var req = mode === 'edit'
        ? api.patch('/api/comments/' + c.id, { content: ta.value }, { activity: true })
        : api.post('/api/posts/' + post.id + '/comments', { content: ta.value, parentId: c.id }, { activity: true });
      req.then(function () { loadComments(commentPage); }, function (err) { board.say($('message'), err.message, true); });
    });
    li.appendChild(form);
    ta.focus();
  }

  $('comment-content').addEventListener('input', function () {
    api.setText($('comment-length'), $('comment-content').value.length);
  });

  $('comment-form').addEventListener('submit', function (e) {
    e.preventDefault();
    api.post('/api/posts/' + post.id + '/comments', { content: $('comment-content').value }, { activity: true })
      .then(function () {
        $('comment-content').value = '';
        api.setText($('comment-length'), 0);
        loadComments(commentPage);
      }, function (err) { board.say($('message'), err.message, true); });
  });

  if ($('report')) {
    $('report').addEventListener('click', function () {
      report.open({ targetType: 'POST', targetId: post.id, blogId: post.blog && post.blog.id, label: '글' });
    });
    $('report-author').addEventListener('click', function () {
      report.open({ targetType: 'USER', targetId: post.author.id, blogId: post.blog && post.blog.id,
        label: post.author.nickname + '님' });
    });
  }

  api.get('/api/auth/session').then(function (s) { me = s; }, function () { me = null; })
    .then(prepare).then(load);
})();
