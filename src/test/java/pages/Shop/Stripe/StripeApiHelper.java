package pages.Shop.Stripe;


import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentIntentCollection;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentListParams;

import java.util.List;


public class StripeApiHelper {


    public StripeApiHelper(String apiKey) {
        Stripe.apiKey = apiKey; // Set your Stripe test secret key
    }




    /**
     * Retrieves the latest PaymentIntent ID for the given customer email.
     *
     * @param email The customer's email
     * @return PaymentIntent ID or null if none found
     */
    public String getLatestPaymentIntentIdForEmail(String email) {
        try {
            PaymentIntentListParams params = PaymentIntentListParams.builder()
                    .setLimit(5L)
                    .build();

            PaymentIntentCollection paymentIntents = PaymentIntent.list(params);
            List<PaymentIntent> data = paymentIntents.getData();

            for (PaymentIntent pi : data) {
                if (pi.getReceiptEmail() != null && pi.getReceiptEmail().equalsIgnoreCase(email)) {
                    return pi.getId();
                }
            }

            System.out.println("⚠️ No PaymentIntent found for email: " + email);
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Confirms a PaymentIntent for testing purposes.
     *
     * @param paymentIntentId The PaymentIntent ID
     * @return true if confirmation succeeded, false otherwise
     */
    public boolean confirmTestPayment(String paymentIntentId) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            PaymentIntentConfirmParams confirmParams = PaymentIntentConfirmParams.builder()
                    .setPaymentMethod("pm_card_visa") // Test card payment method
                    .build();

            PaymentIntent confirmedIntent = paymentIntent.confirm(confirmParams);

            return "succeeded".equalsIgnoreCase(confirmedIntent.getStatus());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Checks if a PaymentIntent has succeeded.
     *
     * @param paymentIntentId The PaymentIntent ID
     * @return true if succeeded, false otherwise
     */
    public boolean isPaymentSuccessful(String paymentIntentId) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            return "succeeded".equalsIgnoreCase(paymentIntent.getStatus());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


}
