package Utils;
import java.io.BufferedReader;
import java.io.InputStreamReader;




public class StripeCheckoutHelper {

    /**
     * Simulate a successful Stripe Checkout by calling the Stripe CLI.
     * It will trigger checkout.session.completed with the given orderId.
     */
    public static void simulateCheckoutSuccess(String orderId) {
        runStripeCommand(
                "stripe trigger checkout.session.completed " +
                        " --override checkout_session:\"metadata[order_id]\"=\"" + orderId + "\""
        );
    }

    /**
     * Simulate a failed Stripe Checkout (declined card).
     */
    public static void simulateCheckoutFailure(String orderId) {
        runStripeCommand(
                "stripe trigger payment_intent.payment_failed " +
                        " --override payment_intent:\"metadata[order_id]\"=\"" + orderId + "\""
        );
    }

    /**
     * Internal runner: executes a Stripe CLI command and prints output.
     */
    private static void runStripeCommand(String command) {
        try {
            ProcessBuilder builder = new ProcessBuilder("bash", "-lc", command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[StripeCLI] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Stripe CLI command failed: " + command);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error running Stripe CLI: " + command, e);
        }
    }
}
