package pages.Shop.Stripe;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Runs a Playwright test that completes Stripe Hosted Checkout using a fresh CHECKOUT_URL.
 *
 * Usage:
 *   String url = preview.proceedToStripeAndGetCheckoutUrl();
 *   PlaywrightStripeBridge.pay(url, "qa+stripe@example.com");
 *
 * Advanced:
 *   PlaywrightStripeBridge.Options opts = PlaywrightStripeBridge.Options.defaultOptions()
 *       .setCheckoutUrl(url)
 *       .setCheckoutEmail("qa+stripe@example.com")
 *       .setProject("chromium")
 *       .setHeaded(true) // headed Chromium under Xvfb on CI avoids hCaptcha gating
 *       .setTestGrep("Stripe hosted checkout")
 *       .setTimeout(Duration.ofMinutes(3))
 *       .setWorkingDirectory(new File(".")); // repo root with playwright.config.ts + run-pw.sh
 *   PlaywrightStripeBridge.pay(opts);
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

        // Decide effective working directory first.
        final File wd = effectiveWorkingDirectory(options);

        // Build the command while temporarily setting the WD so detection uses it.
        final File oldWd = options.workingDirectory;
        options.setWorkingDirectory(wd);
        final List<String> cmd;
        try {
            cmd = buildCommand(options);
        } finally {
            options.setWorkingDirectory(oldWd);
        }

        // Environment for the Playwright process
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("CI", "1"); // make PW behave deterministically in CI
        env.put("PW_BRIDGE_WD", wd.getAbsolutePath());
        env.put("CHECKOUT_URL", options.checkoutUrl);
        if (options.checkoutEmail != null) env.put("CHECKOUT_EMAIL", options.checkoutEmail);

        // Ensure Playwright (config) understands that we want headed mode when requested.
        if (Boolean.TRUE.equals(options.headed)) {
            env.put("PW_HEADLESS", "0"); // your config reads this; 0 => headed
        }
        // Pass the same per-test timeout down to Playwright (ms)
        if (options.timeout != null && !options.timeout.isZero() && !options.timeout.isNegative()) {
            env.put("PW_TIMEOUT_MS", String.valueOf(options.timeout.toMillis()));
        }
        // Optional: let you increase expect timeouts without code changes
        if (!env.containsKey("PW_EXPECT_TIMEOUT_MS")) {
            env.put("PW_EXPECT_TIMEOUT_MS", "15000");
        }

        final String MARKER = "PW_BRIDGE::SUCCESS_URL ";
        final java.util.concurrent.atomic.AtomicReference<String> successUrlRef = new java.util.concurrent.atomic.AtomicReference<>(null);

        int exit;
        try {
            System.out.println("[PW] using working dir: " + wd.getAbsolutePath());
            System.out.println("[PW] command: " + String.join(" ", cmd));
            System.out.println("[PW] PATH=" + env.getOrDefault("PATH", System.getenv("PATH")));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(wd);

            Map<String, String> pbEnv = pb.environment();
            pbEnv.putAll(env);

            // Keep streams separate so we can parse stdout lines cleanly.
            pb.redirectErrorStream(false);
            Process p = pb.start();

            // STDOUT (parse success marker)
            Thread tOut = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[PW] " + line);
                        int idx = line.indexOf(MARKER);
                        if (idx >= 0) {
                            String url = line.substring(idx + MARKER.length()).trim();
                            if (!url.isBlank()) successUrlRef.compareAndSet(null, url);
                        }
                    }
                } catch (IOException ignored) {}
            });
            tOut.setDaemon(true);
            tOut.start();

            // STDERR (avoid blocking on full buffers)
            Thread tErr = new Thread(() -> pipe(p.getErrorStream(), System.err, "[PW-ERR] "));
            tErr.setDaemon(true);
            tErr.start();

            // Wait with an overall timeout at the process level.
            if (options.timeout != null && !options.timeout.isZero() && !options.timeout.isNegative()) {
                if (!p.waitFor(options.timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly();
                    throw new RuntimeException("Playwright checkout timed out after " + options.timeout);
                }
            } else {
                p.waitFor();
            }
            exit = p.exitValue();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to execute Playwright checkout in " + wd.getAbsolutePath() +
                            " with cmd: " + String.join(" ", (cmd == null ? List.<String>of() : cmd)),
                    e
            );
        }

        boolean ok = (exit == 0);
        return new Result(ok, successUrlRef.get());
    }

    // ---------------- internal helpers ----------------

    /** Find the Playwright repo dir (works in Jenkins and locally). */
    private static File effectiveWorkingDirectory(Options o) {
        // 1) In CI, prefer PW_BRIDGE_WD if valid
        if (isCi()) {
            String envWd = System.getenv("PW_BRIDGE_WD");
            if (envWd != null && !envWd.isBlank()) {
                File f = new File(envWd);
                if (isPwRepoDir(f)) return f.getAbsoluteFile();
            }
        }
        // 2) Respect caller-provided WD only if it looks like a PW repo
        if (isPwRepoDir(o.workingDirectory)) return o.workingDirectory.getAbsoluteFile();

        // 3) Jenkins layout
        String ws = System.getenv("WORKSPACE");
        if (ws != null && !ws.isBlank()) {
            File f = new File(ws, "automation/playwright");
            if (isPwRepoDir(f)) return f.getAbsoluteFile();
        }

        // 4) Common local layouts
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
        // Fallback: current dir (still allow running if user points WD elsewhere)
        return new File(".").getAbsoluteFile();
    }

    /** Build the final shell command that runs Playwright via your run-pw.sh wrapper. */
    private static List<String> buildCommand(Options o) {
        // Let CI override the test file
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

        // Prefer explicit config (keeps loader consistent)
        File cfgTs = new File(baseWd, "playwright.config.ts");
        File cfgJs = new File(baseWd, "playwright.config.js");
        if (cfgTs.exists()) {
            cmdLine.append("--config=").append(shellQuote("playwright.config.ts")).append(" ");
        } else if (cfgJs.exists()) {
            cmdLine.append("--config=").append(shellQuote("playwright.config.js")).append(" ");
        }

        // Test file (relative is fine)
        cmdLine.append(shellQuote(testArg)).append(" ");

        // Common flags
        cmdLine.append("--project=").append(shellQuote(projectArg)).append(" ")
                .append("--reporter=line ")
                .append("--trace=on "); // keep rich traces in CI

        if (grepArg != null && !grepArg.isBlank()) {
            cmdLine.append("-g ").append(shellQuote(grepArg)).append(" ");
        }
        if (headed) {
            cmdLine.append("--headed "); // CLI override for headed mode
        }

        return Arrays.asList("bash", "-lc", cmdLine.toString());
    }

    private static String shellQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private static void pipe(InputStream in, OutputStream out, String prefix) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true)) {
            String line;
            while ((line = br.readLine()) != null) {
                pw.println(prefix + line);
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
        private String npxExecutable = "npx"; // kept for API compatibility (unused when using run-pw.sh)
        private String checkoutUrl;        // REQUIRED
        private String checkoutEmail;      // optional
        private String testPath;           // default: tests/stripe-checkout.spec.ts
        private String project;            // default: chromium
        private String testGrep;           // default: "Stripe hosted checkout"
        private Boolean headed;            // default: true on CI (recommended), otherwise true as well
        private Duration timeout;          // default: 5 minutes
        private File workingDirectory;     // default: auto-detected

        public static Options defaultOptions() {
            // Overall process timeout (ms) from env, else 5 minutes
            Duration t = Optional.ofNullable(System.getenv("PW_BRIDGE_TIMEOUT_MS"))
                    .map(Long::parseLong).map(Duration::ofMillis)
                    .orElse(Duration.ofMinutes(5));
            // Default to headed; CI runs under Xvfb in your pipeline.
            boolean headedDefault = true;
            String envHeadless = System.getenv("PW_HEADLESS");
            if (envHeadless != null) {
                // PW_HEADLESS=0 => headed; 1 => headless
                headedDefault = !"1".equals(envHeadless) && !"true".equalsIgnoreCase(envHeadless);
            } else if ("1".equals(System.getenv("PWDEBUG"))) {
                headedDefault = true;
            }
            return new Options()
                    .setHeaded(headedDefault)
                    .setTimeout(t);
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
    }
}
