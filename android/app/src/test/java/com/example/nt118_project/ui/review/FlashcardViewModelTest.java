package com.example.nt118_project.ui.review;

import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.dto.CardDto;
import com.example.nt118_project.data.remote.dto.ReviewResult;
import com.example.nt118_project.data.repository.DeckRepository;
import com.example.nt118_project.support.ImmediateTaskTest;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class FlashcardViewModelTest extends ImmediateTaskTest {
    @Test public void failedReviewKeepsCardAndCountsUntilRetrySucceeds() {
        Repository repo = new Repository();
        FlashcardViewModel model = new FlashcardViewModel(repo);
        model.start(1, "Deck");
        model.grade(5);
        assertTrue(model.isReviewing());
        model.grade(5);
        model.skip();
        assertEquals(1, repo.requests);
        assertEquals(0, model.knownCount());
        assertEquals(1, model.card().id);
        repo.pending.onResult(Resource.error("offline", 0));
        assertFalse(model.isReviewing());
        assertEquals(1, model.card().id);
        model.grade(5);
        repo.pending.onResult(Resource.success(null));
        assertEquals(1, model.knownCount());
        assertTrue(model.isFinished());
    }

    @Test public void failedAgainDoesNotDuplicateQueue() {
        Repository repo = new Repository();
        FlashcardViewModel model = new FlashcardViewModel(repo);
        model.start(1, "Deck");
        model.grade(1);
        repo.pending.onResult(Resource.error("offline", 0));
        assertEquals(1, model.queueSize());
        assertEquals(0, model.againCount());
        model.grade(1);
        repo.pending.onResult(Resource.success(null));
        assertEquals(2, model.queueSize());
        assertEquals(1, model.againCount());
        assertEquals(1, model.card().id);
    }

    @Test public void freePracticeDoesNotRescheduleCard() {
        Repository repo = new Repository();
        repo.free = true;
        FlashcardViewModel model = new FlashcardViewModel(repo);
        model.start(1, "Deck");
        model.grade(5);
        assertEquals(0, repo.requests);
        assertTrue(model.isFinished());
        assertEquals(1, model.knownCount());
    }

    private static final class Repository extends DeckRepository {
        boolean free;
        int requests;
        ApiCalls.Result<ReviewResult> pending;
        Repository() { super(null, null); }
        @Override public void allCards(long id, boolean dueOnly, ApiCalls.Result<List<CardDto>> result) {
            CardDto card = new CardDto();
            card.id = 1;
            result.onResult(Resource.success(dueOnly && free ? List.of() : List.of(card)));
        }
        @Override public void review(long id, int quality, ApiCalls.Result<ReviewResult> result) {
            requests++;
            pending = result;
        }
    }
}
