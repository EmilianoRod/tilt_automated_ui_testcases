package pages.Shop.Stripe;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Runs a Playwright test that completes Stripe Hosted Checkout using a fresh CHECKOUT_URL.
 *
 * Usage:
 *   String url = preview.proceedToStripeAndGetCheckoutUrl();
 *   PlaywrightStripeBridge.pay(url, "qa+stripe@example.com");
 */
public final class PlaywrightStripeBridge {

    private PlaywrightStripeBridge() {}

    /** Quick entry point with sane defaults. Throws RuntimeException if Playwright exits non-zero. */
    public static void pay(String freshCheckoutUrl, String email) {
        Options opts = Options.defaultOptions()
                .setCheckoutUrl(Objects.requireNonNull(freshCheckoutUrl, "freshCheckoutUrl"))
                .setCheckoutEmail(email);
        pay(opts);
    }

    /** Main entry: runs Playwright and throws if the run did not succeed. */
    public static void pay(Options options) {
        Result r = payReturning(options);
        if (!r.isSuccess()) {
            throw new RuntimeException("Playwright checkout failed");
        }
        if (r.getSuccessUrl() == null) {
            System.out.println("[PW] (note) success URL not captured. Did you add the console.log marker in the spec?");
        }
    }

    /** Same as {@link #pay(Options)} but returns success + the final success URL if captured. */
    public static Result payReturning(Options options) {
        Objects.requireNonNull(options.checkoutUrl, "checkoutUrl is required");

        final boolean ci = isCi();
        final File wd = effectiveWorkingDirectory(options);

        // Build command from the effective WD
        final File oldWd = options.workingDirectory;
        options.setWorkingDirectory(wd);
        final List<String> cmd;
        try {
            cmd = buildCommand(options);
        } finally {
            options.setWorkingDirectory(oldWd);
        }

        // ---- Environment for Playwright process ----
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("PW_BRIDGE_WD", wd.getAbsolutePath());
        env.put("CHECKOUT_URL", options.checkoutUrl);
        if (options.checkoutEmail != null) env.put("CHECKOUT_EMAIL", options.checkoutEmail);

        // Never allow fake success in CI; allow locally only if explicitly asked
        if (Boolean.TRUE.equals(options.fakeSuccess) && !ci) {
            env.put("PW_STRIPE_FAKE_SUCCESS", "1");
        } else {
            env.remove("PW_STRIPE_FAKE_SUCCESS");
        }

        // Headed vs headless: prefer headed unless explicitly disabled
        if (Boolean.TRUE.equals(options.headed)) {
            env.put("PW_HEADLESS", "0");
        } else if (Boolean.FALSE.equals(options.headed)) {
            env.put("PW_HEADLESS", "1");
        }

        // Timeouts down to PW (ms)
        if (options.timeout != null && !options.timeout.isZero() && !options.timeout.isNegative()) {
            env.put("PW_TIMEOUT_MS", String.valueOf(options.timeout.toMillis()));
        }
        env.putIfAbsent("PW_EXPECT_TIMEOUT_MS", "5000");
        env.putIfAbsent("PW_NAV_TIMEOUT_MS", "15000");
        env.putIfAbsent("PW_ACTION_TIMEOUT_MS", "10000");

        // Coherent browser profile hints (matched by your run script / config)
        env.putIfAbsent("PW_LOCALE", "en-US");
        env.putIfAbsent("PW_TZ", "America/New_York");
        env.putIfAbsent("PW_ACCEPT_LANGUAGE", "en-US,en;q=0.9");

        final String SUCCESS_MARK = "PW_BRIDGE::SUCCESS_URL ";
        final var successUrlRef = new java.util.concurrent.atomic.AtomicReference<String>(null);

        // Signals to detect captcha / non-render conditions emitted by the PW spec
        final var sawCaptcha = new java.util.concurrent.atomic.AtomicBoolean(false);
        final var sawNoPaymentElement = new java.util.concurrent.atomic.AtomicBoolean(false);

        int exit;
        try {
            System.out.println("[PW] using working dir: " + wd.getAbsolutePath());
            System.out.println("[PW] command: " + String.join(" ", cmd));
            System.out.println("[PW] PATH=" + env.getOrDefault("PATH", System.getenv("PATH")));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(wd);
            pb.redirectErrorStream(false);

            Map<String, String> pbEnv = pb.environment();
            pbEnv.putAll(env);

            Process p = pb.start();

            // Parse STDOUT for success marker & hints
            Thread tOut = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[PW] " + line);
                        if (line.contains("hCaptcha")) {
                            sawCaptcha.set(true);
                        }
                        if (line.contains("Payment Element did not render")) {
                            sawNoPaymentElement.set(true);
                        }
                        int idx = line.indexOf(SUCCESS_MARK);
                        if (idx >= 0) {
                            String url = line.substring(idx + SUCCESS_MARK.length()).trim();
                            if (!url.isBlank()) successUrlRef.compareAndSet(null, url);
                        }
                    }
                } catch (IOException ignored) {}
            });
            tOut.setDaemon(true);
            tOut.start();

            // Drain STDERR so buffers don’t block the process
            Thread tErr = new Thread(() -> pipe(p.getErrorStream(), System.err, "[PW-ERR] "));
            tErr.setDaemon(true);
            tErr.start();

            // Process-level timeout
            if (options.timeout != null && !options.timeout.isZero() && !options.timeout.isNegative()) {
                if (!p.waitFor(options.timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly();
                    throw new RuntimeException("Playwright checkout timed out after " + options.timeout);
                }
            } else {
                p.waitFor();
            }

            try { tOut.join(1500); } catch (InterruptedException ignored) {}
            try { tErr.join(500); }  catch (InterruptedException ignored) {}

            exit = p.exitValue();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to execute Playwright checkout in " + wd.getAbsolutePath() +
                            " with cmd: " + String.join(" ", (/*null-safe*/ cmd == null ? List.<String>of() : cmd)),
                    e
            );
        }

        // ---- Decide success/failure robustly ----
        boolean ok = (exit == 0);

        // In CI, any captcha or non-render -> **hard fail**, regardless of exit code
        if (ci && (sawCaptcha.get() || sawNoPaymentElement.get())) {
            ok = false;
            System.err.println("[PW] CI strict mode: rejecting run due to hCaptcha / Payment Element not rendered.");
        }

        String successUrl = successUrlRef.get();

        // Validate success URL (must be trustworthy)
        if (ok) {
            if (successUrl == null || successUrl.isBlank()) {
                ok = false;
                System.err.println("[PW] No success URL emitted by Playwright.");
            } else if (!isTrustworthySuccessUrl(successUrl)) {
                ok = false;
                System.err.println("[PW] Untrusted success URL: " + successUrl);
            }
        }

        return new Result(ok, ok ? successUrl : null);
    }

    // ---------------- internal helpers ----------------

    private static boolean isTrustworthySuccessUrl(String url) {
        try {
            URI u = URI.create(url);
            final String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            final String qs = u.getQuery() == null ? "" : u.getQuery();
            final String frag = u.getFragment() == null ? "" : u.getFragment();
            final String tokens = (qs + "#" + frag).toLowerCase(Locale.ROOT);

            final boolean onStripeCheckout = host.contains("checkout.stripe.com");
            final boolean hasSuccessTokens =
                    tokens.contains("redirect_status=succeeded")
                            || tokens.contains("checkout_session_id")
                            || tokens.contains("success")
                            || tokens.contains("thank")
                            || tokens.contains("return")
                            || tokens.contains("receipt");

            // If we are still on checkout.stripe.com, require explicit success tokens.
            if (onStripeCheckout && !hasSuccessTokens) return false;

            // Otherwise, we must have left to merchant domain (OK) or have tokens.
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Find the Playwright repo dir (works in Jenkins and locally). */
    private static File effectiveWorkingDirectory(Options o) {
        if (isCi()) {
            String envWd = System.getenv("PW_BRIDGE_WD");
            if (envWd != null && !envWd.isBlank()) {
                File f = new File(envWd);
                if (isPwRepoDir(f)) return f.getAbsoluteFile();
            }
        }
        if (isPwRepoDir(o.workingDirectory)) return o.workingDirectory.getAbsoluteFile();

        String ws = System.getenv("WORKSPACE");
        if (ws != null && !ws.isBlank()) {
            File f = new File(ws, "automation/playwright");
            if (isPwRepoDir(f)) return f.getAbsoluteFile();
        }

        String[] candidates = {
                "automation/playwright",
                "../stripe-checkout-playwright",
                "../../stripe-checkout-playwright",
                "."
        };
        for (String c : candidates) {
            File f = new File(c);
            if (isPwRepoDir(f)) return f.getAbsoluteFile();
        }
        return new File(".").getAbsoluteFile();
    }

    /** Build the final shell command that runs Playwright via your run-pw.sh wrapper. */
    private static List<String> buildCommand(Options o) {
        String testOverride = System.getenv("PW_TEST");
        String testArg = (testOverride != null && !testOverride.isBlank())
                ? testOverride
                : (o.testPath != null ? o.testPath : "tests/stripe-checkout.spec.ts");

        String projectArg = (o.project != null ? o.project : "chromium");
        String grepArg    = (o.testGrep != null ? o.testGrep : "Stripe hosted checkout");
        boolean headed    = Boolean.TRUE.equals(o.headed);

        File baseWd = (o.workingDirectory != null) ? o.workingDirectory : effectiveWorkingDirectory(o);

        StringBuilder cmdLine = new StringBuilder();
        cmdLine.append("cd ").append(shellQuote(baseWd.getPath()))
                .append(" && ./run-pw.sh test ");

        File cfgTs = new File(baseWd, "playwright.config.ts");
        File cfgJs = new File(baseWd, "playwright.config.js");
        if (cfgTs.exists()) {
            cmdLine.append("--config=").append(shellQuote("playwright.config.ts")).append(" ");
        } else if (cfgJs.exists()) {
            cmdLine.append("--config=").append(shellQuote("playwright.config.js")).append(" ");
        }

        cmdLine.append(shellQuote(testArg)).append(" ");
        cmdLine.append("--project=").append(shellQuote(projectArg)).append(" ")
                .append("--reporter=line ")
                .append("--trace=on ");

        if (grepArg != null && !grepArg.isBlank()) {
            cmdLine.append("-g ").append(shellQuote(grepArg)).append(" ");
        }
        // Prefer using env PW_HEADLESS to control headedness; but keep this for explicit calls.
        if (headed) {
            cmdLine.append("--headed ");
        }

        return Arrays.asList("bash", "-lc", cmdLine.toString());
    }

    private static String shellQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    private static void pipe(InputStream in, OutputStream out, String prefix) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (prefix != null) pw.println(prefix + line);
                else pw.println(line);
            }
        } catch (IOException ignored) {}
    }

    private static boolean isPwRepoDir(File f) {
        if (f == null) return false;
        return f.exists() && f.isDirectory() &&
                (new File(f, "playwright.config.ts").exists()
                        || new File(f, "playwright.config.js").exists()
                        || new File(f, "package.json").exists());
    }

    private static boolean isCi() {
        String ci = System.getenv("CI");
        return (ci != null && !ci.isBlank())
                || System.getenv("JENKINS_URL") != null
                || System.getenv("GITHUB_ACTIONS") != null;
    }

    // ---------------- result + options ----------------

    public static final class Result {
        private final boolean success;
        private final String successUrl; // may be null if not found
        public Result(boolean success, String successUrl) { this.success = success; this.successUrl = successUrl; }
        public boolean isSuccess() { return success; }
        public String getSuccessUrl() { return successUrl; }
    }

    public static final class Options {
        private String npxExecutable = "npx"; // kept for API compatibility (not used with run-pw.sh)
        private String checkoutUrl;        // REQUIRED
        private String checkoutEmail;      // optional
        private String testPath;           // default: tests/stripe-checkout.spec.ts
        private String project;            // default: chromium
        private String testGrep;           // default: "Stripe hosted checkout"
        private Boolean headed;            // default: true (Xvfb in CI)
        private Duration timeout;          // default: 3 minutes (env override)
        private File workingDirectory;     // default: auto-detected
        private Boolean fakeSuccess;       // default: null (defer to env)

        public static Options defaultOptions() {
            // Overall process timeout from env, else 3 minutes (was 5; fail faster in CI)
            Duration t = Optional.ofNullable(System.getenv("PW_BRIDGE_TIMEOUT_MS"))
                    .map(Long::parseLong).map(Duration::ofMillis)
                    .orElse(Duration.ofMinutes(3));

            // Default to headed; CI runs under Xvfb in your pipeline.
            boolean headedDefault = true;
            String envHeadless = System.getenv("PW_HEADLESS");
            if (envHeadless != null) {
                headedDefault = !"1".equals(envHeadless) && !"true".equalsIgnoreCase(envHeadless);
            } else if ("1".equals(System.getenv("PWDEBUG"))) {
                headedDefault = true;
            }

            // Respect env fake-success by default if set (but bridge blocks it in CI).
            Boolean fake = "1".equals(System.getenv("PW_STRIPE_FAKE_SUCCESS")) ? Boolean.TRUE : null;

            return new Options()
                    .setHeaded(headedDefault)
                    .setTimeout(t)
                    .setFakeSuccess(fake);
        }

        public Options setCheckoutUrl(String url) { this.checkoutUrl = url; return this; }
        public Options setCheckoutEmail(String email) { this.checkoutEmail = email; return this; }
        public Options setTestPath(String testPath) { this.testPath = testPath; return this; }
        public Options setProject(String project) { this.project = project; return this; }
        public Options setTestGrep(String grep) { this.testGrep = grep; return this; }
        public Options setHeaded(boolean headed) { this.headed = headed; return this; }
        public Options setTimeout(Duration timeout) { this.timeout = timeout; return this; }
        public Options setWorkingDirectory(File dir) { this.workingDirectory = dir; return this; }
        public Options setNpxExecutable(String path) { this.npxExecutable = path; return this; }
        public Options setFakeSuccess(Boolean fakeSuccess) { this.fakeSuccess = fakeSuccess; return this; }
    }
}
