// 게시판 화면 공통 (004 T027·T031·T045). 글 목록 한 줄, 번호 페이지, 시각 표시.
// 값은 모두 textContent로 넣는다. 글 본문(contentHtml)만 board-post.js가 innerHTML로 넣는다(D-64).
(function (global) {
  'use strict';

  function el(tag, className, text) {
    var e = document.createElement(tag);
    if (className) { e.className = className; }
    if (text !== undefined && text !== null) { e.textContent = String(text); }
    return e;
  }

  function clear(node) {
    while (node.firstChild) { node.removeChild(node.firstChild); }
  }

  function formatTime(value) {
    return value ? String(value).replace('T', ' ').substring(0, 16) : '';
  }

  function postUrl(p) {
    if (p.boardType === 'NOTICE' || !p.blog) {
      return '/notices/' + p.id;
    }
    return '/blog/' + encodeURIComponent(p.blog.address) + '/posts/' + p.id;
  }

  // 릴리스 노트 공지(D-108)는 제목이 "v1.02 …"처럼 버전으로 시작한다.
  var UPDATE_TITLE = /^v\d+\.\d+/i;
  function isUpdate(p) { return UPDATE_TITLE.test(p.title || ''); }

  // PostSummary 목록을 그린다. options.showBlog면 블로그 이름을 함께 보인다.
  // options.pinned: 목록 위에 고정하는 공지(D-109). 지우지 않고 이어 붙이며 "공지"("v1.02 …" 같은 버전 글은 "업데이트") 표시를 단다.
  // options.updateBadge: 공지사항 목록에서 버전 글에만 "업데이트" 표시를 단다(D-111).
  function renderPosts(list, items, options) {
    options = options || {};
    if (!options.append) { clear(list); }
    if (!items || items.length === 0) {
      list.appendChild(el('li', 'blog-list__empty', options.empty || '글이 없습니다.'));
      return;
    }
    items.forEach(function (p) {
      var li = el('li', options.pinned ? 'post-item post-item--notice' : 'post-item');
      var body = el('div', 'post-item__body');
      if (options.pinned) {
        body.appendChild(el('span', 'post-item__badge', isUpdate(p) ? '업데이트' : '공지')); // D-109, D-111
      } else if (options.updateBadge && isUpdate(p)) {
        body.appendChild(el('span', 'post-item__badge', '업데이트')); // D-111
      }
      var a = el('a', 'post-item__title', p.title);
      a.href = postUrl(p);
      body.appendChild(a);
      var before = [];
      if (options.showBlog && p.blog) { before.push(p.blog.name); }
      if (p.categoryName) { before.push(p.categoryName); }
      var metaLine = el('div', 'post-item__meta');
      if (before.length) { metaLine.appendChild(document.createTextNode(before.join(' · ') + ' · ')); }
      metaLine.appendChild(authorNode(p.author));
      metaLine.appendChild(document.createTextNode(' · ' + formatTime(p.createdAt) + ' · 좋아요 ' + p.likeCount
        + ' · 댓글 ' + p.commentCount + ' · 조회 ' + p.viewCount));
      body.appendChild(metaLine);
      li.appendChild(body);
      if (p.thumbnailUrl) {
        var img = el('img', 'post-item__thumb');
        img.src = p.thumbnailUrl;
        img.alt = '';
        img.loading = 'lazy';
        li.appendChild(img);
      }
      list.appendChild(li);
    });
  }

  // 작성자 닉네임. 활동 중인 회원이면 프로필 링크(003 T021, FR-001), 탈퇴·블로그 탈퇴는 글자만.
  function authorNode(author) {
    if (!author) { return document.createTextNode(''); }
    if (author.state === 'ACTIVE' && author.id) {
      var a = el('a', 'author-link', author.nickname);
      a.href = '/profile/' + author.id;
      return a;
    }
    return document.createTextNode(author.nickname || '');
  }

  // 번호 페이지. 다섯 개씩 보이고 처음·끝으로 바로 간다(FR-018).
  function renderPager(nav, page, totalPages, onPick) {
    clear(nav);
    if (!totalPages || totalPages <= 1) { return; }
    function btn(label, target, current) {
      var b = el('button', current ? 'is-current' : '', label);
      b.type = 'button';
      if (current) { b.setAttribute('aria-current', 'page'); }
      b.addEventListener('click', function () { onPick(target); });
      nav.appendChild(b);
    }
    var start = Math.max(1, page - 2);
    var end = Math.min(totalPages, start + 4);
    start = Math.max(1, end - 4);
    if (start > 1) { btn('처음', 1, false); }
    for (var i = start; i <= end; i++) { btn(String(i), i, i === page); }
    if (end < totalPages) { btn('끝', totalPages, false); }
  }

  function say(node, text, isError) {
    node.className = 'message ' + (isError ? 'message--error' : 'message--info');
    node.textContent = text || '';
  }

  // 응답이 늦어 멈췄으면 다시 시도 버튼을 붙인다(FR-070).
  function sayError(node, err, retry) {
    say(node, err && err.message ? err.message : '요청을 처리하지 못했습니다.', true);
    if (retry && err && err.code === 'TIMEOUT') {
      var b = el('button', 'link-button', '다시 시도');
      b.type = 'button';
      b.addEventListener('click', retry);
      node.appendChild(document.createTextNode(' '));
      node.appendChild(b);
    }
  }

  global.board = {
    el: el, clear: clear, formatTime: formatTime, postUrl: postUrl, renderPosts: renderPosts, authorNode: authorNode,
    renderPager: renderPager, say: say, sayError: sayError
  };
})(window);
