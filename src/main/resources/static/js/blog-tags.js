// 블로그 태그 입력 칩 (T027, 6.4). 화면 정리는 미리보기이고 최종 판단은 서버가 한다.
(function (global) {
  'use strict';

  var ALLOWED = /^[가-힣a-z0-9_]{1,20}$/;

  function normalize(raw) {
    var s = (raw || '').trim().replace(/^#+/, '').trim().replace(/\s+/g, '_').toLowerCase();
    return ALLOWED.test(s) ? s : null;
  }

  // input에서 Enter·쉼표로 태그를 더하고 list(ul)에 그린다. get()으로 목록을 얻는다.
  function attach(input, list, max, onError) {
    var tags = [];

    function render() {
      while (list.firstChild) {
        list.removeChild(list.firstChild);
      }
      tags.forEach(function (tag, i) {
        var li = document.createElement('li');
        li.textContent = '#' + tag;
        var x = document.createElement('button');
        x.type = 'button';
        x.textContent = '×';
        x.setAttribute('aria-label', tag + ' 지우기');
        x.addEventListener('click', function () { tags.splice(i, 1); render(); });
        li.appendChild(x);
        list.appendChild(li);
      });
    }

    function add() {
      var tag = normalize(input.value);
      if (!input.value.trim()) {
        return;
      }
      if (!tag) {
        onError('태그는 1~20자 한글·영문·숫자·_만 쓸 수 있습니다.');
        return;
      }
      if (tags.indexOf(tag) < 0) {
        if (tags.length >= max) {
          onError('태그는 ' + max + '개까지 달 수 있습니다.');
          return;
        }
        tags.push(tag);
      }
      input.value = '';
      onError('');
      render();
    }

    input.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ',') {
        e.preventDefault();
        add();
      }
    });

    return {
      get: function () { add(); return tags.slice(); },
      set: function (values) { tags = (values || []).slice(); render(); }
    };
  }

  global.blogTags = { attach: attach, normalize: normalize };
})(window);
