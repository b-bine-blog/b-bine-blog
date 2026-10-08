// 공통 API 호출 (T020)
// - CSRF: XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보낸다(SEC-10, R-12).
// - 활동 표시: 사용자가 직접 한 행동이면 {activity: true}로 X-User-Activity: 1을 붙인다(D-62, R-04).
//   알림 폴링 같은 자동 요청에는 붙이지 않는다.
// - 401이면 POST /api/auth/refresh를 한 번 시도하고 원래 요청을 다시 보낸다(R-03).
// - 5초가 넘으면 중단하고 "다시 시도해 주세요"를 안내한다(4.2, SC-010).
// - 화면에 값을 넣을 때는 textContent만 쓴다(D-64).
(function (global) {
  'use strict';

  var TIMEOUT_MS = 5000;
  var TIMEOUT_MESSAGE = '응답이 늦어 요청을 멈췄습니다. 다시 시도해 주세요.';

  function readCookie(name) {
    var parts = document.cookie ? document.cookie.split('; ') : [];
    for (var i = 0; i < parts.length; i++) {
      var eq = parts[i].indexOf('=');
      if (parts[i].substring(0, eq) === name) {
        return decodeURIComponent(parts[i].substring(eq + 1));
      }
    }
    return null;
  }

  function ApiError(status, body) {
    this.name = 'ApiError';
    this.status = status;
    this.body = body || {};
    this.code = this.body.code || (status === 0 ? 'TIMEOUT' : 'UNKNOWN');
    this.message = this.body.message || (status === 0 ? TIMEOUT_MESSAGE : '요청을 처리하지 못했습니다. 다시 시도해 주세요.');
  }
  ApiError.prototype = Object.create(Error.prototype);

  function send(method, url, body, options) {
    var headers = { 'Accept': 'application/json' };
    var csrf = readCookie('XSRF-TOKEN');
    if (csrf) {
      headers['X-XSRF-TOKEN'] = csrf;
    }
    if (options.activity) {
      headers['X-User-Activity'] = '1';
    }
    var payload;
    if (body instanceof FormData) {
      payload = body;
    } else if (body !== undefined && body !== null) {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, TIMEOUT_MS);
    return fetch(url, {
      method: method,
      headers: headers,
      body: payload,
      credentials: 'same-origin',
      signal: controller.signal
    }).then(function (res) {
      clearTimeout(timer);
      return res;
    }, function (err) {
      clearTimeout(timer);
      throw new ApiError(0, null);
    });
  }

  function parse(res) {
    if (res.status === 204) {
      return Promise.resolve(null);
    }
    var type = res.headers.get('Content-Type') || '';
    var body = type.indexOf('application/json') >= 0 ? res.json() : Promise.resolve(null);
    return body.then(function (data) {
      if (res.ok) {
        return data;
      }
      throw new ApiError(res.status, data);
    });
  }

  var refreshing = null;

  function refreshOnce() {
    if (!refreshing) {
      refreshing = send('POST', '/api/auth/refresh', null, {}).then(function (res) {
        refreshing = null;
        return res.ok;
      }, function () {
        refreshing = null;
        return false;
      });
    }
    return refreshing;
  }

  function request(method, url, body, options) {
    options = options || {};
    return send(method, url, body, options).then(function (res) {
      var retryable = res.status === 401 && !options.noRefresh && url.indexOf('/api/auth/') !== 0;
      if (!retryable) {
        return parse(res);
      }
      return refreshOnce().then(function (ok) {
        if (!ok) {
          return parse(res);
        }
        return send(method, url, body, options).then(parse);
      });
    });
  }

  // 화면에 글자를 넣는 유일한 방법. innerHTML은 글 본문 외에는 쓰지 않는다(D-64).
  function setText(el, value) {
    if (el) {
      el.textContent = value === undefined || value === null ? '' : String(value);
    }
  }

  global.api = {
    get: function (url, options) { return request('GET', url, null, options); },
    post: function (url, body, options) { return request('POST', url, body, options); },
    put: function (url, body, options) { return request('PUT', url, body, options); },
    patch: function (url, body, options) { return request('PATCH', url, body, options); },
    del: function (url, body, options) { return request('DELETE', url, body, options); },
    setText: setText,
    ApiError: ApiError,
    TIMEOUT_MESSAGE: TIMEOUT_MESSAGE
  };
})(window);
