// 메인 위쪽 띠의 소식 (2026-10-13 사용자 요청, D-116): 최신 공지, 인기 블로그, 인기 글을 번갈아 보여 준다.
// 글자는 textContent로만 넣는다(D-64). 불러오지 못한 항목은 빼고, 하나도 없으면 띠에 아무것도 더하지 않는다.
(function () {
  'use strict';

  var INTERVAL_MS = 5000;
  var box = document.getElementById('hero-spotlight');
  if (!box) { return; }

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) { node.className = className; }
    if (text != null) { node.textContent = text; }
    return node;
  }

  function first(url, pick) {
    return api.get(url).then(function (r) {
      var item = r && r.items && r.items[0];
      return item ? pick(item) : null;
    }, function () { return null; });
  }

  var sources = [
    first('/api/notices', function (p) {
      return { label: '최신 공지', title: p.title, href: '/notices/' + p.id };
    }),
    first('/api/blogs?sort=popular&page=1&size=10', function (b) {
      return { label: '인기 블로그', title: b.name, sub: '멤버 ' + b.memberCount + '명',
        href: '/blog/' + encodeURIComponent(b.address) };
    }),
    first('/api/feed?tab=POPULAR&page=1&size=10', function (p) {
      return { label: '인기 글', title: p.title, sub: p.blog ? p.blog.name : '',
        href: board.postUrl(p) };
    })
  ];

  Promise.all(sources).then(function (all) {
    var items = all.filter(Boolean);
    if (!items.length) { return; }
    var slides = items.map(function (it) {
      var a = el('a', 'spotlight__item');
      a.href = it.href;
      a.appendChild(el('span', 'spotlight__label', it.label));
      a.appendChild(el('span', 'spotlight__title', it.title));
      if (it.sub) { a.appendChild(el('span', 'spotlight__sub', it.sub)); }
      box.appendChild(a);
      return a;
    });
    var dots = el('div', 'spotlight__dots');
    var buttons = items.map(function (it, i) {
      var b = el('button', 'spotlight__dot');
      b.type = 'button';
      b.setAttribute('aria-label', it.label + ' 보기');
      b.addEventListener('click', function () { show(i); restart(); });
      dots.appendChild(b);
      return b;
    });
    if (items.length > 1) { box.appendChild(dots); }
    box.hidden = false;

    var current = 0;
    var timer = null;
    function show(i) {
      current = i;
      slides.forEach(function (s, k) {
        s.classList.toggle('is-active', k === i);
        s.setAttribute('aria-hidden', k === i ? 'false' : 'true');
        s.tabIndex = k === i ? 0 : -1;
      });
      buttons.forEach(function (b, k) { b.setAttribute('aria-current', k === i ? 'true' : 'false'); });
    }
    function restart() {
      if (timer) { clearInterval(timer); }
      if (items.length > 1) {
        timer = setInterval(function () { show((current + 1) % items.length); }, INTERVAL_MS);
      }
    }
    // 마우스를 올리거나 키보드로 고르는 동안은 멈춘다
    box.addEventListener('mouseenter', function () { if (timer) { clearInterval(timer); timer = null; } });
    box.addEventListener('mouseleave', restart);
    box.addEventListener('focusin', function () { if (timer) { clearInterval(timer); timer = null; } });
    box.addEventListener('focusout', restart);
    show(0);
    restart();
  });
})();
