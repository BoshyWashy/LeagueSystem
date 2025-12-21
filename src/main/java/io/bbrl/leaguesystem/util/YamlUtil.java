package io.bbrl.leaguesystem.util;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public final class YamlUtil {

    /* ---------- Gson that skips ALL JDK internals ---------- */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override public boolean shouldSkipField(FieldAttributes f) {
                    return isJdkInternal(f.getDeclaredType().getTypeName());
                }
                @Override public boolean shouldSkipClass(Class<?> clazz) {
                    return isJdkInternal(clazz.getName());
                }
                private boolean isJdkInternal(String typeName) {
                    return typeName.startsWith("java.") || typeName.startsWith("javax.") || typeName.startsWith("sun.");
                }
            })
            .create();

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    /* ---------- plain YAML helpers ---------- */
    public static void saveYaml(File f, Map<String, Object> data) {
        /* 1.  never null */
        if (data == null) data = new LinkedHashMap<>();

        /* 2.  force at least one key so YamlConfiguration#set is ALWAYS called */
        if (data.isEmpty()) data.put("__placeholder__", "");

        /* 3.  ensure parent folder exists */
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        /* 4.  load & set */
        YamlConfiguration yml = new YamlConfiguration();
        data.forEach(yml::set);

        /* 5.  write – any IOException is printed (not swallowed) */
        try {
            yml.save(f);
        } catch (IOException e) {
            System.err.println("[LeagueSystem] FAILED to write " + f.getAbsolutePath());
            e.printStackTrace();
        }
    }

    public static List<String> getList(File f, String path) {
        return YamlConfiguration.loadConfiguration(f).getStringList(path);
    }

    /* ---------- object <-> YAML via Gson ---------- */
    public static <T> T loadObject(File f, Class<T> clazz) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        Map<String, Object> map = yml.getValues(false);
        /* remove placeholder if it exists */
        map.remove("__placeholder__");
        String json = GSON.toJson(map);
        return GSON.fromJson(json, clazz);
    }

    public static void saveObject(File f, Object obj) {
        String json = GSON.toJson(obj);
        Map<String, Object> map = GSON.fromJson(json, MAP_TYPE);
        saveYaml(f, map);
    }
}