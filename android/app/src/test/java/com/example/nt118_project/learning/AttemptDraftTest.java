package com.example.nt118_project.learning;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AttemptDraftTest {
    @Test public void newerChoicesAreNotLostWhenAnOlderSaveIsAcknowledged(){
        AttemptDraft draft=new AttemptDraft();draft.put(12,"A",false);JsonArray sent=draft.snapshot();
        draft.put(12,"B",true);draft.put(13,"C",false);draft.acknowledge(sent);
        assertEquals(2,draft.snapshot().size());assertEquals("B",draft.snapshot().get(0).getAsJsonObject().get("label").getAsString());
        draft.acknowledge(draft.snapshot());assertTrue(draft.isEmpty());
    }
    @Test public void clearingAnAnswerSurvivesPersistenceAndAppliesToTheCorrectQuestion(){
        AttemptDraft draft=new AttemptDraft();draft.put(15,null,true);AttemptDraft restored=new AttemptDraft();restored.restore(JsonParser.parseString(draft.snapshot().toString()).getAsJsonArray());
        JsonArray questions=JsonParser.parseString("[{\"id\":14,\"selected_label\":\"A\"},{\"id\":15,\"selected_label\":\"B\"}]").getAsJsonArray();
        restored.apply(questions);assertEquals("A",questions.get(0).getAsJsonObject().get("selected_label").getAsString());assertTrue(questions.get(1).getAsJsonObject().get("selected_label").isJsonNull());assertTrue(questions.get(1).getAsJsonObject().get("marked_for_review").getAsBoolean());
    }
    @Test public void snapshotIsIndependentOfFutureEdits(){AttemptDraft draft=new AttemptDraft();draft.put(1,"A",false);JsonArray saved=draft.snapshot();draft.put(1,"D",false);assertEquals("A",saved.get(0).getAsJsonObject().get("label").getAsString());}
    @Test public void clockCannotDisplayNegativeTime(){assertEquals(3000,AttemptDraft.remaining(8000,5000));assertEquals(0,AttemptDraft.remaining(8000,9000));}
}
