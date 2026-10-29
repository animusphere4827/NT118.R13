package com.example.nt118_project.data.remote.dto;

import java.util.List;

/** Một mục của Free Dictionary API (api.dictionaryapi.dev); chỉ giữ các trường app dùng. */
public class FreeDictionaryEntryDto {
    public String word;
    public String phonetic;
    public List<Phonetic> phonetics;
    public List<Meaning> meanings;

    public static class Phonetic {
        public String text;
        public String audio;
    }

    public static class Meaning {
        public String partOfSpeech;
        public List<Definition> definitions;
        public List<String> synonyms;
        public List<String> antonyms;
    }

    public static class Definition {
        public String definition;
        public String example;
        public List<String> synonyms;
        public List<String> antonyms;
    }
}
