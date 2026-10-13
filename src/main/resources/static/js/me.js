// 내 정보 화면 (T064, USR-07, FR-040~FR-043). 값은 textContent·value로만 넣는다(D-64).
// 003 T020: 소개 고치기(PUT /api/members/me/bio, 0~500자), 내 프로필·알림 설정·차단 목록 링크.
(function () {
  'use strict';

  var MAX_IMAGE_BYTES = 3 * 1024 * 1024; // 서버도 같은 값으로 다시 확인한다(6.3)
  var $ = function (id) { return document.getElementById(id); };

  var NICKNAME_REASONS = {
    DUPLICATE: '이미 쓰고 있는 닉네임입니다.',
    RESERVED: '쓸 수 없는 단어가 들어 있습니다.',
    INVALID_FORMAT: '2~12자 한글·영문·숫자로 입력해 주세요.'
  };

  function show(el, message, isError) {
    api.setText(el, message);
    el.className = 'message ' + (isError ? 'message--error' : 'message--info');
  }

  function errorMessage(err) {
    if (err.code === 'VALIDATION_FAILED' && err.body.fieldErrors && err.body.fieldErrors.length) {
      var f = err.body.fieldErrors[0];
      if (f.field === 'phone') {
        return '010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요.';
      }
      if (f.field === 'nickname') {
        return f.code === 'Reserved' ? NICKNAME_REASONS.RESERVED : NICKNAME_REASONS.INVALID_FORMAT;
      }
    }
    if (err.code === 'FILE_TOO_LARGE') {
      return '사진은 3MB 이하만 올릴 수 있습니다.';
    }
    if (err.code === 'FILE_TYPE_NOT_ALLOWED') {
      return 'jpg·jpeg·png·gif·webp 사진만 올릴 수 있습니다.';
    }
    return err.message;
  }

  function formatPhone(digits) {
    return digits && digits.length === 11
      ? digits.substring(0, 3) + '-' + digits.substring(3, 7) + '-' + digits.substring(7) : (digits || '');
  }

  function formatDate(iso) {
    return iso ? iso.replace(/-/g, '.') : '';
  }

  function renderImage(url) {
    var img = $('profile-image');
    if (url) {
      img.src = url;
      img.hidden = false;
      $('no-profile-image').hidden = true;
      $('delete-image').hidden = false;
    } else {
      img.removeAttribute('src');
      img.hidden = true;
      $('no-profile-image').hidden = false;
      $('delete-image').hidden = true;
    }
  }

  function render(me) {
    api.setText($('email'), me.email);
    api.setText($('name'), me.name);
    api.setText($('joined-on'), formatDate(me.joinedOn));
    $('nickname').value = me.nickname;
    $('phone').value = formatPhone(me.phone);
    renderImage(me.profileImageUrl);
    $('bio').value = me.bio || '';
    updateBioLength();
    $('info').hidden = false;
  }

  // 글자 수는 서버와 같이 코드 포인트로 센다(이모지 하나 = 1자)
  function updateBioLength() {
    api.setText($('bio-length'), Array.from($('bio').value.trim()).length);
  }

  function saveBio(e) {
    e.preventDefault();
    $('save-bio').disabled = true;
    api.put('/api/members/me/bio', { bio: $('bio').value }, { activity: true }).then(function (r) {
      $('save-bio').disabled = false;
      $('bio').value = r.bio || '';
      updateBioLength();
      show($('bio-message'), '소개를 저장했습니다.', false);
    }, function (err) {
      $('save-bio').disabled = false;
      show($('bio-message'), err.code === 'VALIDATION_FAILED' ? '소개는 500자까지 쓸 수 있습니다.' : err.message, true);
    });
  }

  function load() {
    api.get('/api/members/me', { activity: true }).then(render, function (err) {
      if (err.status === 401) {
        location.href = '/login.html?next=' + encodeURIComponent(location.pathname);
        return;
      }
      show($('message'), err.message, true);
    });
  }

  function checkNickname() {
    var nickname = $('nickname').value.trim();
    api.get('/api/members/nickname-availability?nickname=' + encodeURIComponent(nickname), { activity: true })
      .then(function (res) {
        show($('edit-message'), res.available ? '쓸 수 있는 닉네임입니다.' : NICKNAME_REASONS[res.reason],
          !res.available);
      }, function (err) {
        show($('edit-message'), errorMessage(err), true);
      });
  }

  function save(e) {
    e.preventDefault();
    $('save').disabled = true;
    api.patch('/api/members/me', { nickname: $('nickname').value.trim(), phone: $('phone').value.trim() },
      { activity: true }).then(function (me) {
      $('save').disabled = false;
      render(me);
      show($('edit-message'), '저장했습니다.', false);
    }, function (err) {
      $('save').disabled = false;
      show($('edit-message'), errorMessage(err), true);
    });
  }

  function uploadImage() {
    var file = $('profile-file').files[0];
    if (!file) {
      return;
    }
    if (file.size > MAX_IMAGE_BYTES) {
      show($('image-message'), '사진은 3MB 이하만 올릴 수 있습니다.', true);
      $('profile-file').value = '';
      return;
    }
    var form = new FormData();
    form.append('file', file);
    api.put('/api/members/me/profile-image', form, { activity: true }).then(function (res) {
      renderImage(res.profileImageUrl);
      show($('image-message'), '사진을 바꿨습니다.', false);
    }, function (err) {
      show($('image-message'), errorMessage(err), true);
    }).finally(function () {
      $('profile-file').value = '';
    });
  }

  function deleteImage() {
    api.del('/api/members/me/profile-image', null, { activity: true }).then(function () {
      renderImage(null);
      show($('image-message'), '사진을 지웠습니다.', false);
    }, function (err) {
      show($('image-message'), errorMessage(err), true);
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    load();
    $('check-nickname').addEventListener('click', checkNickname);
    $('edit-form').addEventListener('submit', save);
    $('profile-file').addEventListener('change', uploadImage);
    $('delete-image').addEventListener('click', deleteImage);
    $('bio').addEventListener('input', updateBioLength);
    $('bio-form').addEventListener('submit', saveBio);
    api.get('/api/auth/session').then(function (s) { $('my-profile').href = '/profile/' + s.memberId; },
      function () { /* 로그인하지 않았으면 load()가 로그인 화면으로 보낸다 */ });
  });
})();
