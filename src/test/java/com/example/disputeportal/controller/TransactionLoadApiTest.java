package com.example.disputeportal.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The loading API end to end, using the dev-profile API key and the seeded customers. Each test is rolled
 * back, so loaded transactions don't leak between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionLoadApiTest {

    private static final String URL = "/api/v1/transactions/batch";

    @Autowired
    private MockMvc mvc;

    @Value("${app.api.loader.key}")
    private String apiKey;

    private static String body(String reference, String customer, String amount) {
        return """
                {"transactions":[{"reference":"%s","customerUsername":"%s","merchant":"Checkers","description":"Groceries",
                "amount":%s,"currency":"ZAR","transactionDate":"2020-01-01T08:00:00Z"}]}
                """.formatted(reference, customer, amount);
    }

    private ResultActions send(String key, String json) throws Exception {
        var request = post(URL).contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request.header("Authorization", "Bearer " + key);
        }
        return mvc.perform(request);
    }

    @Test
    void callsWithoutAKeyAreUnauthorised() throws Exception {
        send(null, body("API-0001", "thandi", "10.00"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    void callsWithAWrongKeyAreUnauthorised() throws Exception {
        send("x".repeat(48), body("API-0001", "thandi", "10.00")).andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedWrongKeysLockTheClientOut() throws Exception {
        // A separate client address so this doesn't lock out the other tests.
        for (int i = 0; i < 5; i++) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}")
                            .header("Authorization", "Bearer " + "y".repeat(48))
                            .with(request -> {
                                request.setRemoteAddr("10.9.9.9");
                                return request;
                            }))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("API-0001", "thandi", "10.00"))
                        .header("Authorization", "Bearer " + apiKey)
                        .with(request -> {
                            request.setRemoteAddr("10.9.9.9");
                            return request;
                        }))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void loadsTransactionsAndTreatsAReplayAsDuplicates() throws Exception {
        send(apiKey, body("API-0001", "thandi", "123.45"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(1))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.duplicates").value(0));

        send(apiKey, body("API-0001", "thandi", "123.45"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.duplicates").value(1));
    }

    @Test
    void changedDetailsForAnExistingReferenceAreAConflict() throws Exception {
        send(apiKey, body("API-0002", "thandi", "10.00")).andExpect(status().isOk());

        send(apiKey, body("API-0002", "thandi", "11.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].path").value("transactions[0].reference"));
    }

    @Test
    void invalidFieldsAreReportedWithTheirPath() throws Exception {
        send(apiKey, body("API-0003", "thandi", "-5.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("transactions[0].amount"));
    }

    @Test
    void unknownCustomersAndAdminsAreRejected() throws Exception {
        send(apiKey, body("API-0004", "nobody", "10.00")).andExpect(status().isUnprocessableEntity());
        send(apiKey, body("API-0005", "demo-admin", "10.00")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        send(apiKey, "{ not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value(containsString("not valid JSON")));
    }

    @Test
    void emptyBatchesAreRejected() throws Exception {
        send(apiKey, "{\"transactions\":[]}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void otherContentTypesAreRefused() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("hello")
                        .header("Authorization", "Bearer " + apiKey))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void oversizedBodiesAreRefused() throws Exception {
        send(apiKey, "{\"transactions\":[{\"merchant\":\"" + "x".repeat(1_100_000) + "\"}]}")
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void noCsrfTokenIsNeededBecauseNoCookiesAreInvolved() throws Exception {
        send(apiKey, body("API-0006", "thandi", "10.00")).andExpect(status().isOk());
    }
}
