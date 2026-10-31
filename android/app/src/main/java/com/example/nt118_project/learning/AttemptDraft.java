package com.example.nt118_project.learning;

import com.google.gson.*;
import java.util.*;

public final class AttemptDraft {
    private final Map<Long,JsonObject> pending=new LinkedHashMap<>();
    public void put(long id,String label,boolean marked) {
        JsonObject row=new JsonObject();row.addProperty("question_id",id);
        if(label==null||label.isEmpty())row.add("label",JsonNull.INSTANCE);else row.addProperty("label",label);
        row.addProperty("marked_for_review",marked);pending.put(id,row);
    }
    public boolean isEmpty(){return pending.isEmpty();}
    public void clear(){pending.clear();}
    public JsonArray snapshot(){JsonArray rows=new JsonArray();for(JsonObject row:pending.values())rows.add(row.deepCopy());return rows;}
    public void restore(JsonArray rows){pending.clear();for(JsonElement row:rows){JsonObject value=row.getAsJsonObject();pending.put(value.get("question_id").getAsLong(),value.deepCopy());}}
    public void acknowledge(JsonArray sent){for(JsonElement item:sent){JsonObject row=item.getAsJsonObject();long id=row.get("question_id").getAsLong();if(row.equals(pending.get(id)))pending.remove(id);}}
    public void apply(JsonArray questions){for(JsonElement item:questions){JsonObject q=item.getAsJsonObject(),change=pending.get(q.get("id").getAsLong());if(change!=null){q.add("selected_label",change.get("label").deepCopy());q.add("marked_for_review",change.get("marked_for_review").deepCopy());}}}
    public static long remaining(long deadlineAtArrival,long elapsedNow){return Math.max(0,deadlineAtArrival-elapsedNow);}
}
