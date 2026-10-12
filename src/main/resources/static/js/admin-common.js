// 관리자 화면 공통 (005 T009, D-103 #1, D-64, SC-010).
// 탭 전환, 표(좁은 화면은 카드)·페이지 단추·확인 창 도우미. 모든 값은 textContent로 넣는다(innerHTML 없음).
// 각 탭 스크립트(admin-*.js)가 adminUI.registerTab(이름, 처음 열 때 부를 함수)로 등록한다.
(function (global) {
  'use strict';

  try { sessionStorage.removeItem('mb.adminRefreshTried'); } catch (e) { /* 무시 */ }

  var loaders = {};
  var loaded = {};

  var ACTION_LABELS = {
    OWNER_WARNING: '블로그장 경고', OWNER_KICK: '블로그장 강퇴', AUTO_DELEGATE: '자동 위임', CLOSE_SCHEDULE: '폐쇄 예약',
    BLOG_HIDE: '블로그 숨김', BLOG_UNHIDE: '블로그 숨김 해제', BLOG_FORCE_CLOSE: '강제 폐쇄',
    POST_HIDE: '글 숨김', POST_UNHIDE: '글 숨김 해제', POST_DELETE: '글 삭제',
    COMMENT_HIDE: '댓글 숨김', COMMENT_UNHIDE: '댓글 숨김 해제', COMMENT_DELETE: '댓글 삭제',
    RESTORE: '복구', REPORT_HANDLE: '신고 처리', PRIVACY_VIEW: '개인정보 전체 보기',
    INQUIRY_ANSWER: '문의 답변' // ADM-08, D-112
  };
  var BLOG_STATUS = { ACTIVE: '운영 중', CLOSING: '폐쇄 예정', CLOSED: '폐쇄됨' };
  var VISIBILITY = { PUBLIC: '공개', LINK: '일부 공개', PRIVATE: '비공개' };
  var ROLE = { OWNER: '블로그장', MANAGER: '부블로그장', MEMBER: '멤버' };
  var CONTENT_STATUS = { VISIBLE: '보임', HIDDEN: '숨김', DELETED: '삭제' };
  var DELETED_BY = { AUTHOR: '작성자', BLOG_MANAGER: '블로그장·관리 권한자', ADMIN: '관리자' };
  var REPORT_RESULT = { NONE: '문제 없음', OWNER_WARNING: '블로그장 경고', OWNER_KICK: '블로그장 강퇴', BLOG_CLOSE: '블로그 폐쇄' };
  var TARGET = { USER: '회원', BLOG: '블로그', POST: '글', COMMENT: '댓글', REPORT: '신고', INQUIRY: '문의' };

  function el(tag, className, text) {
    var e = document.createElement(tag);
    if (className) { e.className = className; }
    if (text !== undefined && text !== null) { e.textContent = String(text); }
    return e;
  }

  function button(text, onClick, className) {
    var b = el('button', className || 'button-small', text);
    b.type = 'button';
    b.addEventListener('click', onClick);
    return b;
  }

  function clear(node) {
    while (node.firstChild) { node.removeChild(node.firstChild); }
  }

  function fmt(value) {
    if (!value) { return '-'; }
    return String(value).replace('T', ' ').substring(0, 16);
  }

  function nick(ref) {
    if (!ref) { return '-'; }
    return ref.nickname + ' (#' + ref.id + ')';
  }

  function message(node, text, error) {
    if (!node) { return; }
    node.textContent = text || '';
    node.className = 'message' + (text ? (error ? ' message--error' : ' message--info') : '');
  }

  function errorText(err) {
    return err && err.message ? err.message : '요청을 처리하지 못했습니다. 다시 시도해 주세요.';
  }

  // columns: [{label, value: function(row) → 문자열 또는 Node}], onRow: 줄을 누르면 부를 함수
  function table(container, columns, rows, onRow, emptyText) {
    clear(container);
    if (!rows || rows.length === 0) {
      container.appendChild(el('p', 'blog-list__empty', emptyText || '결과가 없습니다.'));
      return;
    }
    var t = el('table', 'admin-table');
    var head = el('thead');
    var hr = el('tr');
    columns.forEach(function (c) { hr.appendChild(el('th', null, c.label)); });
    head.appendChild(hr);
    t.appendChild(head);
    var body = el('tbody');
    rows.forEach(function (row) {
      var tr = el('tr');
      columns.forEach(function (c) {
        var td = el('td');
        td.setAttribute('data-label', c.label);
        var v = c.value(row);
        if (v instanceof Node) { td.appendChild(v); } else { td.textContent = v === undefined || v === null ? '-' : String(v); }
        tr.appendChild(td);
      });
      if (onRow) {
        tr.className = 'is-clickable';
        tr.tabIndex = 0;
        tr.addEventListener('click', function (e) {
          if (e.target.closest('button, a')) { return; }
          onRow(row);
        });
        tr.addEventListener('keydown', function (e) { if (e.key === 'Enter') { onRow(row); } });
      }
      body.appendChild(tr);
    });
    t.appendChild(body);
    container.appendChild(t);
  }

  function pager(container, page, totalPages, go) {
    clear(container);
    if (!totalPages || totalPages <= 1) { return; }
    var start = Math.max(1, page - 4);
    var end = Math.min(totalPages, start + 9);
    for (var p = start; p <= end; p++) {
      (function (n) {
        var b = button(String(n), function () { go(n); });
        if (n === page) { b.className = 'is-current'; b.setAttribute('aria-current', 'page'); }
        container.appendChild(b);
      })(p);
    }
  }

  // dl 목록: pairs = [[이름, 값(문자열·Node)], ...]
  function dl(pairs) {
    var list = el('dl', 'admin-dl');
    pairs.forEach(function (p) {
      list.appendChild(el('dt', null, p[0]));
      var dd = el('dd');
      if (p[1] instanceof Node) { dd.appendChild(p[1]); } else { dd.textContent = p[1] === undefined || p[1] === null ? '-' : String(p[1]); }
      list.appendChild(dd);
    });
    return list;
  }

  function chip(text, tone) {
    return el('span', 'status-chip' + (tone ? ' status-chip--' + tone : ''), text);
  }

  function blogStatusChip(blog) {
    var wrap = el('span', 'row');
    wrap.appendChild(chip(BLOG_STATUS[blog.status] || blog.status, blog.status === 'ACTIVE' ? null : 'warn'));
    if (blog.hidden) { wrap.appendChild(chip('숨김', 'danger')); }
    return wrap;
  }

  function query(params) {
    var parts = [];
    Object.keys(params).forEach(function (k) {
      var v = params[k];
      if (v !== undefined && v !== null && v !== '') { parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v)); }
    });
    return parts.length ? '?' + parts.join('&') : '';
  }

  function show(name) {
    document.querySelectorAll('#admin-tabs [role="tab"]').forEach(function (t) {
      t.setAttribute('aria-selected', String(t.getAttribute('data-tab') === name));
    });
    document.querySelectorAll('main.admin > [data-panel]').forEach(function (p) {
      p.hidden = p.getAttribute('data-panel') !== name;
    });
    if (!loaded[name] && loaders[name]) {
      loaded[name] = true;
      loaders[name]();
    }
    if (history.replaceState) { history.replaceState(null, '', '#' + name); }
  }

  global.adminUI = {
    el: el, button: button, clear: clear, fmt: fmt, nick: nick, message: message, errorText: errorText,
    table: table, pager: pager, dl: dl, chip: chip, blogStatusChip: blogStatusChip, query: query,
    labels: { action: ACTION_LABELS, blogStatus: BLOG_STATUS, visibility: VISIBILITY, role: ROLE,
      contentStatus: CONTENT_STATUS, deletedBy: DELETED_BY, reportResult: REPORT_RESULT, target: TARGET },
    confirm: function (text) { return global.confirm(text); },
    registerTab: function (name, loader) { loaders[name] = loader; },
    show: show
  };

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('#admin-tabs [role="tab"]').forEach(function (t) {
      t.addEventListener('click', function () { show(t.getAttribute('data-tab')); });
    });
    var first = (location.hash || '#members').substring(1);
    show(document.querySelector('[data-panel="' + first + '"]') ? first : 'members');
  });
})(window);
