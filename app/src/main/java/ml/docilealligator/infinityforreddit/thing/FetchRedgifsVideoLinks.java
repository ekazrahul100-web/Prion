package ml.docilealligator.infinityforreddit.thing;

import android.content.SharedPreferences;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import java.io.IOException;
import java.util.concurrent.Executor;
import ml.docilealligator.infinityforreddit.FetchVideoLinkListener;
import ml.docilealligator.infinityforreddit.apis.RedgifsAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchRedgifsVideoLinks {
    public static void fetchRedgifsVideoLinks(Executor executor, Handler handler, Retrofit redgifsRetrofit,
                                              SharedPreferences currentAccountSharedPreferences,
                                              @Nullable String redgifsId,
                                              FetchVideoLinkListener fetchVideoLinkListener) {
        executor.execute(() -> {
            try {
                // Get valid token
                String accessToken = getValidAccessToken(redgifsRetrofit, currentAccountSharedPreferences);
                if (accessToken.isEmpty()) {
                    handler.post(() -> fetchVideoLinkListener.failed(null));
                    return;
                }

                Response<String> response = redgifsRetrofit
                        .create(RedgifsAPI.class)
                        .getRedgifsData(APIUtils.getRedgifsOAuthHeader(accessToken),
                                redgifsId, APIUtils.USER_AGENT)
                        .execute();
                if (response.isSuccessful()) {
                    parseRedgifsVideoLinks(handler, response.body(), fetchVideoLinkListener, currentAccountSharedPreferences);
                } else if (response.code() == 401) {
                    // Token expired, try once more with new token
                    accessToken = refreshAccessToken(redgifsRetrofit, currentAccountSharedPreferences);
                    if (!accessToken.isEmpty()) {
                        response = redgifsRetrofit
                                .create(RedgifsAPI.class)
                                .getRedgifsData(
                                        APIUtils.getRedgifsOAuthHeader(accessToken),
                                        redgifsId, APIUtils.USER_AGENT)
                                .execute();
                        if (response.isSuccessful()) {
                            parseRedgifsVideoLinks(handler, response.body(), fetchVideoLinkListener, currentAccountSharedPreferences);
                        } else {
                            handler.post(() -> fetchVideoLinkListener.failed(null));
                        }
                    } else {
                        handler.post(() -> fetchVideoLinkListener.failed(null));
                    }
                } else {
                    handler.post(() -> fetchVideoLinkListener.failed(null));
                }
            } catch (IOException e) {
                e.printStackTrace();
                handler.post(() -> fetchVideoLinkListener.failed(null));
            }
        });
    }

    @WorkerThread
    @Nullable
    public static String fetchRedgifsVideoLinkSync(Retrofit redgifsRetrofit,
                                              SharedPreferences currentAccountSharedPreferences,
                                              @Nullable String redgifsId) {
        try {
            // Get valid token
            String accessToken = getValidAccessToken(redgifsRetrofit, currentAccountSharedPreferences);
            if (accessToken.isEmpty()) {
                return null;
            }

            Response<String> response = redgifsRetrofit
                    .create(RedgifsAPI.class)
                    .getRedgifsData(APIUtils.getRedgifsOAuthHeader(accessToken),
                            redgifsId, APIUtils.USER_AGENT)
                    .execute();
            if (response.isSuccessful()) {
                return parseRedgifsVideoLinks(response.body(), currentAccountSharedPreferences);
            } else if (response.code() == 401) {
                // Token expired, try once more with new token
                accessToken = refreshAccessToken(redgifsRetrofit, currentAccountSharedPreferences);
                if (!accessToken.isEmpty()) {
                    response = redgifsRetrofit
                            .create(RedgifsAPI.class)
                            .getRedgifsData(
                                    APIUtils.getRedgifsOAuthHeader(accessToken),
                                    redgifsId, APIUtils.USER_AGENT)
                            .execute();
                    if (response.isSuccessful()) {
                        return parseRedgifsVideoLinks(response.body(), currentAccountSharedPreferences);
                    }
                }

                return null;
            } else {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void fetchRedgifsVideoLinksInRecyclerViewAdapter(Executor executor, Handler handler,
                                                                   Call<String> redgifsCall,
                                                                   FetchVideoLinkListener fetchVideoLinkListener) {
        executor.execute(() -> {
            try {
                Response<String> response = redgifsCall.execute();
                if (response.isSuccessful()) {
                    parseRedgifsVideoLinks(handler, response.body(), fetchVideoLinkListener, null);
                } else {
                    handler.post(() -> fetchVideoLinkListener.failed(null));
                }
            } catch (IOException e) {
                e.printStackTrace();
                handler.post(() -> fetchVideoLinkListener.failed(null));
            }
        });
    }

    private static void parseRedgifsVideoLinks(Handler handler, @Nullable String response,
                                              FetchVideoLinkListener fetchVideoLinkListener,
                                              @Nullable SharedPreferences currentAccountSharedPreferences) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            JSONObject gif = jsonResponse.getJSONObject(JSONUtils.GIF_KEY);
            JSONObject urls = gif.getJSONObject(JSONUtils.URLS_KEY);

            boolean preferHd = currentAccountSharedPreferences == null || currentAccountSharedPreferences.getBoolean(
                    ml.docilealligator.infinityforreddit.activities.ReelsSettingsActivity.PREF_QUALITY_HD, true);

            String mp4 = null;
            if (!preferHd && urls.has("sd")) {
                mp4 = urls.getString("sd");
            } else if (urls.has(JSONUtils.HD_KEY)) {
                mp4 = urls.getString(JSONUtils.HD_KEY);
            } else if (urls.has("sd")) {
                mp4 = urls.getString("sd");
            }

            if (mp4 == null) {
                handler.post(() -> fetchVideoLinkListener.failed(null));
                return;
            }

            if (mp4.contains("-silent")) {
                mp4 = mp4.substring(0, mp4.indexOf("-silent")) + ".mp4";
            }
            final String mp4Name = mp4;
            handler.post(() -> fetchVideoLinkListener.onFetchRedgifsVideoLinkSuccess(mp4Name, mp4Name));
        } catch (JSONException e) {
            e.printStackTrace();
            handler.post(() -> fetchVideoLinkListener.failed(null));
        }
    }

    @Nullable
    private static String parseRedgifsVideoLinks(@Nullable String response, @Nullable SharedPreferences currentAccountSharedPreferences) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            JSONObject gif = jsonResponse.getJSONObject(JSONUtils.GIF_KEY);
            JSONObject urls = gif.getJSONObject(JSONUtils.URLS_KEY);

            boolean preferHd = currentAccountSharedPreferences == null || currentAccountSharedPreferences.getBoolean(
                    ml.docilealligator.infinityforreddit.activities.ReelsSettingsActivity.PREF_QUALITY_HD, true);

            if (!preferHd && urls.has("sd")) {
                return urls.getString("sd");
            } else if (urls.has(JSONUtils.HD_KEY)) {
                return urls.getString(JSONUtils.HD_KEY);
            } else if (urls.has("sd")) {
                return urls.getString("sd");
            } else {
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }



    private static String getValidAccessToken(Retrofit redgifsRetrofit, SharedPreferences currentAccountSharedPreferences) {
        // Check if existing token is valid
        APIUtils.RedgifsAuthToken currentToken = APIUtils.REDGIFS_TOKEN.get();
        if (currentToken.isValid()) {
            return currentToken.token;
        }

        // Get new token if current one is invalid
        return refreshAccessToken(redgifsRetrofit, currentAccountSharedPreferences);
    }

    private static String refreshAccessToken(Retrofit redgifsRetrofit, SharedPreferences currentAccountSharedPreferences) {
        try {
            RedgifsAPI api = redgifsRetrofit.create(RedgifsAPI.class);
            retrofit2.Response<String> response = api.getRedgifsTemporaryToken().execute();

            if (response.isSuccessful() && response.body() != null) {
                String newAccessToken = new JSONObject(response.body()).getString("token");

                // Update both the atomic reference and shared preferences
                APIUtils.RedgifsAuthToken newToken = APIUtils.RedgifsAuthToken.expireIn1day(newAccessToken);
                APIUtils.REDGIFS_TOKEN.set(newToken);
                currentAccountSharedPreferences.edit().putString(SharedPreferencesUtils.REDGIFS_ACCESS_TOKEN, newAccessToken).apply();

                return newAccessToken;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
