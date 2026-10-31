package com.example.nt118_project.learning;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class StudyJson {
    private StudyJson() { }
    public static String value(JsonObject object, String key) {
        JsonElement value=object==null?null:object.get(key);
        return value==null||value.isJsonNull()?"":value.getAsString();
    }
    public static long number(JsonObject object,String key) {
        JsonElement value=object==null?null:object.get(key);
        return value==null||value.isJsonNull()?0:value.getAsLong();
    }
    public static boolean yes(JsonObject object,String key) {
        JsonElement value=object==null?null:object.get(key);
        return value!=null&&!value.isJsonNull()&&value.getAsBoolean();
    }
    public static JsonArray rows(JsonObject object,String key) {
        JsonElement value=object==null?null:object.get(key);
        return value!=null&&value.isJsonArray()?value.getAsJsonArray():new JsonArray();
    }
    public static JsonObject body(String key,String value) {
        JsonObject body=new JsonObject();body.addProperty(key,value);return body;
    }
}
