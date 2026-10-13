package com.mainblog.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간 기준은 한국 시간(REQ-001). 시간을 쓰는 코드는 모두 {@code LocalDateTime.now(clock)}을 써서
 * 테스트에서 10분·30분·14일 경계를 옮겨 볼 수 있게 한다 (T007, R-14).
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
