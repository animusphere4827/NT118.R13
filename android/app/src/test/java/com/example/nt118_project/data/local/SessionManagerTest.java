package com.example.nt118_project.data.local;

import android.content.SharedPreferences;
import com.example.nt118_project.data.remote.dto.UserDto;
import com.example.nt118_project.support.ImmediateTaskTest;
import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class SessionManagerTest extends ImmediateTaskTest {
    private SessionManager session() { return new SessionManager(new MemoryStore(), new Gson()); }

    @Test public void delayedRefreshCannotRestoreLoggedOutSession() {
        SessionManager session = session();
        session.start("old", "refresh-old", null);
        SessionManager.Snapshot pending = session.snapshot();
        session.clear();
        assertFalse(session.refresh(pending, "late", "refresh-late", null));
        assertNull(session.token());
    }

    @Test public void oldCallbacksCannotOverwriteOrExpireNewAccount() {
        SessionManager session = session();
        session.start("first", "refresh-first", null);
        SessionManager.Snapshot pending = session.snapshot();
        session.start("second", "refresh-second", null);
        session.updateUser(pending, new UserDto());
        session.expire(pending);
        assertFalse(session.refresh(pending, "late", "refresh-late", null));
        assertEquals("second", session.token());
        assertNull(session.currentUser().getValue());
        assertNull(session.sessionExpired().getValue());
    }

    @Test public void refreshRotatesOnceAndPreservesSessionIdentity() {
        SessionManager session = session();
        session.start("old", "refresh-old", null);
        SessionManager.Snapshot pending = session.snapshot();
        assertTrue(session.refresh(pending, "new", "refresh-new", null));
        assertTrue(session.matches(pending));
        assertFalse(session.refresh(pending, "duplicate", "refresh-duplicate", null));
        assertEquals("new", session.token());
    }

    private static final class MemoryStore extends TokenStore {
        private String token, refresh, user;
        MemoryStore() { super((SharedPreferences) null); }
        @Override public String getToken() { return token; }
        @Override public String getRefreshToken() { return refresh; }
        @Override public boolean hasToken() { return token != null; }
        @Override public String getUserJson() { return user; }
        @Override public void saveUserJson(String value) { user = value; }
        @Override public void saveTokens(String value, String next) { token = value; refresh = next; }
        @Override public void clear() { token = null; refresh = null; user = null; }
    }
}
