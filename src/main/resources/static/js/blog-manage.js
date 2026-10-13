// 블로그 운영 화면 /blog/{주소}/manage (T065, T068, T071).
// 탭은 myPermissions로 보이고 숨기며, 실제 권한은 서버가 요청마다 확인한다(FR-034). 값은 모두 textContent로 넣는다(D-64).
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var address = decodeURIComponent(location.pathname.split('/')[2] || '');
  var PERIODS = [['DAYS_3', '3일'], ['DAYS_14', '14일'], ['DAYS_30', '30일'], ['PERMANENT', '영구']];
  var ROLES = { OWNER: '블로그장', MANAGER: '부블로그장', MEMBER: '멤버' };
  var RESULTS = [['NONE', '문제 없음'], ['WARNING', '경고'], ['SUSPEND', '정지'], ['KICK', '강제 퇴장']];
  var blog = null;
  var tagInput = null;
  var loaders = {};

  function say(text, isError) {
    var el = $('tab-message');
    el.className = 'message ' + (isError ? 'message--error' : 'message--info');
    api.setText(el, text);
  }

  function fail(err) { say(err.message, true); }

  function time(v) { return v ? v.replace('T', ' ').substring(0, 16) : ''; }

  function el(tag, text, className) {
    var node = document.createElement(tag);
    if (text != null) { node.textContent = text; }
    if (className) { node.className = className; }
    return node;
  }

  function button(text, onClick) {
    var b = el('button', text);
    b.type = 'button';
    b.addEventListener('click', onClick);
    return b;
  }

  function select(options) {
    var s = el('select');
    options.forEach(function (o) {
      var opt = el('option', o[1]);
      opt.value = o[0];
      s.appendChild(opt);
    });
    return s;
  }

  function row(cells) {
    var tr = el('tr');
    cells.forEach(function (c) {
      var td = el('td');
      if (c instanceof Node) { td.appendChild(c); } else { td.textContent = c == null ? '' : c; }
      tr.appendChild(td);
    });
    return tr;
  }

  function fill(tbodyId, rows, emptyText, columns) {
    var body = $(tbodyId);
    body.textContent = '';
    if (!rows.length) {
      var td = el('td', emptyText);
      td.colSpan = columns;
      var tr = el('tr');
      tr.appendChild(td);
      body.appendChild(tr);
    }
    rows.forEach(function (r) { body.appendChild(r); });
  }

  function base() { return '/api/blogs/' + blog.id; }

  function has(perm) { return (blog.myPermissions || []).indexOf(perm) >= 0; }

  // 정보 수정 (FR-009, FR-013)
  loaders.info = function () {
    $('name').value = blog.name;
    $('description').value = blog.description || '';
    tagInput.set(blog.tags || []);
    $('visibility').value = blog.visibility;
    $('join-policy').value = blog.joinPolicy;
    document.querySelectorAll('[data-owner-only]').forEach(function (n) {
      n.hidden = !has('OWNER') || (n.hasAttribute('data-link-only') && blog.visibility !== 'LINK');
    });
  };

  $('info-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var body = { name: $('name').value, description: $('description').value || null, tags: tagInput.get() };
    if (has('OWNER')) {
      body.visibility = $('visibility').value;
      body.joinPolicy = $('join-policy').value;
    }
    if (has('OWNER') && body.visibility === 'PRIVATE' && blog.visibility !== 'PRIVATE'
        && !confirm('비공개로 바꾸면 구독자에게 알림이 가고 멤버가 아닌 구독자는 글을 볼 수 없습니다. 바꿀까요?')) {
      return;
    }
    api.patch(base(), body, { activity: true })
      .then(function (b) { blog = b; loaders.info(); say('저장했습니다.'); })
      .catch(function (err) {
        say(err.code === 'BLOG_LIMIT_EXCEEDED'
          ? '이 공개 범위로는 블로그를 ' + err.body.limit + '개까지 가질 수 있습니다.' : err.message, true);
      });
  });

  $('cover-upload').addEventListener('click', function () {
    var file = $('cover').files[0];
    if (!file) { say('이미지를 골라 주세요.', true); return; }
    var form = new FormData();
    form.append('file', file);
    api.put(base() + '/cover-image', form, { activity: true })
      .then(function () { say('대표 이미지를 바꿨습니다.'); }).catch(fail);
  });

  $('cover-delete').addEventListener('click', function () {
    api.del(base() + '/cover-image', null, { activity: true })
      .then(function () { say('대표 이미지를 지웠습니다.'); }).catch(fail);
  });

  // 배경 이미지(D-121). 대표 이미지와 같은 검사·크기 제한
  $('background-upload').addEventListener('click', function () {
    var file = $('background').files[0];
    if (!file) { say('이미지를 골라 주세요.', true); return; }
    var form = new FormData();
    form.append('file', file);
    api.put(base() + '/background-image', form, { activity: true })
      .then(function () { say('배경 이미지를 바꿨습니다.'); }).catch(fail);
  });

  $('background-delete').addEventListener('click', function () {
    api.del(base() + '/background-image', null, { activity: true })
      .then(function () { say('배경 이미지를 지웠습니다. 기본 배경으로 보입니다.'); }).catch(fail);
  });

  $('share-regenerate').addEventListener('click', function () {
    if (!confirm('지금 링크로는 더 이상 들어올 수 없습니다. 새로 만들까요?')) { return; }
    api.post(base() + '/share-link', null, { activity: true })
      .then(function (link) {
        return navigator.clipboard.writeText(location.origin + link.url)
          .then(function () { say('새 공유 링크를 만들고 복사했습니다.'); },
            function () { say('새 공유 링크를 만들었습니다: ' + location.origin + link.url); });
      }).catch(fail);
  });

  // 참여 신청 (FR-019~FR-021)
  loaders.requests = function () {
    api.get(base() + '/join-requests?size=30').then(function (res) {
      fill('request-rows', res.items.map(function (r) {
        var actions = el('div', null, 'row');
        actions.appendChild(button('승인', function () { decide(r.id, 'approval'); }));
        actions.appendChild(button('거절', function () { decide(r.id, 'rejection'); }));
        return row([r.nickname, time(r.requestedAt), actions]);
      }), '대기 중인 신청이 없습니다.', 3);
    }).catch(fail);
  };

  function decide(id, action) {
    api.post(base() + '/join-requests/' + id + '/' + action, null, { activity: true })
      .then(function () { say(action === 'approval' ? '승인했습니다.' : '거절했습니다.'); loaders.requests(); })
      .catch(fail);
  }

  // 멤버: 제재, 부블로그장 지정·해제(블로그장만) (FR-039~FR-056)
  loaders.members = function () {
    api.get(base() + '/members?size=30').then(function (res) {
      fill('member-rows', res.items.map(memberRow), '멤버가 없습니다.', 7);
    }).catch(fail);
  };

  function memberRow(m) {
    var state = m.suspended ? (m.suspendedUntil ? time(m.suspendedUntil) + '까지 정지' : '영구 정지') : '';
    if (m.suspendCountInWindow) { state += (state ? ' · ' : '') + '1년 정지 ' + m.suspendCountInWindow + '번'; }
    if (m.kickSuggested) { state += ' · 강제 퇴장 검토'; }
    var roleCell = el('div');
    roleCell.appendChild(el('span', ROLES[m.role]));
    if (has('OWNER') && m.role !== 'OWNER') { roleCell.appendChild(managerControls(m)); }
    var actions = el('div', null, 'row');
    var canSanction = m.role === 'MEMBER' || (m.role === 'MANAGER' && has('OWNER'));
    if (canSanction) {
      var period = select(PERIODS);
      actions.appendChild(button('경고', function () { sanction(m, { type: 'WARNING' }); }));
      actions.appendChild(period);
      actions.appendChild(button('정지', function () { sanction(m, { type: 'SUSPEND', period: period.value }); }));
      if (m.suspended) {
        actions.appendChild(button('정지 해제', function () { sanction(m, { type: 'UNSUSPEND' }); }));
      }
      actions.appendChild(button('강제 퇴장', function () { sanction(m, { type: 'KICK' }); }));
    }
    return row([m.nickname, m.name, m.maskedEmail, m.maskedPhone, roleCell, state, actions]);
  }

  function managerControls(m) {
    var box = el('div');
    var perms = m.permissions || {};
    var checks = [['blogInfo', '정보 수정'], ['memberManage', '멤버 관리'], ['postManage', '글 관리']].map(function (p) {
      var label = el('label', null, 'inline');
      var input = el('input');
      input.type = 'checkbox';
      input.checked = m.role === 'MANAGER' && !!perms[p[0]];
      label.appendChild(input);
      label.appendChild(document.createTextNode(' ' + p[1]));
      box.appendChild(label);
      return [p[0], input];
    });
    box.appendChild(button(m.role === 'MANAGER' ? '권한 저장' : '부블로그장 지정', function () {
      var body = {};
      checks.forEach(function (c) { body[c[0]] = c[1].checked; });
      api.put(base() + '/managers/' + m.userId, body, { activity: true })
        .then(function () { say('부블로그장 권한을 저장했습니다.'); loaders.members(); }).catch(fail);
    }));
    if (m.role === 'MANAGER') {
      box.appendChild(button('부블로그장 해제', function () {
        api.del(base() + '/managers/' + m.userId, null, { activity: true })
          .then(function () { say('일반 멤버로 바꿨습니다.'); loaders.members(); }).catch(fail);
      }));
    }
    return box;
  }

  function sanction(m, body) {
    var labels = { WARNING: '경고', SUSPEND: '정지', UNSUSPEND: '정지 해제', KICK: '강제 퇴장' };
    if (body.type !== 'UNSUSPEND') {
      var reason = prompt(m.nickname + '님 ' + labels[body.type] + ' 사유 (선택)');
      if (reason === null) { return; }
      body.reason = reason;
    }
    if (body.type === 'KICK' && !confirm('강제 퇴장하면 블랙리스트에 오르고 다시 참여할 수 없습니다. 진행할까요?')) {
      return;
    }
    api.post(base() + '/members/' + m.userId + '/sanctions', body, { activity: true })
      .then(function () { say(labels[body.type] + ' 처리했습니다.'); loaders.members(); }).catch(fail);
  }

  // 블로그 안 신고 (FR-052)
  loaders.reports = function () {
    api.get(base() + '/reports?size=30').then(function (res) {
      fill('report-rows', res.items.map(function (r) {
        var result = select(RESULTS);
        var period = select(PERIODS);
        period.hidden = true;
        result.addEventListener('change', function () { period.hidden = result.value !== 'SUSPEND'; });
        var actions = el('div', null, 'row');
        actions.appendChild(result);
        actions.appendChild(period);
        actions.appendChild(button('처리', function () {
          var body = { result: result.value };
          if (result.value === 'SUSPEND') { body.period = period.value; }
          api.post(base() + '/reports/' + r.id + '/resolution', body, { activity: true })
            .then(function () { say('신고를 처리했습니다.'); loaders.reports(); }).catch(fail);
        }));
        return row([(r.targetUserNickname || '알 수 없음') + ' (' + r.targetType + ')', r.targetSnapshot,
          r.reason + (r.reasonDetail ? ' · ' + r.reasonDetail : ''), actions]);
      }), '처리할 신고가 없습니다.', 4);
    }).catch(fail);
  };

  // 해제 문의와 블랙리스트 (FR-059, FR-062, FR-063)
  loaders.blacklist = function () {
    api.get(base() + '/blacklist-appeals').then(function (res) {
      fill('appeal-rows', res.items.map(function (a) {
        var actions = el('div', null, 'row');
        actions.appendChild(button('해제', function () { appeal(a.id, 'release'); }));
        actions.appendChild(button('거절', function () { appeal(a.id, 'rejection'); }));
        return row([a.applicantNickname, a.content || '', a.nameMatched ? '일치' : '불일치',
          a.phoneMatched ? '일치' : '불일치', actions]);
      }), '대기 중인 문의가 없습니다.', 5);
    }).catch(fail);
    api.get(base() + '/blacklist?size=30').then(function (res) {
      fill('blacklist-rows', res.items.map(function (b) {
        return row([time(b.registeredAt), b.kickReason || '', b.kickedByNickname || '',
          b.released ? time(b.releasedAt) + ' 해제' : '']);
      }), '블랙리스트가 비어 있습니다.', 4);
    }).catch(fail);
  };

  function appeal(id, action) {
    api.post(base() + '/blacklist-appeals/' + id + '/' + action, null, { activity: true })
      .then(function () { say(action === 'release' ? '해제했습니다.' : '거절했습니다.'); loaders.blacklist(); })
      .catch(fail);
  }

  // 위임·폐쇄 (FR-066~FR-078). 위임할 멤버가 없거나 폐쇄 예정이면 위임을 숨긴다(FR-070, FR-071).
  // 카테고리 (004 T028). 순서는 위·아래 버튼으로 바꾸고 전체 순서를 한 번에 보낸다.
  loaders.categories = function () {
    var base = '/api/blogs/' + blog.id + '/categories';
    api.get(base).then(function (r) {
      var rows = $('category-rows');
      while (rows.firstChild) { rows.removeChild(rows.firstChild); }
      var ids = r.categories.map(function (c) { return c.id; });
      function move(index, delta) {
        var next = ids.slice();
        var t = next[index];
        next[index] = next[index + delta];
        next[index + delta] = t;
        api.put(base + '/order', { categoryIds: next }, { activity: true })
          .then(function () { loaders.categories(); }).catch(fail);
      }
      r.categories.forEach(function (c, i) {
        var tr = el('tr');
        var order = el('td');
        if (i > 0) { order.appendChild(button('위', function () { move(i, -1); })); }
        if (i < ids.length - 1) { order.appendChild(button('아래', function () { move(i, 1); })); }
        tr.appendChild(order);
        tr.appendChild(el('td', c.name));
        tr.appendChild(el('td', String(c.postCount)));
        var actions = el('td');
        actions.appendChild(button('이름 바꾸기', function () {
          var name = prompt('새 이름 (최대 30자)', c.name);
          if (name === null) { return; }
          api.patch(base + '/' + c.id, { name: name }, { activity: true })
            .then(function () { say('이름을 바꿨습니다.'); loaders.categories(); }).catch(fail);
        }));
        actions.appendChild(button('삭제', function () {
          if (!confirm('"' + c.name + '" 카테고리를 지울까요? 글은 미분류로 옮겨집니다.')) { return; }
          api.del(base + '/' + c.id, null, { activity: true })
            .then(function () { say('지웠습니다.'); loaders.categories(); }).catch(fail);
        }));
        tr.appendChild(actions);
        rows.appendChild(tr);
      });
    }).catch(fail);
  };

  $('category-form').addEventListener('submit', function (e) {
    e.preventDefault();
    api.post('/api/blogs/' + blog.id + '/categories', { name: $('category-name').value }, { activity: true })
      .then(function () { $('category-name').value = ''; say('추가했습니다.'); loaders.categories(); })
      .catch(fail);
  });

  loaders.owner = function () {
    var closing = blog.status === 'CLOSING';
    $('close').hidden = closing;
    // 관리자가 강제 폐쇄한 블로그는 블로그장이 철회할 수 없다(ADM-02, BLG-09, D-104)
    var adminClose = closing && blog.closeReason === 'ADMIN';
    $('withdraw-close').hidden = !closing || blog.closeReason === 'OWNER_REVOKED' || adminClose;
    api.setText($('closing-info'), adminClose
      ? time(blog.closeScheduledAt) + '에 폐쇄됩니다. 관리자가 폐쇄한 블로그입니다. 철회는 메인 관리자에게 문의하세요.'
      : closing
        ? time(blog.closeScheduledAt) + '에 폐쇄됩니다. 그 전까지 철회할 수 있습니다.'
        : '누른 날짜로부터 7일 뒤 새벽 4시에 폐쇄됩니다. 그 전까지 철회할 수 있습니다.');
    $('delegate-box').hidden = !blog.canDelegate;
    if (!blog.canDelegate) { return; }
    api.get(base() + '/members?size=30').then(function (res) {
      var target = $('delegate-target');
      target.textContent = '';
      res.items.filter(function (m) { return m.role !== 'OWNER'; }).forEach(function (m) {
        var opt = el('option', m.nickname + ' (' + ROLES[m.role] + ')');
        opt.value = m.userId;
        target.appendChild(opt);
      });
    }).catch(fail);
    api.get(base() + '/delegation').then(function (d) {
      api.setText($('delegate-pending'), d ? d.toNickname + '님의 응답을 기다리는 중입니다(' + time(d.expiresAt) + '까지).' : '');
      $('delegate').disabled = !!d;
    }).catch(function () { api.setText($('delegate-pending'), ''); });
  };

  $('delegate').addEventListener('click', function () {
    var to = Number($('delegate-target').value);
    if (!to || !confirm('위임을 요청할까요?')) { return; }
    api.post(base() + '/delegation', { toUserId: to }, { activity: true })
      .then(function () { say('위임을 요청했습니다.'); loaders.owner(); }).catch(fail);
  });

  $('close').addEventListener('click', function () {
    if (!confirm('7일 뒤 새벽 4시에 블로그가 폐쇄됩니다. 멤버와 구독자에게 알림이 갑니다. 진행할까요?')) { return; }
    api.post(base() + '/closing', null, { activity: true })
      .then(function (b) { blog = b; say('폐쇄를 예약했습니다.'); loaders.owner(); }).catch(fail);
  });

  $('withdraw-close').addEventListener('click', function () {
    api.del(base() + '/closing', null, { activity: true })
      .then(function (b) { blog = b; say('폐쇄를 철회했습니다.'); loaders.owner(); }).catch(fail);
  });

  function open(tab) {
    document.querySelectorAll('[data-tab]').forEach(function (b) {
      b.setAttribute('aria-selected', b.getAttribute('data-tab') === tab ? 'true' : 'false');
    });
    document.querySelectorAll('[data-panel]').forEach(function (p) {
      p.hidden = p.getAttribute('data-panel') !== tab;
    });
    say('');
    loaders[tab]();
  }

  document.addEventListener('DOMContentLoaded', function () {
    tagInput = blogTags.attach($('tag-input'), $('tags'), 10, function (text) { say(text, true); });
    $('back').href = '/blog/' + encodeURIComponent(address);
    api.get('/api/blogs/address/' + encodeURIComponent(address)).then(function (b) {
      blog = b;
      api.setText($('title'), blog.name + ' 운영');
      var first = null;
      document.querySelectorAll('[data-tab]').forEach(function (btn) {
        var visible = has(btn.getAttribute('data-perm'));
        btn.hidden = !visible;
        if (visible && !first) { first = btn.getAttribute('data-tab'); }
        btn.addEventListener('click', function () { open(btn.getAttribute('data-tab')); });
      });
      if (!first) {
        api.setText($('message'), '이 블로그를 운영할 권한이 없습니다.');
        return;
      }
      // 알림에서 온 주소의 ?tab= 값(예: requests)이 권한 있는 탭이면 그 탭을 먼저 연다(003 T013, research R-06)
      var wanted = new URLSearchParams(location.search).get('tab');
      var wantedBtn = wanted ? document.querySelector('[data-tab="' + wanted.replace(/[^a-z]/g, '') + '"]') : null;
      open(wantedBtn && !wantedBtn.hidden ? wantedBtn.getAttribute('data-tab') : first);
    }, function (err) {
      if (err.status === 401) {
        location.href = '/login.html?next=' + encodeURIComponent(location.pathname);
        return;
      }
      api.setText($('message'), err.status === 404 ? '블로그를 찾을 수 없습니다.' : err.message);
    });
  });
})();
