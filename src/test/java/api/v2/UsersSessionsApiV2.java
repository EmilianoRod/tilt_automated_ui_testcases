package api.v2;

import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;


public interface UsersSessionsApiV2 {
    @GET("api/v2/users/sessions")
    Call<ResponseBody> index(@QueryMap Map<String, Object> query);

    @GET("api/v2/users/sessions" + "/<built-in function id>")
    Call<ResponseBody> show(@Path("id") String id, @QueryMap Map<String, Object> query);

    @POST("api/v2/users/sessions")
    Call<ResponseBody> create(@Body Object body);

    @PUT("api/v2/users/sessions" + "/<built-in function id>")
    Call<ResponseBody> update(@Path("id") String id, @Body Object body);

    @DELETE("api/v2/users/sessions" + "/<built-in function id>")
    Call<ResponseBody> destroy(@Path("id") String id);

    @GET("api/v2/users/sessions" + "/handle_organization_token_after_login")
    Call<ResponseBody> handle_organization_token_after_login(@QueryMap Map<String, Object> query);

    @GET("api/v2/users/sessions" + "/extract_token_and_org_type")
    Call<ResponseBody> extract_token_and_org_type(@QueryMap Map<String, Object> query);

    @GET("api/v2/users/sessions" + "/process_redhat_token")
    Call<ResponseBody> process_redhat_token(@QueryMap Map<String, Object> query);

}