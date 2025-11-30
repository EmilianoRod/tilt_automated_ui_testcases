package listeners;

import org.testng.IAnnotationTransformer;
import org.testng.IRetryAnalyzer;
import org.testng.annotations.ITestAnnotation;
import org.testng.internal.annotations.DisabledRetryAnalyzer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class RetryTransformer implements IAnnotationTransformer {

    @Override
    @SuppressWarnings("rawtypes")   // TestNG uses raw types here
    public void transform(ITestAnnotation annotation,
                          Class testClass,
                          Constructor testConstructor,
                          Method testMethod) {

        final int retryMax = resolveRetryMax();
        final Class<? extends IRetryAnalyzer> existing = annotation.getRetryAnalyzerClass();
        final String methodName =
                (testMethod != null ? testMethod.getDeclaringClass().getSimpleName() +
                        "." + testMethod.getName() : "<no-method>");

        // --- Case 1: retries globally disabled --------------------------------
        if (retryMax <= 0) {
            if (existing != null && existing != IRetryAnalyzer.class) {
                System.out.println("[RetryTransformer] retry=0 → keeping existing analyzer "
                        + existing.getSimpleName() + " for " + methodName);
            } else {
                System.out.println("[RetryTransformer] retry=0 → no retry analyzer for "
                        + methodName);
            }
            return;
        }

        // --- Case 2: test already has a *custom* analyzer ---------------------
        // Respect any analyzer that is not default, not our RetryAnalyzer,
        // and not DisabledRetryAnalyzer.
        if (existing != null
                && existing != IRetryAnalyzer.class
                && existing != RetryAnalyzer.class
                && existing != DisabledRetryAnalyzer.class) {

            System.out.println("[RetryTransformer] " + methodName
                    + " already defines custom retryAnalyzer → keeping "
                    + existing.getSimpleName());
            return;
        }

        // --- Case 3: no analyzer, default analyzer, or DisabledRetryAnalyzer --
        // We apply our RetryAnalyzer so retries (and video on retry) are enabled.
        annotation.setRetryAnalyzer(RetryAnalyzer.class);

        System.out.println("[RetryTransformer] Applied RetryAnalyzer → " + methodName
                + " (retryMax=" + retryMax + ")");
    }

    private int resolveRetryMax() {
        String raw = System.getProperty("retry", "1");
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                System.out.println("[RetryTransformer] Negative retry='" + raw +
                        "', using 0 (no retries).");
                return 0;
            }
            return value;
        } catch (NumberFormatException e) {
            System.out.println("[RetryTransformer] Invalid retry='" + raw +
                    "', defaulting to 1");
            return 1;
        }
    }
}
