// 글쓰기·수정 (004 T021·T034, FR-002~FR-004, FR-014, FR-029, D-62, D-74, research R-02).
// /blog/{주소}/write, /blog/{주소}/posts/{번호}/edit, /notices/write, /notices/{번호}/edit 가 같이 쓴다.
// - Toast UI 마크다운 에디터. 이미지 버튼·붙여넣기는 업로드 API를 부르고 /files/ 주소를 넣는다.
// - 입력이 멈추고 1초 뒤 localStorage에 자동 저장하고, 저장에 성공하면 지운다(다른 기기에서는 이어 쓸 수 없음).
// - 입력이 있으면 5분마다 로그인을 연장한다(session.startEditorKeepAlive).
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var TITLE_MAX = 30;
  var CONTENT_MAX = 5000;
  var parts = location.pathname.split('/');
  var isNotice = parts[1] === 'notices';
  var address = isNotice ? null : decodeURIComponent(parts[2] || '');
  var editId = isNotice ? (parts[3] === 'edit' ? parts[2] : null) : (parts[3] === 'posts' ? parts[4] : null);
  var blog = null;
  var editor = null;
  var draftKey = null;
  var saveTimer = null;

  function fail(err) {
    board.sayError($('message'), err, function () { location.reload(); });
  }

  function count(text) {
    return Array.from(text || '').length; // 이모지도 한 글자(D-92)
  }

  function updateCounters() {
    api.setText($('title-length'), count($('title').value));
    var n = count(editor.getMarkdown());
    api.setText($('content-length'), n.toLocaleString());
    $('content-length').parentNode.classList.toggle('message--error', n > CONTENT_MAX);
  }

  function uploadUrl() {
    return isNotice ? '/api/notices/images' : '/api/blogs/' + blog.id + '/images';
  }

  function createEditor(initial) {
    editor = new toastui.Editor({
      el: $('editor'),
      height: '420px',
      initialEditType: 'markdown',
      previewStyle: window.innerWidth < 768 ? 'tab' : 'vertical',
      usageStatistics: false,
      initialValue: initial || '',
      hooks: {
        addImageBlobHook: function (blob, callback) {
          var form = new FormData();
          form.append('files', blob, blob.name || 'image.png');
          api.post(uploadUrl(), form, { activity: true }).then(function (r) {
            callback(r.images[0].url, blob.name || '');
          }, function (err) { board.say($('message'), err.message, true); });
        }
      },
      events: { change: function () { updateCounters(); scheduleDraft(); } }
    });
    session.startEditorKeepAlive($('editor'));
  }

  function readDraft() {
    try { return JSON.parse(localStorage.getItem(draftKey) || 'null'); } catch (e) { return null; }
  }

  function scheduleDraft() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(function () {
      try {
        localStorage.setItem(draftKey, JSON.stringify({
          title: $('title').value, content: editor.getMarkdown(), tags: $('tags').value,
          categoryId: $('category').value, savedAt: new Date().toISOString()
        }));
        api.setText($('draft-info'), '임시 저장됨 ' + new Date().toLocaleTimeString());
        $('discard-draft').hidden = false;
      } catch (e) { /* 저장 공간이 없으면 넘어간다 */ }
    }, 1000);
  }

  function clearDraft() {
    try { localStorage.removeItem(draftKey); } catch (e) { /* 무시 */ }
  }

  function fill(values) {
    $('title').value = values.title || '';
    $('tags').value = values.tags || '';
    if (values.categoryId) { $('category').value = String(values.categoryId); }
    createEditor(values.content || '');
    updateCounters();
    $('write-form').hidden = false;
  }

  function start(existing) {
    var draft = readDraft();
    var base = existing ? {
      title: existing.title, content: existing.content, tags: (existing.tags || []).join(', '),
      categoryId: existing.categoryId
    } : {};
    if (draft && confirm('임시 저장한 글이 있습니다. 이어서 쓸까요?')) {
      fill(draft);
      $('discard-draft').hidden = false;
    } else {
      fill(base);
    }
  }

  function loadCategories() {
    return api.get('/api/blogs/' + blog.id + '/categories').then(function (r) {
      r.categories.forEach(function (c) {
        var o = document.createElement('option');
        o.value = String(c.id);
        o.textContent = c.name;
        $('category').appendChild(o);
      });
    });
  }

  function init() {
    if (isNotice) {
      $('category-box').hidden = true;
      $('tag-box').hidden = true;
      $('back').href = '/notices';
      $('back').textContent = '공지사항';
      draftKey = editId ? 'draft:post:' + editId : 'draft:notice';
      if (editId) {
        $('heading').textContent = '공지 수정';
        return api.get('/api/posts/' + encodeURIComponent(editId)).then(start, fail);
      }
      start(null);
      return Promise.resolve();
    }
    return api.get('/api/blogs/address/' + encodeURIComponent(address)).then(function (b) {
      blog = b;
      $('back').href = '/blog/' + encodeURIComponent(b.address);
      $('back').textContent = b.name;
      draftKey = editId ? 'draft:post:' + editId : 'draft:' + b.id;
      return loadCategories().then(function () {
        if (editId) {
          $('heading').textContent = '글 수정';
          return api.get('/api/posts/' + encodeURIComponent(editId)).then(function (p) {
            if (!p.canEdit) { throw { message: '이 글을 고칠 수 없습니다.' }; }
            start(p);
          });
        }
        start(null);
      });
    }).catch(fail);
  }

  $('title').addEventListener('input', function () { updateCounters(); scheduleDraft(); });
  $('tags').addEventListener('input', scheduleDraft);
  $('category').addEventListener('change', scheduleDraft);
  $('discard-draft').addEventListener('click', function () {
    clearDraft();
    $('discard-draft').hidden = true;
    api.setText($('draft-info'), '임시 저장을 지웠습니다.');
  });

  $('write-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var title = $('title').value.trim();
    var content = editor.getMarkdown();
    if (!title || count(title) > TITLE_MAX) { board.say($('message'), '제목은 1~30자로 입력해 주세요.', true); return; }
    if (!content.trim() || count(content) > CONTENT_MAX) {
      board.say($('message'), '본문은 1~5,000자로 입력해 주세요.', true);
      return;
    }
    var body = { title: title, content: content };
    if (!isNotice) {
      body.categoryId = $('category').value ? Number($('category').value) : null;
      body.tags = $('tags').value.split(',').map(function (t) { return t.trim(); }).filter(Boolean);
    }
    var req;
    if (editId) {
      req = api.put('/api/posts/' + encodeURIComponent(editId), body, { activity: true });
    } else {
      req = api.post(isNotice ? '/api/notices' : '/api/blogs/' + blog.id + '/posts', body, { activity: true });
    }
    $('submit').disabled = true;
    req.then(function (p) {
      clearTimeout(saveTimer);
      clearDraft();
      location.href = board.postUrl(p);
    }, function (err) {
      $('submit').disabled = false;
      var field = err.body && err.body.fieldErrors && err.body.fieldErrors[0];
      var names = { title: '제목', content: '본문', tags: '태그', categoryId: '카테고리' };
      board.say($('message'), field ? names[field.field] + '을(를) 다시 확인해 주세요.' : err.message, true);
    });
  });

  init();
})();
