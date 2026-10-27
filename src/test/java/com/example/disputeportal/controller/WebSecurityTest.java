package com.example.disputeportal.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Runs against the "dev" profile, which seeds demo customers (thandi, sipho) into in-memory H2. */
@SpringBootTest
@AutoConfigureMockMvc
class WebSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousUsersAreRedirectedToLogin() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void healthProbeIsPublic() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void customerSeesTheirTransactions() throws Exception {
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions/list"))
                .andExpect(model().attributeExists("transactions"))
                .andExpect(content().string(containsString("Disputes logged")))
                .andExpect(content().string(containsString("Disputes closed")));
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void historyPageListsDisputesAndLinksToTheirDetails() throws Exception {
        mvc.perform(get("/disputes"))
                .andExpect(status().isOk())
                .andExpect(view().name("disputes/history"))
                .andExpect(content().string(containsString("View details")))
                .andExpect(content().string(not(containsString("Progress history"))));
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void disputeFormListsEarlierDisputesOnTheTransaction() throws Exception {
        // Thandi's transaction 3 (dispute 1) is open, so use the resolved one: transaction 10 (dispute 3).
        mvc.perform(get("/transactions/10/dispute"))
                .andExpect(status().isOk())
                .andExpect(view().name("disputes/form"))
                .andExpect(content().string(containsString("Earlier disputes on this transaction")));
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void customerSeesOwnDisputeDetailWithHistory() throws Exception {
        mvc.perform(get("/disputes/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("disputes/detail"))
                .andExpect(model().attributeExists("detail"));
    }

    @Test
    @WithMockUser(username = "sipho", roles = "CUSTOMER")
    void customerCannotSeeSomeoneElsesDispute() throws Exception {
        // Dispute 1 belongs to thandi.
        mvc.perform(get("/disputes/1")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void unknownTransactionIsNotFound() throws Exception {
        mvc.perform(get("/transactions/999999/dispute"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/transactions/1/dispute").param("reason", "FRAUD").param("details", "Not my purchase"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void invalidFormIsRedisplayedWithErrors() throws Exception {
        // Transaction 1 is the newest of thandi's seeded transactions, and has no open dispute.
        mvc.perform(post("/transactions/1/dispute").with(csrf()).param("details", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("disputes/form"))
                .andExpect(model().attributeHasFieldErrors("form", "reason", "details"));
    }
}
