package Utils;


import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentIntentCollection;
import com.stripe.param.PaymentIntentListParams;

import java.util.List;

public class BackendUtils {


    // Initialize your Stripe API key (use test key from Stripe Dashboard)
    static {
        Stripe.apiKey = "sk_test_XXXXXXXXXXXXXXXXXXXXXXXX"; // TODO: Replace with your test secret key
    }

    /**
     * Retrieves the latest Stripe PaymentIntent ID for a given user email.
     *
     * @param userEmail Email of the user who made the payment.
     * @return The latest PaymentIntent ID.
     */
    public static String getLatestPaymentIntentIdForUser(String userEmail) {
        try {
            System.out.println("🔄 [BackendUtils] Fetching latest PaymentIntent for: " + userEmail);

            PaymentIntentListParams params = PaymentIntentListParams.builder()
                    .setLimit(5L) // limit results for performance
                    .build();

            PaymentIntentCollection paymentIntents = PaymentIntent.list(params);
            List<PaymentIntent> intentList = paymentIntents.getData();

            for (PaymentIntent intent : intentList) {
                if (intent.getReceiptEmail() != null && intent.getReceiptEmail().equalsIgnoreCase(userEmail)) {
                    System.out.println("✅ Found PaymentIntent: " + intent.getId());
                    return intent.getId();
                }
            }

            System.out.println("⚠️ No matching PaymentIntent found for email: " + userEmail);
            return null;

        } catch (Exception e) {
            System.err.println("❌ Error retrieving PaymentIntent: " + e.getMessage());
            return null;
        }
    }

    /**
     * Confirms that a payment succeeded by checking its status in Stripe.
     *
     * @param paymentIntentId The ID of the PaymentIntent to check.
     * @return true if payment succeeded, false otherwise.
     */
    public static boolean isPaymentSuccessful(String paymentIntentId) {
        try {
            if (paymentIntentId == null) {
                System.err.println("❌ No PaymentIntent ID provided to verify payment");
                return false;
            }

            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            String status = intent.getStatus();
            System.out.println("🔍 PaymentIntent status: " + status);

            return "succeeded".equalsIgnoreCase(status);

        } catch (Exception e) {
            System.err.println("❌ Error verifying payment: " + e.getMessage());
            return false;
        }
    }


}
