package api.v2;

import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;


public interface CouponCodesApiV2 {
    @GET("api/v2/coupon_codes")
    Call<ResponseBody> index(@QueryMap Map<String, Object> query);

    @GET("api/v2/coupon_codes" + "/<built-in function id>")
    Call<ResponseBody> show(@Path("id") String id, @QueryMap Map<String, Object> query);

    @POST("api/v2/coupon_codes")
    Call<ResponseBody> create(@Body Object body);

    @PUT("api/v2/coupon_codes" + "/<built-in function id>")
    Call<ResponseBody> update(@Path("id") String id, @Body Object body);

    @DELETE("api/v2/coupon_codes" + "/<built-in function id>")
    Call<ResponseBody> destroy(@Path("id") String id);

    @GET("api/v2/coupon_codes" + "/validate")
    Call<ResponseBody> validate(@QueryMap Map<String, Object> query);

}