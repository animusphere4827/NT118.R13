package com.example.nt118_project.data.remote.dto;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.example.nt118_project.common.Resource;
import org.junit.Test;

import static org.junit.Assert.*;

public final class GoogleLoginRequestTest {
    private final Gson gson = new Gson();

    @Test public void sendsPasswordOnlyForExplicitLinking() {
        JsonObject initial = gson.toJsonTree(new GoogleLoginRequest("firebase-token")).getAsJsonObject();
        assertFalse(initial.has("existing_password"));
        String password = " Password with spaces ";
        JsonObject link = gson.toJsonTree(new GoogleLoginRequest("firebase-token", password)).getAsJsonObject();
        assertEquals("firebase-token", link.get("id_token").getAsString());
        assertEquals(password, link.get("existing_password").getAsString());
    }

    @Test public void retainsMachineReadableLinkErrors() {
        ErrorResponse error = gson.fromJson(
                "{\"error\":\"Wrong password\",\"error_code\":\"GOOGLE_LINK_PASSWORD_INVALID\"}",
                ErrorResponse.class);
        Resource<Object> result = Resource.error(error.error, 401, error.errorCode);
        assertEquals("GOOGLE_LINK_PASSWORD_INVALID", result.errorCode);
        assertEquals(401, result.code);
    }
}
