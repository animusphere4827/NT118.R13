package com.example.nt118_project.learning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.nt118_project.R;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PathUnlockPolicyTest {

    private static PathLesson lesson(String id, boolean done) {
        return new PathLesson(id, id, "", PathLesson.Type.VOCABULARY, 5, done);
    }

    private static List<PathUnit> units(PathLesson... lessons) {
        return Collections.singletonList(new PathUnit("u", "U", "", Arrays.asList(lessons)));
    }

    @Test
    public void firstUnfinishedLessonIsCurrentAndLaterOnesAreLocked() {
        PathUnlockPolicy.Result r = PathUnlockPolicy.evaluate(units(
                lesson("a", true), lesson("b", false), lesson("c", false)));
        assertEquals(PathUnlockPolicy.State.DONE, r.entries.get(0).state);
        assertEquals(PathUnlockPolicy.State.IN_PROGRESS, r.entries.get(1).state);
        assertEquals(PathUnlockPolicy.State.LOCKED, r.entries.get(2).state);
        assertNotNull(r.current);
        assertEquals("b", r.current.lesson.id);
        assertEquals(1, r.completedCount);
        assertEquals(33, r.percent());
    }

    @Test
    public void unlockContinuesAcrossUnits() {
        List<PathUnit> path = Arrays.asList(
                new PathUnit("u1", "U1", "", Arrays.asList(lesson("a", true), lesson("b", true))),
                new PathUnit("u2", "U2", "", Arrays.asList(lesson("c", false), lesson("d", false))));
        PathUnlockPolicy.Result r = PathUnlockPolicy.evaluate(path);
        assertEquals("c", r.current.lesson.id);
        assertEquals(PathUnlockPolicy.State.LOCKED, r.entriesOf(path.get(1)).get(1).state);
    }

    @Test
    public void completedLessonAfterCurrentStaysDone() {
        PathUnlockPolicy.Result r = PathUnlockPolicy.evaluate(units(
                lesson("a", false), lesson("b", true), lesson("c", false)));
        assertEquals(PathUnlockPolicy.State.IN_PROGRESS, r.entries.get(0).state);
        assertEquals(PathUnlockPolicy.State.DONE, r.entries.get(1).state);
        assertEquals(PathUnlockPolicy.State.LOCKED, r.entries.get(2).state);
    }

    @Test
    public void finishedPathHasNoCurrentLesson() {
        PathUnlockPolicy.Result r = PathUnlockPolicy.evaluate(units(lesson("a", true), lesson("b", true)));
        assertNull(r.current);
        assertTrue(r.isFinished());
        assertEquals(100, r.percent());
    }

    @Test
    public void emptyPathIsNotFinishedAndHasZeroPercent() {
        PathUnlockPolicy.Result r = PathUnlockPolicy.evaluate(new ArrayList<>());
        assertEquals(0, r.totalCount());
        assertEquals(0, r.percent());
        assertFalse(r.isFinished());
    }

    @Test
    public void onlyLockedLessonsCannotBeOpened() {
        assertTrue(PathUnlockPolicy.canOpen(PathUnlockPolicy.State.DONE));
        assertTrue(PathUnlockPolicy.canOpen(PathUnlockPolicy.State.IN_PROGRESS));
        assertFalse(PathUnlockPolicy.canOpen(PathUnlockPolicy.State.LOCKED));
    }

    @Test
    public void mockPathHasUniqueIdsAndExactlyOneCurrentLesson() {
        List<PathUnit> path = MockLearningPathRepository.sampleUnits();
        Set<String> ids = new HashSet<>();
        int current = 0;
        for (PathUnlockPolicy.Entry e : PathUnlockPolicy.evaluate(path).entries) {
            assertTrue("trùng id " + e.lesson.id, ids.add(e.lesson.id));
            if (e.state == PathUnlockPolicy.State.IN_PROGRESS) {
                current++;
            }
        }
        assertEquals(1, current);
    }

    @Test
    public void serverStatesAllowParallelTracksAndKeepRecommendation() {
        PathLesson vocabulary = new PathLesson("1", "Vocabulary", "", PathLesson.Type.VOCABULARY,
                10, false, 12, PathUnlockPolicy.State.IN_PROGRESS, false);
        PathLesson grammar = new PathLesson("2", "Grammar", "", PathLesson.Type.GRAMMAR,
                10, false, 24, PathUnlockPolicy.State.IN_PROGRESS, true);
        PathLesson locked = new PathLesson("3", "Later", "", PathLesson.Type.GRAMMAR,
                10, false, 25, PathUnlockPolicy.State.LOCKED, false);
        PathUnlockPolicy.Result result = PathUnlockPolicy.evaluate(units(vocabulary, grammar, locked));
        assertEquals(PathUnlockPolicy.State.IN_PROGRESS, result.entries.get(0).state);
        assertEquals(PathUnlockPolicy.State.IN_PROGRESS, result.entries.get(1).state);
        assertEquals(PathUnlockPolicy.State.LOCKED, result.entries.get(2).state);
        assertEquals("2", result.current.lesson.id);
        assertEquals(12, result.entries.get(0).lesson.contentId);
    }

    @Test
    public void everyLessonTypeHasADestination() {
        assertEquals(R.id.vocabulary_lesson, LessonRouter.destinationFor(PathLesson.Type.VOCABULARY));
        assertEquals(R.id.grammar_lesson, LessonRouter.destinationFor(PathLesson.Type.GRAMMAR));
        assertEquals(R.id.exam_take, LessonRouter.destinationFor(PathLesson.Type.PRACTICE_PART5));
        assertEquals(R.id.reading_article, LessonRouter.destinationFor(PathLesson.Type.READING));
        assertEquals(R.id.exam_take, LessonRouter.destinationFor(PathLesson.Type.MOCK_TEST));
    }
}
