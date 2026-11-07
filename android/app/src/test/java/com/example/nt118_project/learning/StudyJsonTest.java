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

    @Test public void quizFeedbackStoresCanonicalAnswerOnMatchingId() {
        JsonArray questions = JsonParser.parseString("[{\"id\":31},{\"id\":27}]").getAsJsonArray();
        com.google.gson.JsonObject feedback = JsonParser.parseString(
            "{\"question_id\":27,\"answer\":{\"order\":[\"b\",\"a\",\"c\"]},\"is_correct\":true}"
        ).getAsJsonObject();
        org.junit.Assert.assertTrue(StudyJson.applyQuizFeedback(questions, 27, feedback));
        assertEquals("[\"b\",\"a\",\"c\"]", questions.get(1).getAsJsonObject().getAsJsonObject("answer").get("order").toString());
        org.junit.Assert.assertFalse(questions.get(0).getAsJsonObject().has("feedback"));
        feedback.getAsJsonObject("answer").getAsJsonArray("order").add("d");
        assertEquals("[\"b\",\"a\",\"c\"]", questions.get(1).getAsJsonObject().getAsJsonObject("answer").get("order").toString());
    }

    @Test public void unrelatedQuizFeedbackDoesNotChangeQuestion() {
        JsonArray questions = JsonParser.parseString("[{\"id\":27}]").getAsJsonArray();
        org.junit.Assert.assertFalse(StudyJson.applyQuizFeedback(questions, 27,
            JsonParser.parseString("{\"question_id\":31,\"answer\":[1]}").getAsJsonObject()));
        org.junit.Assert.assertFalse(questions.get(0).getAsJsonObject().has("answer"));
    }

    @Test public void quizFeedbackPreservesMatchingPairsAndNormalizedFillAnswer() {
        JsonArray questions = JsonParser.parseString("[{\"id\":27},{\"id\":31}]").getAsJsonArray();
        org.junit.Assert.assertTrue(StudyJson.applyQuizFeedback(questions, 27,
            JsonParser.parseString("{\"question_id\":27,\"answer\":{\"pairs\":[{\"left_id\":\"a\",\"right_id\":\"b\"}]}} ").getAsJsonObject()));
        assertEquals("b", questions.get(0).getAsJsonObject().getAsJsonObject("answer")
            .getAsJsonArray("pairs").get(0).getAsJsonObject().get("right_id").getAsString());
        org.junit.Assert.assertTrue(StudyJson.applyQuizFeedback(questions, 31,
            JsonParser.parseString("{\"question_id\":31,\"answer\":\"confirm\"}").getAsJsonObject()));
        assertEquals("confirm", questions.get(1).getAsJsonObject().get("answer").getAsString());
    }
}
