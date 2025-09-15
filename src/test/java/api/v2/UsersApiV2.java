package api.v2;

import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;


public interface UsersApiV2 {
    @GET("api/v2/users")
    Call<ResponseBody> index(@QueryMap Map<String, Object> query);

    @GET("api/v2/users" + "/<built-in function id>")
    Call<ResponseBody> show(@Path("id") String id, @QueryMap Map<String, Object> query);

    @POST("api/v2/users")
    Call<ResponseBody> create(@Body Object body);

    @PUT("api/v2/users" + "/<built-in function id>")
    Call<ResponseBody> update(@Path("id") String id, @Body Object body);

    @DELETE("api/v2/users" + "/<built-in function id>")
    Call<ResponseBody> destroy(@Path("id") String id);

    @PUT("api/v2/users" + "/update_password")
    Call<ResponseBody> update_password(@Body Object body);

    @GET("api/v2/users" + "/redhat_tilts")
    Call<ResponseBody> redhat_tilts(@QueryMap Map<String, Object> query);

    @POST("api/v2/users" + "/send_confirmation_email")
    Call<ResponseBody> send_confirmation_email(@Body Object body);

    @GET("api/v2/users" + "/apply_filters")
    Call<ResponseBody> apply_filters(@QueryMap Map<String, Object> query);

    @GET("api/v2/users" + "/user")
    Call<ResponseBody> user(@QueryMap Map<String, Object> query);

    @GET("api/v2/users" + "/redhat_users_with_assessments")
    Call<ResponseBody> redhat_users_with_assessments(@QueryMap Map<String, Object> query);

}