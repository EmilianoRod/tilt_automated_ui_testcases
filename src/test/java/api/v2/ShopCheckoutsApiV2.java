package api.v2;

import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;


public interface ShopCheckoutsApiV2 {
    @GET("api/v2/shop/checkouts")
    Call<ResponseBody> index(@QueryMap Map<String, Object> query);

    @GET("api/v2/shop/checkouts" + "/<built-in function id>")
    Call<ResponseBody> show(@Path("id") String id, @QueryMap Map<String, Object> query);

    @POST("api/v2/shop/checkouts")
    Call<ResponseBody> create(@Body Object body);

    @PUT("api/v2/shop/checkouts" + "/<built-in function id>")
    Call<ResponseBody> update(@Path("id") String id, @Body Object body);

    @DELETE("api/v2/shop/checkouts" + "/<built-in function id>")
    Call<ResponseBody> destroy(@Path("id") String id);

    @GET("api/v2/shop/checkouts" + "/build_discounts")
    Call<ResponseBody> build_discounts(@QueryMap Map<String, Object> query);

    @POST("api/v2/shop/checkouts" + "/create_order")
    Call<ResponseBody> create_order(@Body Object body);

    @POST("api/v2/shop/checkouts" + "/create_stripe_session")
    Call<ResponseBody> create_stripe_session(@Body Object body);

    @GET("api/v2/shop/checkouts" + "/stripe_line_items")
    Call<ResponseBody> stripe_line_items(@QueryMap Map<String, Object> query);

    @GET("api/v2/shop/checkouts" + "/stripe_metadata_subscriptions")
    Call<ResponseBody> stripe_metadata_subscriptions(@QueryMap Map<String, Object> query);

    @GET("api/v2/shop/checkouts" + "/validate_coupon_code")
    Call<ResponseBody> validate_coupon_code(@QueryMap Map<String, Object> query);

}