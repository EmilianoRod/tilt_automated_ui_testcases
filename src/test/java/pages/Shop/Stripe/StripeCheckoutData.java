package pages.Shop.Stripe;
import java.util.regex.*;



public class StripeCheckoutData {


    public final String sessionUrl;   // https://checkout.stripe.com/c/pay/cs_test_...
    public final String sessionId;    // cs_test_...
    public final String orderId;      // from your system

    public StripeCheckoutData(String sessionUrl, String sessionId, String orderId) {
        this.sessionUrl = sessionUrl;
        this.sessionId = sessionId;
        this.orderId = orderId;
    }

    public static StripeCheckoutData from(String sessionUrl, String orderId) {
        String sessionId = null;
        if (sessionUrl != null) {
            Matcher m = Pattern.compile("(cs_test_[A-Za-z0-9]+)").matcher(sessionUrl);
            if (m.find()) sessionId = m.group(1);
        }
        return new StripeCheckoutData(sessionUrl, sessionId, orderId);
    }



}
