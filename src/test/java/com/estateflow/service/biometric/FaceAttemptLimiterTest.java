package com.estateflow.service.biometric;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class FaceAttemptLimiterTest {
    @Test void throttlesCaseInsensitiveFaceAttemptsAndExpiresWithoutAffectingPasswordPath() {
        var clock=new MutableClock(); var limiter=new FaceAttemptLimiter(clock);
        for(int i=0;i<5;i++)limiter.failure("Agent");
        assertFalse(limiter.mayAttempt("agent"));
        clock.advanceSeconds(901);
        assertTrue(limiter.mayAttempt("AGENT"));
        limiter.failure("agent"); limiter.success("AGENT");
        assertTrue(limiter.mayAttempt("agent"));
    }

    private static final class MutableClock extends Clock {
        private Instant now=Instant.parse("2026-10-26T00:00:00Z");
        void advanceSeconds(long seconds){now=now.plusSeconds(seconds);}
        @Override public java.time.ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(java.time.ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
}
