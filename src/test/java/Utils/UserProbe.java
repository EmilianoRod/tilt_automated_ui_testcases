package Utils;

import java.time.Duration;

public final class UserProbe {
    private static final int MAX_ATTEMPTS = 8;
    private static final Duration SLEEP = Duration.ofSeconds(5);

    @FunctionalInterface
    public interface Finder {
        boolean byEmail(String email) throws Exception;
    }

    /** Polls using the provided finder (UI or API) until user appears or timeout reached. */
    public static boolean waitUntilUserExists(String email, Finder finder) {
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            try {
                if (finder.byEmail(email)) return true;
            } catch (Exception ignored) {
                // Optionally log
            }
            try { Thread.sleep(SLEEP.toMillis()); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
