package com.example.nt118_project.ui.learning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class GreetingTest {

    @Test
    public void periodBoundaries() {
        assertEquals(Greeting.Period.NIGHT, Greeting.period(4));
        assertEquals(Greeting.Period.MORNING, Greeting.period(5));
        assertEquals(Greeting.Period.MORNING, Greeting.period(10));
        assertEquals(Greeting.Period.NOON, Greeting.period(11));
        assertEquals(Greeting.Period.NOON, Greeting.period(12));
        assertEquals(Greeting.Period.AFTERNOON, Greeting.period(13));
        assertEquals(Greeting.Period.AFTERNOON, Greeting.period(17));
        assertEquals(Greeting.Period.EVENING, Greeting.period(18));
        assertEquals(Greeting.Period.EVENING, Greeting.period(21));
        assertEquals(Greeting.Period.NIGHT, Greeting.period(22));
        assertEquals(Greeting.Period.NIGHT, Greeting.period(0));
    }

    @Test
    public void shortNameTakesLastWord() {
        assertEquals("Thành", Greeting.shortName("  Ngô   Kim Thành "));
        assertEquals("An", Greeting.shortName("An"));
        assertNull(Greeting.shortName("   "));
        assertNull(Greeting.shortName(null));
    }

    @Test
    public void hintIndexStaysInRange() {
        assertEquals(0, Greeting.hintIndex(0, 3));
        assertEquals(2, Greeting.hintIndex(365, 3));
        assertEquals(0, Greeting.hintIndex(10, 0));
    }
}
