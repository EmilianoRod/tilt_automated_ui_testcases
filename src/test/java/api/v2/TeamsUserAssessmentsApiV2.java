package api.v2;

import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;


public interface TeamsUserAssessmentsApiV2 {
    @GET("api/v2/teams/user_assessments")
    Call<ResponseBody> index(@QueryMap Map<String, Object> query);

    @GET("api/v2/teams/user_assessments" + "/<built-in function id>")
    Call<ResponseBody> show(@Path("id") String id, @QueryMap Map<String, Object> query);

    @POST("api/v2/teams/user_assessments")
    Call<ResponseBody> create(@Body Object body);

    @PUT("api/v2/teams/user_assessments" + "/<built-in function id>")
    Call<ResponseBody> update(@Path("id") String id, @Body Object body);

    @DELETE("api/v2/teams/user_assessments" + "/<built-in function id>")
    Call<ResponseBody> destroy(@Path("id") String id);

    @GET("api/v2/teams/user_assessments" + "/team")
    Call<ResponseBody> team(@QueryMap Map<String, Object> query);

    @GET("api/v2/teams/user_assessments" + "/team_users")
    Call<ResponseBody> team_users(@QueryMap Map<String, Object> query);

    @GET("api/v2/teams/user_assessments" + "/filtered_user_assessments")
    Call<ResponseBody> filtered_user_assessments(@QueryMap Map<String, Object> query);

}