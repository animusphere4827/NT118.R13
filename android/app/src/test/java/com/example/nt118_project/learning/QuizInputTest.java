package com.example.nt118_project.learning;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuizInputTest {
    private JsonObject question(String type,String payload){return JsonParser.parseString("{\"type\":\""+type+"\",\"payload\":"+payload+"}").getAsJsonObject();}
    @Test public void blankFillDoesNotSendARequest(){assertNotNull(QuizInput.error(question("FILL_BLANK","{}"),new JsonPrimitive("  ")));assertNull(QuizInput.error(question("FILL_BLANK","{}"),new JsonPrimitive("invoice")));}
    @Test public void matchingRequiresEveryLeftAndRightIdentityExactlyOnce(){
        JsonObject q=question("MATCH","{\"left\":[{\"id\":\"l1\"},{\"id\":\"l2\"}],\"right\":[{\"id\":\"r1\"},{\"id\":\"r2\"}]}");
        assertNotNull(QuizInput.error(q,JsonParser.parseString("{\"pairs\":[{\"left_id\":\"l1\",\"right_id\":\"r1\"},{\"left_id\":\"l2\",\"right_id\":\"r1\"}]}")));
        assertNull(QuizInput.error(q,JsonParser.parseString("{\"pairs\":[{\"left_id\":\"l1\",\"right_id\":\"r2\"},{\"left_id\":\"l2\",\"right_id\":\"r1\"}]}")));
    }
    @Test public void orderingUsesTokenIdsEvenWhenWordsAreIdentical(){
        JsonObject q=question("ORDER","{\"tokens\":[{\"id\":\"a\",\"text\":\"the\"},{\"id\":\"b\",\"text\":\"the\"}]}");
        assertNull(QuizInput.error(q,JsonParser.parseString("{\"order\":[\"b\",\"a\"]}")));
        assertNotNull(QuizInput.error(q,JsonParser.parseString("{\"order\":[\"a\",\"a\"]}")));
        assertNotNull(QuizInput.error(q,JsonParser.parseString("{\"order\":[\"a\",\"foreign\"]}")));
    }
}
