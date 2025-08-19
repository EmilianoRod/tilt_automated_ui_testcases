package pages.Shop.Stripe;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Runs a Playwright test that completes Stripe Hosted Checkout using a fresh CHECKOUT_URL.
 *
 * Usage:
 *   String url = preview.proceedToStripeAndGetCheckoutUrl(); // from your OrderPreviewPage
 *   PlaywrightStripeBridge.pay(url, "qa+stripe@example.com");
 *
 * Advanced:
 *   PlaywrightStripeBridge.Options opts = PlaywrightStripeBridge.Options.defaultOptions()
 *       .setCheckoutUrl(url)
 *       .setCheckoutEmail("qa+stripe@example.com")
 *       .setProject("chromium")      // or "Google Chrome" if you configured channel:'chrome'
 *       .setHeaded(true)
 *       .setTestGrep("Stripe hosted checkout")
 *       .setTimeout(Duration.ofMinutes(3))
 *       .setWorkingDirectory(new File(".")); // repo root with playwright.config.ts
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

    /** Main entry: runs `npx playwright test ...` with environment variables for the test. */
    public static void pay(Options options) {
//        Objects.requireNonNull(options.checkoutUrl, "checkoutUrl is required");
//
//        // Build the Playwright CLI command
//        List<String> cmd = buildCommand(options);
//
//        // Prepare environment (only what we need)
//        Map<String, String> env = new HashMap<>(System.getenv());
//        env.put("CHECKOUT_URL", options.checkoutUrl);
//        if (options.checkoutEmail != null) env.put("CHECKOUT_EMAIL", options.checkoutEmail);
//
//        // Spawn process
//        int exit;
//        try {
//            ProcessBuilder pb = new ProcessBuilder(cmd);
//            if (options.workingDirectory != null) {
//                pb.directory(options.workingDirectory);
//            }
//            // Pass env vars
//            Map<String, String> pbEnv = pb.environment();
//            pbEnv.putAll(env);
//
//            // Inherit stdout/stderr so you see the Playwright logs in your test console
//            pb.redirectErrorStream(true);
//            Process p = pb.start();
//
//            // Pump output to console with a prefix
//            Thread stdout = new Thread(() -> pipe(p.getInputStream(), System.out, "[PW] "));
//            stdout.setDaemon(true);
//            stdout.start();
//
//            // Wait with timeout
//            if (options.timeout != null && !options.timeout.isZero() && !options.timeout.isNegative()) {
//                if (!p.waitFor(options.timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
//                    p.destroyForcibly();
//                    throw new RuntimeException("Playwright checkout timed out after " + options.timeout);
//                }
//            } else {
//                p.waitFor();
//            }
//
//            exit = p.exitValue();
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to execute Playwright checkout", e);
//        }
//
//        if (exit != 0) {
//            throw new RuntimeException("Playwright checkout failed (exit code " + exit + ")");
//        }
        Result r = payReturning(options);
        if (!r.isSuccess()) {
            throw new RuntimeException("Playwright checkout failed");
        }
        if (r.getSuccessUrl() == null) {
            // Not fatal for pay(), but useful to diagnose when you expected a redirect
            System.out.println("[PW] (note) success URL not captured. Did you add the console.log marker in the spec?");
        }
    }



    public static Result payReturning(Options options) {
        Objects.requireNonNull(options.checkoutUrl, "checkoutUrl is required");

        List<String> cmd = buildCommand(options);

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("CHECKOUT_URL", options.checkoutUrl);
        if (options.checkoutEmail != null) env.put("CHECKOUT_EMAIL", options.checkoutEmail);

        final String MARKER = "PW_BRIDGE::SUCCESS_URL ";
        final java.util.concurrent.atomic.AtomicReference<String> successUrlRef = new java.util.concurrent.atomic.AtomicReference<>(null);

        int exit;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (options.workingDirectory != null) pb.directory(options.workingDirectory);

            Map<String, String> pbEnv = pb.environment();
            pbEnv.putAll(env);

            // Keep streams separate so we can parse stdout lines cleanly
            pb.redirectErrorStream(false);
            Process p = pb.start();

            // pump & parse STDOUT
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

            // pump STDERR (so process won't block)
            Thread tErr = new Thread(() -> pipe(p.getErrorStream(), System.err, "[PW-ERR] "));
            tErr.setDaemon(true);
            tErr.start();

            // wait
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
            throw new RuntimeException("Failed to execute Playwright checkout", e);
        }

        boolean ok = (exit == 0);
        return new Result(ok, successUrlRef.get());
    }


    // ---------------- internal helpers ----------------

    private static List<String> buildCommand(Options o) {

        String testArg = o.testPath != null ? o.testPath : "tests/stripe-checkout.spec.ts";
        String projectArg = o.project != null ? o.project : "chromium";
        String grepArg = o.testGrep != null ? o.testGrep : "Stripe hosted checkout";

        List<String> args = new ArrayList<>();

        if (isWindows()) {
            String exe = o.npxExecutable != null ? o.npxExecutable : "npx";
            args.add(exe);
            args.add("playwright");
            args.add("test");
        } else {
            File wd = (o.workingDirectory != null ? o.workingDirectory : new File("."));
            File local = new File(new File(wd.toURI()), "node_modules/.bin/playwright");
            if (local.exists() && local.canExecute()) {
                args.add(local.getAbsolutePath()); args.add("test");
            } else {
                // Fallback to npx on Unix
                String exe = o.npxExecutable != null ? o.npxExecutable : "npx";
                args.add(exe); args.add("playwright"); args.add("test");
            }
        }

        args.add(testArg);
        args.add("--project=" + projectArg);
        // simple reporter so no HTML viewer tries to open
        args.add("--reporter=line");
        if (grepArg != null && !grepArg.isBlank()) {
            args.add("-g"); args.add(grepArg);
        }
        if (Boolean.TRUE.equals(o.headed)) {
            args.add("--headed");
        }

        if (isWindows()) {
            List<String> cmd = new ArrayList<>();
            cmd.add("cmd"); cmd.add("/c");
            cmd.add(String.join(" ", quoteForCmd(args)));
            return cmd;
        }
        return args;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private static List<String> quoteForCmd(List<String> args) {
        // Naive quoting for Windows cmd: wrap with double quotes if space present
        List<String> out = new ArrayList<>(args.size());
        for (String a : args) {
            if (a.matches(".*[\\s\"].*")) {
                out.add("\"" + a.replace("\"", "\\\"") + "\"");
            } else {
                out.add(a);
            }
        }
        return out;
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

    public static final class Result {
        private final boolean success;
        private final String successUrl; // may be null if not found

        public Result(boolean success, String successUrl) {
            this.success = success;
            this.successUrl = successUrl;
        }
        public boolean isSuccess() { return success; }
        public String getSuccessUrl() { return successUrl; }
    }


    // ---------------- options ----------------

    public static final class Options {
        private String npxExecutable = "npx";   // <— NEW: default to "npx"
        private String checkoutUrl;        // REQUIRED
        private String checkoutEmail;      // optional
        private String testPath;           // default: tests/stripe-checkout.spec.ts
        private String project;            // default: chromium  (or "Google Chrome" if configured)
        private String testGrep;           // default: "Stripe hosted checkout"
        private Boolean headed;            // default: false
        private Duration timeout;          // default: 2 minutes
        private File workingDirectory;     // default: current dir

        public static Options defaultOptions() {
            return new Options()
                    .setHeaded(true)
                    .setTimeout(Duration.ofMinutes(2));
        }

        public Options setCheckoutUrl(String url) { this.checkoutUrl = url; return this; }
        public Options setCheckoutEmail(String email) { this.checkoutEmail = email; return this; }
        public Options setTestPath(String testPath) { this.testPath = testPath; return this; }
        public Options setProject(String project) { this.project = project; return this; }
        public Options setTestGrep(String grep) { this.testGrep = grep; return this; }
        public Options setHeaded(boolean headed) { this.headed = headed; return this; }
        public Options setTimeout(Duration timeout) { this.timeout = timeout; return this; }
        public Options setWorkingDirectory(File dir) { this.workingDirectory = dir; return this; }
        public Options setNpxExecutable(String path) { this.npxExecutable = path; return this; } // <— NEW

    }
}