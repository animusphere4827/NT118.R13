package com.example.nt118_project.learning;

import com.google.gson.*;
import java.util.*;
import static com.example.nt118_project.learning.StudyJson.*;

public final class QuizInput {
    private QuizInput(){}
    public static String error(JsonObject question,JsonElement answer){
        String type=value(question,"type");JsonObject payload=question.getAsJsonObject("payload");
        if(type.equals("FILL_BLANK")){
            return answer!=null&&answer.isJsonPrimitive()&&answer.getAsJsonPrimitive().isString()&&!answer.getAsString().trim().isEmpty()&&answer.getAsString().length()<=500?null:"Nhập từ hoặc cụm từ, tối đa 500 ký tự.";
        }
        if(answer==null||!answer.isJsonObject())return "Hoàn thành các lựa chọn trước khi kiểm tra.";
        JsonObject object=answer.getAsJsonObject();
        if(type.equals("MATCH")){
            JsonArray pairs=rows(object,"pairs");Set<String> left=new HashSet<>(),right=new HashSet<>();
            for(JsonElement pair:pairs){if(!pair.isJsonObject())return "Cặp nối chưa hợp lệ.";left.add(value(pair.getAsJsonObject(),"left_id"));right.add(value(pair.getAsJsonObject(),"right_id"));}
            if(pairs.size()!=rows(payload,"left").size()||left.size()!=pairs.size()||right.size()!=pairs.size())return "Nối đủ mỗi từ với một nghĩa khác nhau.";
            for(JsonElement row:rows(payload,"left"))if(!left.contains(value(row.getAsJsonObject(),"id")))return "Cặp nối chưa hợp lệ.";
            for(JsonElement row:rows(payload,"right"))if(!right.contains(value(row.getAsJsonObject(),"id")))return "Cặp nối chưa hợp lệ.";
            return null;
        }
        JsonArray order=rows(object,"order"),tokens=rows(payload,"tokens");Set<String> ids=new HashSet<>();
        for(JsonElement id:order){if(!id.isJsonPrimitive())return "Thứ tự từ chưa hợp lệ.";ids.add(id.getAsString());}
        if(order.size()!=tokens.size()||ids.size()!=order.size())return "Chọn đủ tất cả từ để tạo câu.";
        for(JsonElement row:tokens)if(!ids.contains(value(row.getAsJsonObject(),"id")))return "Thứ tự từ chưa hợp lệ.";
        return null;
    }
}
