// 종 아이콘·알림 사이드바·30초 확인 (003 T012, SOC-04, FR-028~FR-033, D-72, D-102 #17~#21).
// - 30초 확인(unread-count)은 {activity: true} 없이 보낸다. 로그인 연장으로 세지 않는다(FR-032, D-62).
//   401이면(로그인이 끝남) 확인을 멈추고 종 아이콘을 숨긴다.
// - 탭 바꾸기·더 보기·읽음·삭제는 사용자가 누른 것이라 {activity: true}로 보낸다.
// - 모든 글자는 textContent로만 넣는다(D-64). innerHTML은 쓰지 않는다.
(function (global) {
  'use strict';

  var BADGE_MAX = 99; // 서버 notification.badge-max와 같게 둔다. 서버는 100에서 멈춰 센다
  var TABS = [
    ['ALL', '전체'], ['COMMENT', '댓글'], ['LIKE', '좋아요'], ['FOLLOW', '팔로우·구독'],
    ['BLOG', '블로그'], ['OPERATION', '운영']
  ];
  // 원래 이동할 곳이 있는 종류. link가 null이면 대상이 없어진 것이라 "볼 수 없는 내용입니다"를 보인다(research R-06)
  var LINKED = ['POST_COMMENT', 'COMMENT_REPLY', 'POST_LIKE', 'NOTICE', 'NEW_FOLLOWER', 'BLOG_SUBSCRIBED',
    'BLOG_CLOSING', 'BLOG_CLOSING_WITHDRAWN', 'BLOG_PRIVATE', 'JOIN_REQUESTED', 'DELEGATION_REQUESTED'];

  var bell, badge, panel, list, more, message, tabBox;
  var tab = 'ALL';
  var items = [];
  var nextBefore = null;
  var timer = null;
  var stopped = false;

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

  function say(text, isError) {
    message.className = 'message ' + (isError ? 'message--error' : 'message--info');
    api.setText(message, text);
  }

  function setBadge(count) {
    if (!count) {
      badge.hidden = true;
      bell.setAttribute('aria-label', '알림');
      return;
    }
    var text = count > BADGE_MAX ? BADGE_MAX + '+' : String(count);
    api.setText(badge, text);
    badge.hidden = false;
    bell.setAttribute('aria-label', '알림, 안 읽은 알림 ' + text + '개');
  }

  // 30초 확인. 응답의 pollIntervalSeconds(서버 설정 notification.poll-interval)로 다음 확인을 잡는다.
  function poll() {
    if (stopped) { return; }
    api.get('/api/notifications/unread-count').then(function (r) {
      setBadge(r.unreadCount);
      schedule((r.pollIntervalSeconds || 30) * 1000);
    }, function (err) {
      if (err.status === 401) {
        stop();
        return;
      }
      schedule(30 * 1000); // 응답이 늦거나 서버 오류면 다음 확인에서 다시 본다
    });
  }

  function schedule(ms) {
    clearTimeout(timer);
    timer = setTimeout(poll, ms);
  }

  function stop() {
    stopped = true;
    clearTimeout(timer);
    bell.hidden = true;
    close();
  }

  function refreshCount() {
    clearTimeout(timer);
    poll();
  }

  function buildPanel() {
    panel = el('aside', 'notif-panel');
    panel.id = 'notif-panel';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-label', '알림');
    panel.hidden = true;

    var head = el('div', 'notif-panel__head');
    head.appendChild(el('h2', null, '알림'));
    var closeBtn = el('button', 'notif-panel__close', '닫기');
    closeBtn.type = 'button';
    closeBtn.addEventListener('click', close);
    head.appendChild(closeBtn);
    panel.appendChild(head);

    tabBox = el('div', 'tabs notif-panel__tabs');
    tabBox.setAttribute('role', 'tablist');
    TABS.forEach(function (t) {
      var b = el('button', null, t[1]);
      b.type = 'button';
      b.setAttribute('role', 'tab');
      b.setAttribute('data-notif-tab', t[0]);
      b.addEventListener('click', function () { selectTab(t[0]); });
      tabBox.appendChild(b);
    });
    panel.appendChild(tabBox);

    var actions = el('div', 'row notif-panel__actions');
    var readAll = el('button', 'button-outline', '모두 읽음');
    readAll.type = 'button';
    readAll.addEventListener('click', markAllRead);
    var deleteAll = el('button', 'button-quiet', '전체 삭제');
    deleteAll.type = 'button';
    deleteAll.addEventListener('click', removeAll);
    actions.appendChild(readAll);
    actions.appendChild(deleteAll);
    panel.appendChild(actions);

    message = el('p', 'message');
    message.setAttribute('role', 'status');
    panel.appendChild(message);

    list = el('ul', 'notif-list');
    panel.appendChild(list);

    more = el('button', 'button-outline feed-more', '더 보기');
    more.type = 'button';
    more.hidden = true;
    more.addEventListener('click', function () { load(false); });
    panel.appendChild(more);

    document.body.appendChild(panel);
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !panel.hidden) { close(); }
    });
  }

  function open() {
    panel.hidden = false;
    bell.setAttribute('aria-expanded', 'true');
    document.body.classList.add('notif-open');
    selectTab(tab);
  }

  function close() {
    if (!panel || panel.hidden) { return; }
    panel.hidden = true;
    bell.setAttribute('aria-expanded', 'false');
    document.body.classList.remove('notif-open');
    bell.focus();
  }

  function selectTab(next) {
    tab = next;
    tabBox.querySelectorAll('[data-notif-tab]').forEach(function (b) {
      b.setAttribute('aria-selected', b.getAttribute('data-notif-tab') === tab ? 'true' : 'false');
    });
    load(true);
  }

  // 목록만 받는 것으로는 읽음이 되지 않는다(D-102 #18).
  function load(reset) {
    if (reset) {
      items = [];
      nextBefore = null;
    }
    say('');
    var url = '/api/notifications?tab=' + tab + (nextBefore ? '&before=' + nextBefore : '');
    api.get(url, { activity: true }).then(function (r) {
      items = items.concat(r.items);
      nextBefore = r.nextBefore;
      more.hidden = !r.hasMore;
      render();
    }, function (err) {
      if (err.status === 401) { stop(); return; }
      say(err.message, true);
    });
  }

  function render() {
    clear(list);
    if (items.length === 0) {
      list.appendChild(el('li', 'notif-list__empty', '알림이 없습니다.'));
      return;
    }
    items.forEach(function (n) { list.appendChild(item(n)); });
  }

  function item(n) {
    var li = el('li', 'notif-item' + (n.read ? '' : ' is-unread'));
    var main = el('button', 'notif-item__main');
    main.type = 'button';
    main.appendChild(el('span', 'notif-item__content', n.content));
    main.appendChild(el('span', 'notif-item__time', formatTime(n.createdAt)));
    var detail = el('p', 'notif-item__detail');
    detail.hidden = true;
    main.addEventListener('click', function () { openItem(n, li, detail); });
    li.appendChild(main);

    var x = el('button', 'notif-item__delete', '×');
    x.type = 'button';
    x.setAttribute('aria-label', '이 알림 지우기');
    x.addEventListener('click', function () { removeOne(n); });
    li.appendChild(x);
    li.appendChild(detail);
    return li;
  }

  // 누르면 그 알림만 읽음으로 바꾸고 link로 이동한다. link가 없으면 내용만 펼친다(D-102 #21).
  function openItem(n, li, detail) {
    var marking = n.read ? Promise.resolve() : api.post('/api/notifications/' + n.id + '/read', null, { activity: true })
      .then(function () {
        n.read = true;
        li.classList.remove('is-unread');
        refreshCount();
      });
    marking.then(function () {
      if (n.link) {
        location.href = n.link;
        return;
      }
      api.setText(detail, LINKED.indexOf(n.type) >= 0 ? '볼 수 없는 내용입니다.' : n.content);
      detail.hidden = !detail.hidden;
    }, function (err) { say(err.message, true); });
  }

  function markAllRead() {
    api.post('/api/notifications/read-all?tab=' + tab, null, { activity: true }).then(function (r) {
      items.forEach(function (n) { n.read = true; });
      render();
      refreshCount();
      say(r.updated ? '모두 읽음으로 바꿨습니다.' : '안 읽은 알림이 없습니다.');
    }, function (err) { say(err.message, true); });
  }

  function removeOne(n) {
    api.del('/api/notifications/' + n.id, null, { activity: true }).then(function () {
      items = items.filter(function (x) { return x.id !== n.id; });
      render();
      refreshCount();
    }, function (err) { say(err.message, true); });
  }

  function removeAll() {
    var label = tab === 'ALL' ? '모든 알림' : '이 탭의 알림';
    if (!global.confirm(label + '을 지울까요? 지운 알림은 되돌릴 수 없습니다.')) { return; }
    api.del('/api/notifications?tab=' + tab, null, { activity: true }).then(function () {
      items = [];
      nextBefore = null;
      more.hidden = true;
      render();
      refreshCount();
    }, function (err) { say(err.message, true); });
  }

  global.notifications = {
    // layout.js가 로그인 상태의 상단 메뉴를 그린 뒤 부른다.
    start: function (bellButton, badgeEl) {
      bell = bellButton;
      badge = badgeEl;
      buildPanel();
      bell.setAttribute('aria-controls', 'notif-panel');
      bell.setAttribute('aria-expanded', 'false');
      bell.addEventListener('click', function () {
        if (panel.hidden) { open(); } else { close(); }
      });
      poll();
    }
  };
})(window);
