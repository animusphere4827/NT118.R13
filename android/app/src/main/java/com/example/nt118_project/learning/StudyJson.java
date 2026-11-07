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
    public static int questionIndex(JsonArray questions, long questionId) {
        for (int i = 0; i < questions.size(); i++) {
            if (number(questions.get(i).getAsJsonObject(), "id") == questionId) return i;
        }
        return -1;
    }

    public static boolean applyQuizFeedback(JsonArray questions, long questionId, JsonObject feedback) {
        int index = questionIndex(questions, questionId);
        if (index < 0 || number(feedback, "question_id") != questionId || !feedback.has("answer")) return false;
        JsonObject question = questions.get(index).getAsJsonObject();
        question.add("answer", feedback.get("answer").deepCopy());
        question.add("feedback", feedback.deepCopy());
        return true;
    }

    public static JsonObject body(String key,String value) {
        JsonObject body=new JsonObject();body.addProperty(key,value);return body;
    }
}
