package api.v2;

import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;


public interface ShopOrdersApiV2 {
    @GET("api/v2/shop/orders")
    Call<ResponseBody> index(@QueryMap Map<String, Object> query);

    @GET("api/v2/shop/orders" + "/<built-in function id>")
    Call<ResponseBody> show(@Path("id") String id, @QueryMap Map<String, Object> query);

    @POST("api/v2/shop/orders")
    Call<ResponseBody> create(@Body Object body);

    @PUT("api/v2/shop/orders" + "/<built-in function id>")
    Call<ResponseBody> update(@Path("id") String id, @Body Object body);

    @DELETE("api/v2/shop/orders" + "/<built-in function id>")
    Call<ResponseBody> destroy(@Path("id") String id);

    @POST("api/v2/shop/orders" + "/create_order")
    Call<ResponseBody> create_order(@Body Object body);

    @GET("api/v2/shop/orders" + "/metadata_subscriptions")
    Call<ResponseBody> metadata_subscriptions(@QueryMap Map<String, Object> query);

}