package com.mainblog.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 요청 IP (T019, R-06). X-Forwarded-For는 누구나 꾸밀 수 있어 직접 읽지 않는다.
 * 프록시 뒤에 둘 때는 server.forward-headers-strategy로 getRemoteAddr()가 실제 IP를 돌려주게 한다.
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
