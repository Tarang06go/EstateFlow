package com.estateflow.service.biometric;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Per-process, per-device throttling. It affects face attempts only, never password sign-in. */
public final class FaceAttemptLimiter {
    private static final int MAX_FAILURES = 5, MAX_TRACKED_NAMES = 2048;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Clock clock;
    private final Map<String, Attempt> attempts = new LinkedHashMap<>(256,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Attempt> eldest) { return size()>MAX_TRACKED_NAMES; }
    };
    public FaceAttemptLimiter(Clock clock) { this.clock = clock; }
    public synchronized boolean mayAttempt(String username) {
        Attempt a = attempts.get(key(username));
        if (a == null || expired(a)) { attempts.remove(key(username)); return true; }
        return a.failures < MAX_FAILURES;
    }
    public synchronized void failure(String username) {
        String key=key(username); Instant now=clock.instant(); Attempt a=attempts.get(key);
        if(a==null || expired(a)) a=new Attempt(0,now);
        attempts.put(key,new Attempt(a.failures+1,a.start));
    }
    public synchronized void success(String username) { attempts.remove(key(username)); }
    private boolean expired(Attempt attempt) { return !clock.instant().isBefore(attempt.start.plus(WINDOW)); }
    private static String key(String username) { return username == null ? "" : username.trim().toLowerCase(Locale.ROOT); }
    private record Attempt(int failures, Instant start) { }
}
