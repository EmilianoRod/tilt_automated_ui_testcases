package api.v1;

import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;


public interface UsersApiV1 {
    @GET("api/v1/users")
    Call<ResponseBody> index(@QueryMap Map<String, Object> query);

    @GET("api/v1/users" + "/<built-in function id>")
    Call<ResponseBody> show(@Path("id") String id, @QueryMap Map<String, Object> query);

    @POST("api/v1/users")
    Call<ResponseBody> create(@Body Object body);

    @PUT("api/v1/users" + "/<built-in function id>")
    Call<ResponseBody> update(@Path("id") String id, @Body Object body);

    @DELETE("api/v1/users" + "/<built-in function id>")
    Call<ResponseBody> destroy(@Path("id") String id);

}