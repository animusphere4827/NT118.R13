package com.example.nt118_project.ui.learning;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ReadingWordsTest {
    private static final String TEXT = "Dear team, the meeting is in Room 204. Don’t forget the follow-up e-mail.";

    @Test public void picksWordUnderFinger() {
        assertEquals("meeting", ReadingWords.wordAt(TEXT, TEXT.indexOf("meeting") + 3));
        assertEquals("Dear", ReadingWords.wordAt(TEXT, 0));
    }

    @Test public void tapRightAfterWordStillPicksIt() {
        assertEquals("team", ReadingWords.wordAt(TEXT, TEXT.indexOf(",")));
        assertEquals("mail", ReadingWords.wordAt("mail", 4));
    }

    @Test public void keepsApostropheAndHyphenInsideWord() {
        assertEquals("Don't", ReadingWords.wordAt(TEXT, TEXT.indexOf("Don") + 1));
        assertEquals("follow-up", ReadingWords.wordAt(TEXT, TEXT.indexOf("follow") + 2));
        assertEquals("e-mail", ReadingWords.wordAt(TEXT, TEXT.indexOf("e-mail") + 3));
    }

    @Test public void ignoresSpacesNumbersAndBadOffsets() {
        assertNull(ReadingWords.wordAt(TEXT, TEXT.indexOf("204") + 1));
        assertNull(ReadingWords.wordAt(TEXT, TEXT.indexOf("204.") + 3));
        assertNull(ReadingWords.wordAt(TEXT, -1));
        assertNull(ReadingWords.wordAt(TEXT, TEXT.length() + 1));
        assertNull(ReadingWords.wordAt("  ", 1));
    }
}
