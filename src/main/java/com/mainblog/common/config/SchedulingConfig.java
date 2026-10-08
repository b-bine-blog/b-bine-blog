package com.mainblog.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 예약 작업(요청 제한 키 정리, 04:00 배치 R-13)을 켠다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
