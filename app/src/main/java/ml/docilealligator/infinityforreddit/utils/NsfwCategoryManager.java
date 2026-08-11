package ml.docilealligator.infinityforreddit.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NsfwCategoryManager {

    public static final String PREF_CUSTOM_CATEGORIES = "pref_custom_nsfw_categories";
    public static final String PREF_CATEGORY_OVERRIDES = "pref_category_overrides";
    public static final String PREF_SELECTED_CATEGORY_NAME = "pref_reels_selected_nsfw_category_name";

    public static Map<String, List<String>> loadCategories(Context context, SharedPreferences prefs) {
        Map<String, List<String>> map = new LinkedHashMap<>();

        // 1. Built-in categories from assets
        try {
            InputStream is = context.getAssets().open("nsfw411_categories.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            String jsonStr = new String(buffer, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(jsonStr);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.equalsIgnoreCase("Gay") || key.equalsIgnoreCase("Trans")) {
                    continue; // Skip Gay and Trans categories
                }
                JSONArray arr = json.getJSONArray(key);
                List<String> subList = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    subList.add(arr.getString(i));
                }
                map.put(key, subList);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Custom Categories from SharedPreferences
        try {
            String customJsonStr = prefs.getString(PREF_CUSTOM_CATEGORIES, "[]");
            JSONArray customArr = new JSONArray(customJsonStr);
            for (int i = 0; i < customArr.length(); i++) {
                JSONObject obj = customArr.getJSONObject(i);
                String name = obj.getString("name");
                JSONArray subsArr = obj.getJSONArray("subreddits");
                List<String> subList = new ArrayList<>();
                for (int j = 0; j < subsArr.length(); j++) {
                    subList.add(subsArr.getString(j));
                }
                map.put("⭐ " + name, subList);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3. Category Overrides (modified subreddits list for built-in or custom)
        try {
            String overrideJsonStr = prefs.getString(PREF_CATEGORY_OVERRIDES, "{}");
            JSONObject overrideJson = new JSONObject(overrideJsonStr);
            Iterator<String> keys = overrideJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (map.containsKey(key)) {
                    JSONArray arr = overrideJson.getJSONArray(key);
                    List<String> subList = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        subList.add(arr.getString(i));
                    }
                    map.put(key, subList);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return map;
    }

    public static List<String> getAllSubreddits(Map<String, List<String>> map) {
        List<String> all = new ArrayList<>();
        for (List<String> list : map.values()) {
            for (String sub : list) {
                if (!all.contains(sub)) {
                    all.add(sub);
                }
            }
        }
        return all;
    }

    public static void saveCustomCategory(SharedPreferences prefs, String name, List<String> subreddits) {
        try {
            String customJsonStr = prefs.getString(PREF_CUSTOM_CATEGORIES, "[]");
            JSONArray customArr = new JSONArray(customJsonStr);
            
            // Check if existing and update
            boolean found = false;
            for (int i = 0; i < customArr.length(); i++) {
                JSONObject obj = customArr.getJSONObject(i);
                if (obj.getString("name").equalsIgnoreCase(name)) {
                    obj.put("subreddits", new JSONArray(subreddits));
                    found = true;
                    break;
                }
            }
            if (!found) {
                JSONObject newObj = new JSONObject();
                newObj.put("name", name);
                newObj.put("subreddits", new JSONArray(subreddits));
                customArr.put(newObj);
            }
            prefs.edit().putString(PREF_CUSTOM_CATEGORIES, customArr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveCategoryOverride(SharedPreferences prefs, String categoryName, List<String> subreddits) {
        try {
            String overrideJsonStr = prefs.getString(PREF_CATEGORY_OVERRIDES, "{}");
            JSONObject overrideJson = new JSONObject(overrideJsonStr);
            overrideJson.put(categoryName, new JSONArray(subreddits));
            prefs.edit().putString(PREF_CATEGORY_OVERRIDES, overrideJson.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteCustomCategory(SharedPreferences prefs, String name) {
        try {
            String customJsonStr = prefs.getString(PREF_CUSTOM_CATEGORIES, "[]");
            JSONArray customArr = new JSONArray(customJsonStr);
            JSONArray newArr = new JSONArray();
            String cleanName = name.startsWith("⭐ ") ? name.substring(2) : name;
            for (int i = 0; i < customArr.length(); i++) {
                JSONObject obj = customArr.getJSONObject(i);
                if (!obj.getString("name").equalsIgnoreCase(cleanName)) {
                    newArr.put(obj);
                }
            }
            prefs.edit().putString(PREF_CUSTOM_CATEGORIES, newArr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
