package listeners;

import io.qameta.allure.Allure;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

public class RetryAnalyzer implements IRetryAnalyzer {

    /**
     * How many times to retry a failed test.
     *  -Dretry=1  (default)  → first run + 1 retry   = up to 2 executions
     *  -Dretry=0            → retries disabled       = 1 execution only
     *  You can also use -Dretry.max as an alias.
     */
    private final int max;

    /**
     * Per-test counter (how many retries have already been scheduled for THIS test instance).
     * First time retry() is called → count = 0.
     */
    private int count = 0;

    // Attribute keys for other listeners (TestListener, video recorder, etc.)
    public static final String ATTR_RETRY_INDEX      = "retryIndex";       // 1..max (which retry is being scheduled)
    public static final String ATTR_MAX_RETRY        = "maxRetryCount";
    public static final String ATTR_NEXT_RUN_NUMBER  = "nextRunNumber";    // 2..(max+1) (execution index)
    public static final String ATTR_TOTAL_RUNS       = "totalRuns";        // max+1
    public static final String ATTR_RETRY_SCHEDULED  = "retryScheduled";   // true/false

    public RetryAnalyzer() {
        this.max = resolveMaxRetries();
        System.out.println("[RetryAnalyzer] Configured max retries = " + max);
    }

    private int resolveMaxRetries() {
        // Prefer -Dretry, but allow -Dretry.max as a fallback
        String raw = System.getProperty("retry",
                System.getProperty("retry.max", "1"));

        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                System.out.println("[RetryAnalyzer] Negative retry value " + value + " → using 0.");
                return 0;
            }
            return value;
        } catch (Exception e) {
            System.out.println("[RetryAnalyzer] Invalid retry value '" + raw + "', defaulting to 1.");
            return 1;
        }
    }

    @Override
    public boolean retry(ITestResult result) {
        int currentRetryCount = count; // how many retries already used for this test

        if (currentRetryCount < max) {
            // We ARE going to retry
            count++;

            // retryIndex = 1,2,... (which retry we are scheduling)
            int retryIndex = currentRetryCount + 1;
            // total number of executions (first run + retries)
            int totalPossibleRuns = max + 1;
            // next run number (2..totalPossibleRuns)
            int nextRunNumber = retryIndex + 1;

            // Store metadata on this failure result (useful for reporting)
            result.setAttribute(ATTR_RETRY_INDEX, retryIndex);
            result.setAttribute(ATTR_MAX_RETRY, max);
            result.setAttribute(ATTR_NEXT_RUN_NUMBER, nextRunNumber);
            result.setAttribute(ATTR_TOTAL_RUNS, totalPossibleRuns);
            result.setAttribute(ATTR_RETRY_SCHEDULED, Boolean.TRUE);

            String attemptLabel = nextRunNumber + "/" + totalPossibleRuns; // “2/2”, “3/4”, etc.

            // Best-effort: annotate in Allure that a retry is scheduled
            try {
                Allure.label("retryScheduled", attemptLabel);
                Allure.label("flaky", "true"); // mark test as flaky when a retry is used
                Allure.addAttachment(
                        "Retry info",
                        "text/plain",
                        "Test: " + result.getName() + "\nNext execution: " + attemptLabel +
                                "\nRetry index: " + retryIndex + " of " + max
                );
            } catch (Throwable ignored) {
                // Never break retries because of reporting
            }

            System.out.printf(
                    "[RetryAnalyzer] Scheduling retry %d/%d → next run %s for test %s%n",
                    retryIndex,
                    max,
                    attemptLabel,
                    result.getName()
            );

            return true; // ✅ tell TestNG to schedule a retry
        }

        // No more retries
        result.setAttribute(ATTR_RETRY_SCHEDULED, Boolean.FALSE);

        System.out.printf(
                "[RetryAnalyzer] No more retries left for test %s (max=%d)%n",
                result.getName(),
                max
        );
        return false;
    }
}
