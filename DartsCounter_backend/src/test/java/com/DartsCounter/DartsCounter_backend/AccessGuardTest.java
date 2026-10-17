package com.DartsCounter.DartsCounter_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

// /api/data und /services: im Heimnetz frei, von außen nur mit Anmeldung
@SpringBootTest
class AccessGuardTest {

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
    static String adminToken;

    private static final String TUNNEL = AccessGuard.TUNNEL_HEADER;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        if (adminToken == null) {
            JsonMapper json = JsonMapper.builder().build();
            JsonNode admin = json.readTree(mvc.perform(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Hannes\"}")).andReturn().getResponse().getContentAsString());
            String body = json.writeValueAsString(java.util.Map.of("name", "Hannes", "code", admin.get("code").asString()));
            adminToken = json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getContentAsString()).get("token").asString();
        }
    }

    @Test
    void homeNetworkWorksWithoutLogin() throws Exception {
        mvc.perform(put("/api/data/players").contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isNoContent());
        mvc.perform(get("/api/data/players")).andExpect(status().isOk());
        mvc.perform(delete("/api/data/players")).andExpect(status().isNoContent());
    }

    @Test
    void tunnelNeedsLogin() throws Exception {
        mvc.perform(get("/api/data/players").header(TUNNEL, "203.0.113.5"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bitte anmelden."));
        mvc.perform(put("/api/data/games").header(TUNNEL, "203.0.113.5").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/data/games").header(TUNNEL, "203.0.113.5")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/data/players").header(TUNNEL, "203.0.113.5").header("Authorization", "Bearer falsch"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tunnelWorksWithLogin() throws Exception {
        String bearer = "Bearer " + adminToken;
        mvc.perform(put("/api/data/settings").header(TUNNEL, "203.0.113.5").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"botLevel\":\"hard\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/data/settings").header(TUNNEL, "203.0.113.5").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.botLevel").value("hard"));
    }

    @Test
    void tunnelStillAllowsLoginAndCorsPreflight() throws Exception {
        mvc.perform(get("/api/auth/setup").header(TUNNEL, "203.0.113.5")).andExpect(status().isOk());
        mvc.perform(options("/api/data/players").header(TUNNEL, "203.0.113.5")
                        .header("Origin", "https://darts.example.org")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
        // auch die 401 trägt den CORS-Header, sonst kann das Frontend die Meldung nicht lesen
        mvc.perform(get("/api/data/players").header(TUNNEL, "203.0.113.5").header("Origin", "https://darts.example.org"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void foreignAddressNeedsLoginWithoutTunnelHeader() throws Exception {
        // z. B. Portweiterleitung im Router: kein Cloudflare-Header, aber öffentliche Adresse
        mvc.perform(get("/api/data/players").with(r -> { r.setRemoteAddr("203.0.113.5"); return r; }))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/data/players").with(r -> { r.setRemoteAddr("192.168.178.20"); return r; }))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void servicesFromOutsideOnlyForAdmins() throws Exception {
        mvc.perform(get("/services/calibration").header(TUNNEL, "203.0.113.5")).andExpect(status().isUnauthorized());
        mvc.perform(get("/services/calibration").header(TUNNEL, "203.0.113.5").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/services/calibration")).andExpect(status().isOk()); // Heimnetz
    }

    @Test
    void servicesForbiddenForPlayersFromOutside() throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        String created = mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Kai\",\"role\":\"player\"}"))
                .andReturn().getResponse().getContentAsString();
        String code = json.readTree(created).get("code").asString();
        String body = json.writeValueAsString(java.util.Map.of("name", "Kai", "code", code));
        String token = json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString()).get("token").asString();
        mvc.perform(get("/services/calibration").header(TUNNEL, "203.0.113.5").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Nur für Admins."));
    }

    @Test
    void homeNetworkAddresses() {
        org.junit.jupiter.api.Assertions.assertTrue(AccessGuard.isHomeNetwork("127.0.0.1"));
        org.junit.jupiter.api.Assertions.assertTrue(AccessGuard.isHomeNetwork("10.0.0.7"));
        org.junit.jupiter.api.Assertions.assertTrue(AccessGuard.isHomeNetwork("172.20.1.1"));
        org.junit.jupiter.api.Assertions.assertTrue(AccessGuard.isHomeNetwork("::1"));
        org.junit.jupiter.api.Assertions.assertTrue(AccessGuard.isHomeNetwork("fd12:3456::1"));
        org.junit.jupiter.api.Assertions.assertTrue(AccessGuard.isHomeNetwork("fe80::1"));
        org.junit.jupiter.api.Assertions.assertFalse(AccessGuard.isHomeNetwork("8.8.8.8"));
        org.junit.jupiter.api.Assertions.assertFalse(AccessGuard.isHomeNetwork("172.32.0.1"));
        org.junit.jupiter.api.Assertions.assertFalse(AccessGuard.isHomeNetwork("2001:db8::1"));
    }
}
