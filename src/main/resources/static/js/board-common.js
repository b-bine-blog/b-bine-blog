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
      // 제목 위에 카테고리(없으면 "잡담")를 작게, 본문에 그림이 있으면 그림 표시(BRD-02, D-132)
      if (!options.pinned) {
        var cat = el('div', 'post-item__cat', p.categoryName || (p.boardType === 'NOTICE' ? '공지' : UNCATEGORIZED));
        if (p.thumbnailUrl) { cat.appendChild(imageMark()); }
        if (p.edited) { cat.appendChild(el('span', 'post-item__edited', '수정됨')); }  // 고친 글(D-133)
        if (p.scrapped) { cat.appendChild(bookmarkIcon(true, 'post-item__scrap-mark', '스크랩한 글')); }  // D-134
        body.appendChild(cat);
      }
      var a = el('a', 'post-item__title', p.title);
      a.href = postUrl(p);
      body.appendChild(a);
      var metaLine = el('div', 'post-item__meta');
      // 블로그 이름은 그 블로그로 가는 링크(2026-10-14 사용자 요청)
      if (options.showBlog && p.blog) {
        var blogLink = el('a', 'author-link', p.blog.name);
        blogLink.href = '/blog/' + encodeURIComponent(p.blog.address);
        metaLine.appendChild(blogLink);
        metaLine.appendChild(document.createTextNode(' · '));
      }
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

  // 카테고리가 없는 글의 이름(D-132, 예전 "미분류")
  var UNCATEGORIZED = '잡담';

  // 그림이 들어 있는 글 표시(작은 그림 아이콘)
  function imageMark() {
    var ns = 'http://www.w3.org/2000/svg';
    var svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('class', 'post-item__image-mark');
    svg.setAttribute('viewBox', '0 0 16 16');
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', '그림 있음');
    var title = document.createElementNS(ns, 'title');
    title.textContent = '그림 있음';
    svg.appendChild(title);
    var path = document.createElementNS(ns, 'path');
    path.setAttribute('d', 'M2 3h12v10H2z M2 11l3.5-3.5 2.5 2.5 2-2 4 4 M10.5 6.5a1 1 0 1 0 0.01 0');
    path.setAttribute('fill', 'none');
    path.setAttribute('stroke', 'currentColor');
    path.setAttribute('stroke-width', '1.4');
    path.setAttribute('stroke-linejoin', 'round');
    svg.appendChild(path);
    return svg;
  }

  // 책갈피(스크랩) 그림. 채우면 스크랩한 상태(D-134). 모양은 Lucide bookmark.
  function bookmarkIcon(filled, className, label) {
    var ns = 'http://www.w3.org/2000/svg';
    var svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('class', className);
    svg.setAttribute('viewBox', '0 0 24 24');
    if (label) {
      svg.setAttribute('role', 'img');
      svg.setAttribute('aria-label', label);
      var title = document.createElementNS(ns, 'title');
      title.textContent = label;
      svg.appendChild(title);
    } else {
      svg.setAttribute('aria-hidden', 'true');
    }
    var path = document.createElementNS(ns, 'path');
    path.setAttribute('d', 'm19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z');
    path.setAttribute('fill', filled ? 'currentColor' : 'none');
    path.setAttribute('stroke', 'currentColor');
    path.setAttribute('stroke-width', '2');
    path.setAttribute('stroke-linejoin', 'round');
    svg.appendChild(path);
    return svg;
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
    el: el, clear: clear, bookmarkIcon: bookmarkIcon, UNCATEGORIZED: UNCATEGORIZED, formatTime: formatTime, postUrl: postUrl, renderPosts: renderPosts, authorNode: authorNode,
    renderPager: renderPager, say: say, sayError: sayError
  };
})(window);
