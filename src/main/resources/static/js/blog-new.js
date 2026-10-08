// 블로그 만들기 (T027, FR-001~FR-007). 주소 형식은 미리 보여 주기만 하고 서버가 최종 판단한다.
(function () {
  'use strict';

  var ADDRESS = /^[a-z0-9-]{3,30}$/;
  var $ = function (id) { return document.getElementById(id); };
  var message = $('message');

  function showError(text) {
    api.setText(message, text);
  }

  var tagInput = blogTags.attach($('tag-input'), $('tags'), 10, showError);

  var FIELD_MESSAGES = {
    address: '주소는 영문 소문자·숫자·- 3~30자이고, main·admin 같은 예약어는 쓸 수 없습니다.',
    name: '블로그 이름은 1~30자로 써 주세요.',
    description: '소개는 500자까지 쓸 수 있습니다.',
    tags: '태그는 10개까지, 1~20자 한글·영문·숫자·_만 쓸 수 있습니다.'
  };

  function errorText(err) {
    var fields = (err.body && err.body.fieldErrors) || [];
    if (fields.length && FIELD_MESSAGES[fields[0].field]) {
      return FIELD_MESSAGES[fields[0].field];
    }
    return err.message;
  }

  $('create-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var address = $('address').value.trim();
    if (!ADDRESS.test(address)) {
      showError(FIELD_MESSAGES.address);
      return;
    }
    var body = {
      name: $('name').value,
      address: address,
      description: $('description').value,
      tags: tagInput.get(),
      visibility: $('visibility').value,
      joinPolicy: $('join-policy').value
    };
    $('submit').disabled = true;
    showError('');
    api.post('/api/blogs', body, { activity: true })
      .then(function (blog) {
        var file = $('cover').files[0];
        if (!file) {
          return blog;
        }
        var form = new FormData();
        form.append('file', file);
        return api.put('/api/blogs/' + blog.id + '/cover-image', form, { activity: true })
          .then(function () { return blog; }, function () { return blog; });
      })
      .then(function (blog) {
        location.href = '/blog/' + encodeURIComponent(blog.address);
      })
      .catch(function (err) {
        if (err.status === 401) {
          location.href = '/login.html';
          return;
        }
        showError(errorText(err));
        $('submit').disabled = false;
      });
  });
})();
