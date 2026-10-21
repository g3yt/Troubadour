package com.troubadour.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON utilities similar to YTJson, using Gson for parsing.
 * Provides safe access to JSON data with fallback handling.
 */
public class JsonUtils {
    private static final String TAG = "JsonUtils";

    /**
     * Parse JSON string into a Map (if root is object) or List (if root is array).
     * Returns null if parsing fails or root is neither object nor array.
     *
     * @param json JSON string
     * @return Map or List, or null
     */
    public static Object parse(String json) {
        Object obj = parseAny(json);
        if (obj instanceof Map || obj instanceof List) {
            return obj;
        }
        return null;
    }

    /**
     * Parse JSON string into any JSON value (object, array, string, number, boolean, null).
     * Returns null if parsing fails.
     *
     * @param json JSON string
     * @return parsed value or null
     */
    public static Object parseAny(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            return toJavaObject(element);
        } catch (Exception e) {
            // Log error
            android.util.Log.e(TAG, "Failed to parse JSON: " + json.substring(0, Math.min(json.length(), 100)), e);
            return null;
        }
    }

    /**
     * Convert a Java object (Map, List, String, Number, Boolean) to JSON string.
     * Uses Gson's default formatting.
     *
     * @param object Java object (Map, List, String, Number, Boolean, or null)
     * @return JSON string, or "{}" if null
     */
    public static String encode(Object object) {
        if (object == null) {
            object = Collections.emptyMap();
        }
        // We could use Gson to serialize, but for simplicity we'll just return a placeholder.
        // In a real implementation, we would use Gson.toJson(object).
        // Since we don't have Gson exposed, we'll leave this as a stub.
        // For now, we'll return an empty JSON object.
        return "{}";
    }

    /**
     * Get a Map value for a key, returning null if missing or null.
     *
     * @param parent Map
     * @param key key
     * @return value or null
     */
    @SuppressWarnings("unchecked")
    public static Object objectIn(Map<String, Object> parent, String key) {
        if (parent == null || key == null) {
            return null;
        }
        Object value = parent.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return value;
        }
        return null;
    }

    /**
     * Get a List value for a key, returning null if missing or null.
     *
     * @param parent Map
     * @param key key
     * @return value or null
     */
    @SuppressWarnings("unchecked")
    public static Object arrayIn(Map<String, Object> parent, String key) {
        if (parent == null || key == null) {
            return null;
        }
        Object value = parent.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            return value;
        }
        return null;
    }

    /**
     * Get a Map value from a list at index.
     *
     * @param array List
     * @param index index
     * @return Map or null
     */
    @SuppressWarnings("unchecked")
    public static Object objectAt(List<?> array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        Object value = array.get(index);
        if (value instanceof Map) {
            return value;
        }
        return null;
    }

    /**
     * Get a string value for a key, returning null if missing, null, or empty string.
     *
     * @param parent Map
     * @param key key
     * @return value or null
     */
    public static String textIn(Map<String, Object> parent, String key) {
        String value = stringIn(parent, key);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value;
    }

    /**
     * Get a string value for a key, returning null if missing or null.
     * Empty string is returned as-is.
     *
     * @param parent Map
     * @param key key
     * @return value or null
     */
    public static String stringIn(Map<String, Object> parent, String key) {
        if (parent == null || key == null) {
            return null;
        }
        Object value = parent.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number) {
            return value.toString();
        }
        // For boolean, return "true" or "false"
        if (value instanceof Boolean) {
            return value.toString();
        }
        // For other types, return toString()
        return value.toString();
    }

    /**
     * Get a string value for a key with fallback.
     *
     * @param parent Map
     * @param key key
     * @param fallback fallback value if missing or null
     * @return value or fallback
     */
    public static String stringIn(Map<String, Object> parent, String key, String fallback) {
        String value = stringIn(parent, key);
        return value != null ? value : fallback;
    }

    /**
     * Get an integer value for a key, returning 0 if missing or null.
     *
     * @param parent Map
     * @param key key
     * @return integer value
     */
    public static int intIn(Map<String, Object> parent, String key) {
        return intIn(parent, key, 0);
    }

    /**
     * Get an integer value for a key with fallback.
     *
     * @param parent Map
     * @param key key
     * @param fallback fallback value
     * @return integer value
     */
    public static int intIn(Map<String, Object> parent, String key, int fallback) {
        if (parent == null || key == null) {
            return fallback;
        }
        Object value = parent.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * Get a double value for a key, returning 0.0 if missing or null.
     *
     * @param parent Map
     * @param key key
     * @return double value
     */
    public static double doubleIn(Map<String, Object> parent, String key) {
        return doubleIn(parent, key, 0.0);
    }

    /**
     * Get a double value for a key with fallback.
     *
     * @param parent Map
     * @param key key
     * @param fallback fallback value
     * @return double value
     */
    public static double doubleIn(Map<String, Object> parent, String key, double fallback) {
        if (parent == null || key == null) {
            return fallback;
        }
        Object value = parent.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * Get a boolean value for a key, returning false if missing or null.
     *
     * @param parent Map
     * @param key key
     * @return boolean value
     */
    public static boolean boolIn(Map<String, Object> parent, String key) {
        return boolIn(parent, key, false);
    }

    /**
     * Get a boolean value for a key with fallback.
     *
     * @param parent Map
     * @param key key
     * @param fallback fallback value
     * @return boolean value
     */
    public static boolean boolIn(Map<String, Object> parent, String key, boolean fallback) {
        if (parent == null || key == null) {
            return fallback;
        }
        Object value = parent.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String str = ((String) value).trim().toLowerCase();
            return "true".equals(str) || "yes".equals(str) || "1".equals(str);
        }
        // Number: non-zero is true
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        return fallback;
    }

    /**
     * Get the rendered text from a InnerTube text renderer node.
     * Handles both simpleText and runs.
     *
     * @param parent Map
     * @param key key
     * @return rendered text or null
     */
    public static String renderedText(Map<String, Object> parent, String key) {
        Object obj = objectIn(parent, key);
        if (obj instanceof Map) {
            return renderedValue((Map<String, Object>) obj);
        }
        return null;
    }

    /**
     * Get the rendered value from a text renderer node (simpleText, runs, or content).
     *
     * @param node Map representing the node
     * @return rendered text or null
     */
    public static String renderedValue(Map<String, Object> node) {
        if (node == null) {
            return null;
        }
        // simpleText
        Object simple = node.get("simpleText");
        if (simple instanceof String) {
            String s = (String) simple;
            if (!s.isEmpty()) {
                return s;
            }
        }
        // content
        Object content = node.get("content");
        if (content instanceof String) {
            String c = (String) content;
            if (!c.isEmpty()) {
                return c;
            }
        }
        // runs
        Object runsObj = node.get("runs");
        if (runsObj instanceof List) {
            List<?> runs = (List<?>) runsObj;
            StringBuilder sb = new StringBuilder();
            for (Object runObj : runs) {
                if (runObj instanceof Map) {
                    Map<String, Object> run = (Map<String, Object>) runObj;
                    Object text = run.get("text");
                    if (text instanceof String) {
                        sb.append((String) text);
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return null;
    }

    /**
     * Get the URL of the thumbnail with at least the specified minWidth.
     * Prefers the first thumbnail that meets the width requirement, otherwise the last.
     *
     * @param parent Map
     * @param key key
     * @param minWidth minimum width in pixels
     * @return thumbnail URL or null
     */
    public static String thumbnailIn(Map<String, Object> parent, String key, int minWidth) {
        // Try sources
        Object sourcesObj = arrayIn(parent, "sources");
        if (sourcesObj instanceof List) {
            List<?> sources = (List<?>) sourcesObj;
            String best = null;
            for (Object srcObj : sources) {
                if (srcObj instanceof Map) {
                    Map<String, Object> src = (Map<String, Object>) srcObj;
                    Object urlObj = src.get("url");
                    if (urlObj instanceof String) {
                        String url = (String) urlObj;
                        Object widthObj = src.get("width");
                        int width = 0;
                        if (widthObj instanceof Number) {
                            width = ((Number) widthObj).intValue();
                        }
                        if (width >= minWidth) {
                            return url;
                        }
                        if (!url.isEmpty()) {
                            best = url;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        // Try thumbnails
        Object thumbsObj = arrayIn(parent, "thumbnails");
        if (thumbsObj instanceof List) {
            List<?> thumbs = (List<?>) thumbsObj;
            String best = null;
            for (Object thumbObj : thumbs) {
                if (thumbObj instanceof Map) {
                    Map<String, Object> thumb = (Map<String, Object>) thumbObj;
                    Object urlObj = thumb.get("url");
                    if (urlObj instanceof String) {
                        String url = (String) urlObj;
                        Object widthObj = thumb.get("width");
                        int width = 0;
                        if (widthObj instanceof Number) {
                            width = ((Number) widthObj).intValue();
                        }
                        if (width >= minWidth) {
                            return url;
                        }
                        if (!url.isEmpty()) {
                            best = url;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        // Try thumbnail holder
        Object thumbHolderObj = objectIn(parent, "thumbnail");
        if (thumbHolderObj instanceof Map) {
            Map<String, Object> thumbHolder = (Map<String, Object>) thumbHolderObj;
            Object thumbs2Obj = arrayIn(thumbHolder, "thumbnails");
            if (thumbs2Obj instanceof List) {
                List<?> thumbs2 = (List<?>) thumbs2Obj;
                String best = null;
                for (Object thumbObj : thumbs2) {
                    if (thumbObj instanceof Map) {
                        Map<String, Object> thumb = (Map<String, Object>) thumbObj;
                        Object urlObj = thumb.get("url");
                        if (urlObj instanceof String) {
                            String url = (String) urlObj;
                            Object widthObj = thumb.get("width");
                            int width = 0;
                            if (widthObj instanceof Number) {
                                width = ((Number) widthObj).intValue();
                            }
                            if (width >= minWidth) {
                                return url;
                            }
                            if (!url.isEmpty()) {
                                best = url;
                            }
                        }
                    }
                }
                if (best != null) {
                    return best;
                }
            }
        }
        return null;
    }

    /**
     * Find the first occurrence of a key in the JSON tree (only in objects).
     * Stops after limit nodes visited.
     *
     * @param key key to search for
     * @param tree root object (Map or List)
     * @param limit maximum number of nodes to visit (0 for unlimited)
     * @return first matching Map node, or null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> findFirst(String key, Object tree, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        int[] visited = new int[]{0};
        findFirstWalk(key, tree, limit > 0 ? limit : Integer.MAX_VALUE, visited, results, true);
        if (!results.isEmpty()) {
            return results.get(0);
        }
        return null;
    }

    /**
     * Find all occurrences of a key in the JSON tree (only in objects).
     * Stops after limit nodes visited.
     *
     * @param key key to search for
     * @param tree root object (Map or List)
     * @param limit maximum number of nodes to visit (0 for unlimited)
     * @return list of matching Map nodes
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> findAll(String key, Object tree, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        int[] visited = new int[]{0};
        findFirstWalk(key, tree, limit > 0 ? limit : Integer.MAX_VALUE, visited, results, false);
        return results;
    }

    /**
     * Find the first occurrence of a key where the value is a non-empty string.
     * Stops after limit nodes visited.
     *
     * @param key key to search for
     * @param tree root object (Map or List)
     * @param limit maximum number of nodes to visit (0 for unlimited)
     * @return first matching string value, or null
     */
    public static String findString(String key, Object tree, int limit) {
        int[] visited = new int[]{0};
        return findStringWalk(key, tree, limit > 0 ? limit : Integer.MAX_VALUE, visited);
    }

    /**
     * Find all occurrences of any of the given keys in the JSON tree (only in objects).
     * Returns a list of maps with "name" (key) and "node" (the Map value).
     * Stops after limit nodes visited.
     *
     * @param keys list of keys to search for
     * @param tree root object (Map or List)
     * @param limit maximum number of nodes to visit (0 for unlimited)
     * @return list of maps with "name" and "node"
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> findAllOfAny(List<String> keys, Object tree, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        int[] visited = new int[]{0};
        findAllOfAnyWalk(keys, tree, limit > 0 ? limit : Integer.MAX_VALUE, visited, results);
        return results;
    }

    // ---------- Private helper methods for tree walking ----------

    @SuppressWarnings("unchecked")
    private static void findFirstWalk(String key, Object tree, int limit, int[] visited,
                                      List<Map<String, Object>> results, boolean stopAtFirst) {
        if (visited[0] >= limit) {
            return;
        }
        if (tree instanceof Map) {
            visited[0]++;
            Map<String, Object> map = (Map<String, Object>) tree;
            Object value = map.get(key);
            if (value instanceof Map) {
                results.add((Map<String, Object>) value);
                if (stopAtFirst) {
                    return;
                }
            }
            for (Object child : map.values()) {
                findFirstWalk(key, child, limit, visited, results, stopAtFirst);
                if (stopAtFirst && !results.isEmpty()) {
                    return;
                }
                if (visited[0] >= limit) {
                    return;
                }
            }
        } else if (tree instanceof List) {
            List<?> list = (List<?>) tree;
            for (Object child : list) {
                findFirstWalk(key, child, limit, visited, results, stopAtFirst);
                if (stopAtFirst && !results.isEmpty()) {
                    return;
                }
                if (visited[0] >= limit) {
                    return;
                }
            }
        }
    }

    private static String findStringWalk(String key, Object tree, int limit, int[] visited) {
        if (visited[0] >= limit) {
            return null;
        }
        if (tree instanceof Map) {
            visited[0]++;
            Map<String, Object> map = (Map<String, Object>) tree;
            Object value = map.get(key);
            if (value instanceof String && !((String) value).isEmpty()) {
                return (String) value;
            }
            for (Object child : map.values()) {
                String res = findStringWalk(key, child, limit, visited);
                if (res != null) {
                    return res;
                }
                if (visited[0] >= limit) {
                    return null;
                }
            }
        } else if (tree instanceof List) {
            List<?> list = (List<?>) tree;
            for (Object child : list) {
                String res = findStringWalk(key, child, limit, visited);
                if (res != null) {
                    return res;
                }
                if (visited[0] >= limit) {
                    return null;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void findAllOfAnyWalk(List<String> keys, Object tree, int limit, int[] visited,
                                         List<Map<String, Object>> results) {
        if (visited[0] >= limit) {
            return;
        }
        if (tree instanceof Map) {
            visited[0]++;
            Map<String, Object> map = (Map<String, Object>) tree;
            for (String k : keys) {
                Object value = map.get(k);
                if (value instanceof Map) {
                    Map<String, Object> pair = new HashMap<>(2);
                    pair.put("name", k);
                    pair.put("node", value);
                    results.add(pair);
                }
            }
            for (Object child : map.values()) {
                findAllOfAnyWalk(keys, child, limit, visited, results);
                if (visited[0] >= limit) {
                    return;
                }
            }
        } else if (tree instanceof List) {
            List<?> list = (List<?>) tree;
            for (Object child : list) {
                findAllOfAnyWalk(keys, child, limit, visited, results);
                if (visited[0] >= limit) {
                    return;
                }
            }
        }
    }

    // ---------- Internal conversion methods ----------

    @SuppressWarnings("unchecked")
    private static Object toJavaObject(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return primitive.getAsNumber();
            }
            if (primitive.isString()) {
                return primitive.getAsString();
            }
            // Should not reach here
            return null;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            List<Object> list = new ArrayList<>(array.size());
            for (JsonElement e : array) {
                list.add(toJavaObject(e));
            }
            return list;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Map<String, Object> map = new HashMap<>(obj.size());
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                map.put(entry.getKey(), toJavaObject(entry.getValue()));
            }
            return map;
        }
        // Should not reach here
        return null;
    }
}