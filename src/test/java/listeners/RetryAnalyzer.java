package listeners;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

public class RetryAnalyzer implements IRetryAnalyzer {

    private int count = 0;
    private final int max;

    public RetryAnalyzer() {
        // Usage: -Dretry=1 (default 1). Set 0 to disable.
        this.max = Integer.parseInt(System.getProperty("retry", "1"));
    }

    @Override
    public boolean retry(ITestResult result) {
        return count++ < max;
    }



}
