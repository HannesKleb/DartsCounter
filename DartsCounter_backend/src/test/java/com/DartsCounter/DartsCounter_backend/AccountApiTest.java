package com.DartsCounter.DartsCounter_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Endpunkte über HTTP: Rechte, Fehlercodes, keine Hashes in Antworten
@SpringBootTest
class AccountApiTest {

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("dartscounter.data-dir", () -> dataDir.toString());
        registry.add("dartscounter.auth.bcrypt-strength", () -> "4");
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;
    final JsonMapper json = JsonMapper.builder().build();

    // Admin wird einmal für alle Tests angelegt (gleicher Spring-Kontext)
    static String adminToken;
    static String adminId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        if (adminToken == null) {
            JsonNode created = call(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Hannes\"}"), 201);
            adminId = created.get("account").get("id").asString();
            adminToken = login("Hannes", created.get("code").asString());
        }
    }

    private JsonNode call(org.springframework.test.web.servlet.RequestBuilder request, int expectedStatus) throws Exception {
        String body = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    private String login(String name, String code) throws Exception {
        return call(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("name", name, "code", code))), 200).get("token").asString();
    }

    private JsonNode createAccount(String name, String role) throws Exception {
        return call(post("/api/accounts").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("name", name, "role", role))), 201);
    }

    @Test
    void setupOnlyOnce() throws Exception {
        mvc.perform(get("/api/auth/setup")).andExpect(jsonPath("$.needed").value(false));
        mvc.perform(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Boss\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Es gibt schon Konten."));
    }

    @Test
    void accountsNeverContainHashes() throws Exception {
        JsonNode created = createAccount("Paul", "player");
        assertEquals(4, created.get("account").size()); // id, name, role, createdAt
        String list = mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(list.contains("codeHash") || list.contains("salt") || list.contains("$2a$"));
    }

    @Test
    void meAndLogout() throws Exception {
        JsonNode created = createAccount("Mia", "player");
        String token = login("Mia", created.get("code").asString());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mia"))
                .andExpect(jsonPath("$.role").value("player"));
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void playerGets403() throws Exception {
        JsonNode created = createAccount("Tom", "player");
        String playerToken = login("Tom", created.get("code").asString());
        String id = created.get("account").get("id").asString();
        String bearer = "Bearer " + playerToken;

        mvc.perform(get("/api/accounts").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/accounts").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"role\":\"admin\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/accounts/" + id + "/code").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/accounts/" + id).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"admin\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/accounts/" + adminId).header("Authorization", bearer)).andExpect(status().isForbidden());
        // ohne Token
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRulesGive409() throws Exception {
        String bearer = "Bearer " + adminToken;
        mvc.perform(delete("/api/accounts/" + adminId).header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
        mvc.perform(patch("/api/accounts/" + adminId).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"player\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Es muss mindestens ein Admin bleiben."));
    }

    @Test
    void resetCodeEndsSessions() throws Exception {
        JsonNode created = createAccount("Ben", "player");
        String id = created.get("account").get("id").asString();
        String token = login("Ben", created.get("code").asString());

        JsonNode reset = call(post("/api/accounts/" + id + "/code").header("Authorization", "Bearer " + adminToken), 200);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("name", "Ben", "code", created.get("code").asString()))))
                .andExpect(status().isUnauthorized());
        login("Ben", reset.get("code").asString());
    }

    @Test
    void rateLimitGives429() throws Exception {
        createAccount("Lea", "player");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Lea\",\"code\":\"AAAA-AAAA-AAAA\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(AccountService.LOGIN_FAILED));
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lea\",\"code\":\"AAAA-AAAA-AAAA\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void corsAllowsAuthorizationHeader() throws Exception {
        mvc.perform(options("/api/accounts")
                        .header("Origin", "http://192.168.0.10")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }
}
