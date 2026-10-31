package com.example.nt118_project.learning;

import java.util.List;

public final class VocabularySession {
    public long lesson_id;
    public int total_words;
    public int passed_words;
    public int batch_number;
    public int total_batches;
    public int required_percent;
    public String completed_at;
    public String state;
    public List<Word> words;
    public List<Long> studied_ids;
    public Check check;

    public static final class Word {
        public long id;
        public String word;
        public String meaning_vi;
        public String ipa;
        public String part_of_speech;
        public String example_sentence;
    }

    public static final class WordReview {
        public List<Word> rows;
        public int page;
        public int total;
        public int limit;
    }

    public static final class Question {
        public long vocabulary_id;
        public String prompt;
        public String part_of_speech;
        public String hint;
    }

    public static final class Check {
        public String id;
        public int required_percent;
        public int total;
        public List<Question> questions;
        public Result result;
    }

    public static final class Answer {
        public long vocabulary_id;
        public String answer;

        public Answer(long id, String value) {
            vocabulary_id = id;
            answer = value;
        }
    }

    public static final class Review {
        public long vocabulary_id;
        public String prompt;
        public String answer;
        public String correct_answer;
        public boolean is_correct;
    }

    public static final class Result {
        public boolean passed;
        public boolean lesson_completed;
        public int correct_count;
        public int total;
        public int required_correct;
        public List<Review> review;
    }
}
