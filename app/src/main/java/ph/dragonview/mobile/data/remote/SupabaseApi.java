package ph.dragonview.mobile.data.remote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

interface SupabaseApi {
    @POST("auth/v1/signup")
    Call<JsonObject> signUp(@Header("apikey") String apiKey, @Body JsonObject body);

    @POST("auth/v1/token")
    Call<JsonObject> token(@Header("apikey") String apiKey,
                           @Query("grant_type") String grantType,
                           @Body JsonObject body);

    @POST("auth/v1/logout")
    Call<Void> logout(@Header("apikey") String apiKey,
                      @Header("Authorization") String authorization);

    @POST("auth/v1/recover")
    Call<JsonObject> recover(@Header("apikey") String apiKey,
                             @Query("redirect_to") String redirectTo,
                             @Body JsonObject body);

    @PUT("auth/v1/user")
    Call<JsonObject> updatePassword(@Header("apikey") String apiKey,
                                    @Header("Authorization") String authorization,
                                    @Body JsonObject body);

    @POST("rest/v1/rpc/{functionName}")
    Call<JsonElement> rpc(@Path("functionName") String functionName,
                          @Header("apikey") String apiKey,
                          @Header("Authorization") String authorization,
                          @Body Map<String, Object> arguments);
}
