package com.mainblog.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 테스트에서 시간을 옮기는 시계(R-14). 10분·30분·14일 경계를 실제로 기다리지 않고 확인한다. */
public class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void advance(Duration duration) {
        this.instant = instant.plus(duration);
    }

    public void setTo(LocalDateTime dateTime) {
        this.instant = dateTime.atZone(zone).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    /** 지금 시각(이 시계의 시간대 기준). */
    public java.time.LocalDateTime now() {
        return java.time.LocalDateTime.now(this);
    }
}
