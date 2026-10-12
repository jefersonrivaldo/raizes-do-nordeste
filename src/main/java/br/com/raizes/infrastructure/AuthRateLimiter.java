package br.com.raizes.infrastructure;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Per-connection-address fixed windows for the public authentication endpoints. */
public final class AuthRateLimiter {
  private static final int MAX_TRACKED_CLIENTS = 10_000;

  private final int maxRequests;
  private final long windowMillis;
  private final Clock clock;
  private final LinkedHashMap<String, Window> clients = new LinkedHashMap<>();
  private long lastObservedTime = Long.MIN_VALUE;

  public AuthRateLimiter(int maxRequests, long windowSeconds, Clock clock) {
    if (maxRequests < 1) {
      throw new IllegalArgumentException("app.auth-rate-limit-max-requests deve ser positivo.");
    }
    if (windowSeconds < 1 || windowSeconds > Long.MAX_VALUE / 1000) {
      throw new IllegalArgumentException(
          "app.auth-rate-limit-window-seconds deve ser positivo e caber em milissegundos.");
    }
    this.maxRequests = maxRequests;
    this.windowMillis = windowSeconds * 1000;
    this.clock = Objects.requireNonNull(clock);
  }

  public synchronized Decision check(String clientAddress) {
    Objects.requireNonNull(clientAddress);
    // Preserve window ordering if the system clock moves backwards.
    long now = Math.max(lastObservedTime, clock.millis());
    lastObservedTime = now;
    var entries = clients.entrySet().iterator();
    while (entries.hasNext()) {
      if (entries.next().getValue().expiresAt > now) break;
      entries.remove();
    }

    var window = clients.get(clientAddress);
    if (window == null) {
      // Do not evict active clients: otherwise rotating addresses could reset their budget.
      if (clients.size() >= MAX_TRACKED_CLIENTS) {
        return denied(clients.firstEntry().getValue().expiresAt, now);
      }
      long expiresAt =
          now > Long.MAX_VALUE - windowMillis ? Long.MAX_VALUE : now + windowMillis;
      clients.put(clientAddress, new Window(expiresAt));
      return new Decision(true, 0);
    }
    if (window.requests >= maxRequests) return denied(window.expiresAt, now);
    window.requests++;
    return new Decision(true, 0);
  }

  private static Decision denied(long expiresAt, long now) {
    long remainingMillis = expiresAt - now;
    long seconds = remainingMillis / 1000 + (remainingMillis % 1000 == 0 ? 0 : 1);
    return new Decision(false, Math.max(1, seconds));
  }

  public record Decision(boolean allowed, long retryAfterSeconds) {}

  private static final class Window {
    private final long expiresAt;
    private int requests = 1;

    private Window(long expiresAt) {
      this.expiresAt = expiresAt;
    }
  }
}
