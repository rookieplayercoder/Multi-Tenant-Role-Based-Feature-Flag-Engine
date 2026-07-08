package com.prateek.featureflag.auth;

import com.prateek.featureflag.auth.dto.LoginRequest;
import com.prateek.featureflag.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/auth/**}, exercising the real Spring
 * context (controller, service, Spring Security filter chain, JPA/Flyway
 * against the configured Postgres instance) via {@link MockMvc}.
 * <p>
 * {@code @Transactional} at the class level wraps every test method in a
 * transaction that Spring's test framework rolls back afterward, so no
 * registered users persist beyond each test — see the note above this file
 * about which database this actually talks to.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Nested
    class Register {

        @Test
        void registeringWithValidPayloadReturnsCreatedWithAccessToken() throws Exception {
            String email = uniqueEmail();
            RegisterRequest request = new RegisterRequest(email, "correct-horse-1", "Jane Doe");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                    .andExpect(jsonPath("$.userId").isNotEmpty())
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.fullName").value("Jane Doe"));
        }

        @Test
        void registeringWithAnAlreadyRegisteredEmailReturnsConflict() throws Exception {
            String email = uniqueEmail();
            RegisterRequest firstRequest = new RegisterRequest(email, "correct-horse-1", "Jane Doe");
            RegisterRequest secondRequest = new RegisterRequest(email, "different-pass-2", "Jane Impostor");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(firstRequest)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(secondRequest)))
                    .andExpect(status().isConflict());
        }

        @Test
        void registeringWithAPasswordShorterThanEightCharactersReturnsBadRequest() throws Exception {
            RegisterRequest request = new RegisterRequest(uniqueEmail(), "short", "Jane Doe");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void registeringWithAMalformedEmailReturnsBadRequest() throws Exception {
            RegisterRequest request = new RegisterRequest("not-an-email", "correct-horse-1", "Jane Doe");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Login {

        @Test
        void loggingInWithValidCredentialsReturnsOkWithAccessToken() throws Exception {
            String email = uniqueEmail();
            RegisterRequest registerRequest = new RegisterRequest(email, "correct-horse-1", "Jane Doe");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated());

            LoginRequest loginRequest = new LoginRequest(email, "correct-horse-1");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.email").value(email));
        }

        @Test
        void loggingInWithAnIncorrectPasswordReturnsUnauthorized() throws Exception {
            String email = uniqueEmail();
            RegisterRequest registerRequest = new RegisterRequest(email, "correct-horse-1", "Jane Doe");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated());

            LoginRequest loginRequest = new LoginRequest(email, "wrong-password");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void loggingInWithAnUnregisteredEmailReturnsUnauthorized() throws Exception {
            LoginRequest loginRequest = new LoginRequest(uniqueEmail(), "whatever-password");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }
    }
}