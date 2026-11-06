package com.example.nt118_project.learning;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class StudyJsonTest {
    @Test public void findsQuestionByIdWhenPositionsHaveGaps() {
        JsonArray questions = JsonParser.parseString(
            "[{\"id\":27,\"position\":1},{\"id\":31,\"position\":3},{\"id\":54,\"position\":4}]"
        ).getAsJsonArray();

        assertEquals(1, StudyJson.questionIndex(questions, 31));
        assertEquals(2, StudyJson.questionIndex(questions, 54));
        assertEquals(-1, StudyJson.questionIndex(questions, 3));
    }
}
