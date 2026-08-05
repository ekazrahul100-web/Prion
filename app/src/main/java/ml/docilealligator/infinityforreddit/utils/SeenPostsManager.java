package ml.docilealligator.infinityforreddit.utils;

import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages "seen posts" tracking with namespace support.
 * Each namespace (e.g., "home", "reels") maintains its own independent pool
 * so that marking a post as seen in one context does not affect others.
 *
 * Posts automatically expire after 48 hours.
 */
public class SeenPostsManager {
    private static final String KEY_DB_PREFIX = "seen_posts_db_";
    private static final long EXPIRY_MS = 48 * 60 * 60 * 1000L; // 48 hours

    // One ConcurrentHashMap per namespace
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> namespaces = new ConcurrentHashMap<>();
    private static final Set<String> initializedNamespaces = ConcurrentHashMap.newKeySet();

    private static String keyFor(String namespace) {
        return KEY_DB_PREFIX + namespace;
    }

    private static synchronized void initNamespace(SharedPreferences prefs, String namespace) {
        if (initializedNamespaces.contains(namespace)) return;

        ConcurrentHashMap<String, Long> map = new ConcurrentHashMap<>();
        String jsonStr = prefs.getString(keyFor(namespace), null);
        if (jsonStr != null) {
            try {
                JSONObject json = new JSONObject(jsonStr);
                long cutoff = System.currentTimeMillis() - EXPIRY_MS;
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    long timestamp = json.getLong(key);
                    if (timestamp >= cutoff) {
                        map.put(key, timestamp);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        namespaces.put(namespace, map);
        initializedNamespaces.add(namespace);
    }

    private static void saveNamespace(SharedPreferences prefs, String namespace) {
        ConcurrentHashMap<String, Long> map = namespaces.get(namespace);
        if (map == null) return;
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, Long> entry : map.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            prefs.edit().putString(keyFor(namespace), json.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // ─── Namespaced API ───

    public static void markSeen(SharedPreferences prefs, String postId, String namespace) {
        initNamespace(prefs, namespace);
        ConcurrentHashMap<String, Long> map = namespaces.get(namespace);
        if (map != null && !map.containsKey(postId)) {
            map.put(postId, System.currentTimeMillis());
            saveNamespace(prefs, namespace);
        }
    }

    public static boolean hasSeen(SharedPreferences prefs, String postId, String namespace) {
        initNamespace(prefs, namespace);
        ConcurrentHashMap<String, Long> map = namespaces.get(namespace);
        return map != null && map.containsKey(postId);
    }

    public static void clearNamespace(SharedPreferences prefs, String namespace) {
        ConcurrentHashMap<String, Long> map = namespaces.get(namespace);
        if (map != null) {
            map.clear();
        }
        prefs.edit().remove(keyFor(namespace)).apply();
    }

    // ─── Backward-compatible API (defaults to "home" namespace) ───

    public static void markSeen(SharedPreferences prefs, String postId) {
        markSeen(prefs, postId, "home");
    }

    public static boolean hasSeen(SharedPreferences prefs, String postId) {
        return hasSeen(prefs, postId, "home");
    }

    // Legacy init — no longer needed but kept for compatibility
    public static synchronized void init(SharedPreferences prefs) {
        initNamespace(prefs, "home");
    }

    // Legacy save — no longer needed but kept for compatibility
    public static void save(SharedPreferences prefs) {
        saveNamespace(prefs, "home");
    }
}
