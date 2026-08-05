package ml.docilealligator.infinityforreddit.utils;

import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SeenPostsManager {
    private static final String PREF_NAME = "seen_posts_prefs";
    private static final String KEY_DB = "seen_posts_db";
    
    private static final ConcurrentHashMap<String, Long> seenPosts = new ConcurrentHashMap<>();
    private static boolean isInit = false;

    public static synchronized void init(SharedPreferences prefs) {
        if (isInit) return;
        String jsonStr = prefs.getString(KEY_DB, null);
        if (jsonStr != null) {
            try {
                JSONObject json = new JSONObject(jsonStr);
                long cutoff = System.currentTimeMillis() - (48 * 60 * 60 * 1000L);
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    long timestamp = json.getLong(key);
                    if (timestamp >= cutoff) {
                        seenPosts.put(key, timestamp);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        isInit = true;
    }

    public static void save(SharedPreferences prefs) {
        if (!isInit) return;
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, Long> entry : seenPosts.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            prefs.edit().putString(KEY_DB, json.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void markSeen(SharedPreferences prefs, String postId) {
        if (!isInit) init(prefs);
        if (!seenPosts.containsKey(postId)) {
            seenPosts.put(postId, System.currentTimeMillis());
            save(prefs);
        }
    }

    public static boolean hasSeen(SharedPreferences prefs, String postId) {
        if (!isInit) init(prefs);
        return seenPosts.containsKey(postId);
    }
}
