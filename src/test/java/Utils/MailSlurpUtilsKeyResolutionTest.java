package Utils;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Zero-network checks for MailSlurp API key precedence.
 * NOTE: We only toggle System properties here; env vars are read-only at runtime.
 * Config.* may read local files, but system properties take precedence, which we set explicitly.
 */
@Test(singleThreaded = true)
public class MailSlurpUtilsKeyResolutionTest {

    @BeforeMethod
    public void clearBefore() {
        clearProps();
    }

    @AfterMethod
    public void clearAfter() {
        clearProps();
    }

    private static void clearProps() {
        System.clearProperty("mailslurp.forceKey");
        System.clearProperty("mailslurp.apiKey");
        System.clearProperty("MAILSLURP_API_KEY");
        System.clearProperty("mailslurp.expectedFingerprint");
        System.clearProperty("mailslurp.basePath");
    }

    @Test
    public void testForceKeyBeatsEverything() {
        System.setProperty("mailslurp.apiKey", "api-2");
        System.setProperty("MAILSLURP_API_KEY", "api-3");
        System.setProperty("mailslurp.forceKey", "api-1");

        String resolved = MailSlurpUtils._resolveApiKeyForTestOnly();
        Assert.assertEquals(resolved, "api-1", "forceKey must take precedence");
    }

    @Test
    public void testApiKeyBeatsUppercaseVariant() {
        System.setProperty("mailslurp.apiKey", "api-lower");
        System.setProperty("MAILSLURP_API_KEY", "api-upper");

        String resolved = MailSlurpUtils._resolveApiKeyForTestOnly();
        Assert.assertEquals(resolved, "api-lower", "mailslurp.apiKey should beat MAILSLURP_API_KEY");
    }

    @Test
    public void testUppercaseUsedWhenLowerMissing() {
        System.setProperty("MAILSLURP_API_KEY", "api-upper");

        String resolved = MailSlurpUtils._resolveApiKeyForTestOnly();
        Assert.assertEquals(resolved, "api-upper", "MAILSLURP_API_KEY should be used when lower not set");
    }

    @Test
    public void testFingerprintMismatchReturnsSentinel() {
        System.setProperty("mailslurp.apiKey", "some-secret-value");
        // Force mismatch (first 12 hex of SHA-256 won't equal this)
        System.setProperty("mailslurp.expectedFingerprint", "deadbeefdead");

        String resolved = MailSlurpUtils._resolveApiKeyForTestOnly();
        Assert.assertEquals(resolved, "__FINGERPRINT_MISMATCH__", "Mismatch should be signaled");
    }

    @Test
    public void testBasePathDerivation() {
        Assert.assertEquals(MailSlurpUtils._basePathForTestOnly(), "https://api.mailslurp.com");
        System.setProperty("mailslurp.basePath", "https://example.com/base");
        Assert.assertEquals(MailSlurpUtils._basePathForTestOnly(), "https://example.com/base");
    }

    @Test
    public void testNothingSetReturnsNull() {
        String resolved = MailSlurpUtils._resolveApiKeyForTestOnly();
        Assert.assertNull(resolved, "When nothing is set, the helper should return null");
    }

    @Test
    public void testForceKeyWithFingerprintMismatchStillSignalsMismatch() {
        System.setProperty("mailslurp.forceKey", "force-secret");
        System.setProperty("mailslurp.expectedFingerprint", "cafebabecafe"); // wrong on purpose

        String resolved = MailSlurpUtils._resolveApiKeyForTestOnly();
        Assert.assertEquals(resolved, "__FINGERPRINT_MISMATCH__",
                "Fingerprint mismatch must be surfaced even when forceKey is set");
    }
}

