package api.v2;

import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface IndividualsApiV2 {

    @GET("api/v2/individuals")
    Call<ResponseBody> index(@QueryMap Map<String, String> query);

    @GET("api/v2/individuals/{id}")
    Call<ResponseBody> show(@Path("id") String id, @QueryMap Map<String, String> query);

    @Headers("Content-Type: application/json")
    @POST("api/v2/individuals")
    Call<ResponseBody> create(@Body Object body);

    @Headers("Content-Type: application/json")
    @PUT("api/v2/individuals/{id}")
    Call<ResponseBody> update(@Path("id") String id, @Body Object body);

    @DELETE("api/v2/individuals/{id}")
    Call<ResponseBody> destroy(@Path("id") String id);

    @GET("api/v2/individuals/apply_filters")
    Call<ResponseBody> applyFilters(@QueryMap Map<String, String> query);

    @Headers("Content-Type: application/json")
    @PUT("api/v2/individuals/update_order_subscriptions")
    Call<ResponseBody> updateOrderSubscriptions(@Body Object body);
}
