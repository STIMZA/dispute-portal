package com.example.disputeportal.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Real form logins against the seeded demo accounts. */
@SpringBootTest
@AutoConfigureMockMvc
class LoginFlowTest {

    @Autowired
    private MockMvc mvc;

    @Value("${app.demo.password}")
    private String demoPassword;

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("username", username).param("password", password));
    }

    @Test
    void customerLandsOnTransactions() throws Exception {
        login("thandi", demoPassword).andExpect(redirectedUrl("/transactions"));
    }

    @Test
    void adminLandsOnTheDisputeQueue() throws Exception {
        login("demo-admin", demoPassword).andExpect(redirectedUrl("/admin/disputes"));
    }

    @Test
    void wrongPasswordShowsAGenericError() throws Exception {
        login("sipho", "definitely-wrong").andExpect(redirectedUrl("/login?error"));
        login("no-such-user", "definitely-wrong").andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void repeatedFailuresLockTheClientOut() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("lockout-test-user", "wrong").andExpect(redirectedUrl("/login?error"));
        }
        login("lockout-test-user", "wrong").andExpect(redirectedUrl("/login?locked"));
    }
}
