package com.example.disputeportal.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.disputeportal.service.AdminDisputeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Back-office security and workflow. Uses the seeded demo data: dispute 1 is SUBMITTED and dispute 3 is
 * already resolved. Each test is rolled back so the shared in-memory database stays untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSecurityTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AdminDisputeService adminDisputeService;

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/admin/disputes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void customersCannotOpenTheQueue() throws Exception {
        mvc.perform(get("/admin/disputes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void customersCannotDecideDisputes() throws Exception {
        mvc.perform(post("/admin/disputes/1/status").with(csrf()).param("status", "RESOLVED_IN_FAVOUR")
                        .param("note", "Give me my money back"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void adminServiceRejectsNonAdminsEvenWhenCalledDirectly() {
        assertThatThrownBy(() -> adminDisputeService.get(1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "demo-admin", roles = "ADMIN")
    void adminsAreKeptOutOfCustomerPages() throws Exception {
        mvc.perform(get("/transactions")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "demo-admin", roles = "ADMIN")
    void adminSeesTheQueueAndCanFilterIt() throws Exception {
        mvc.perform(get("/admin/disputes"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/disputes"));
        mvc.perform(get("/admin/disputes").param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("statusFilter"));
        mvc.perform(get("/admin/disputes").param("status", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "demo-admin", roles = "ADMIN")
    void adminSeesDisputeDetailOr404() throws Exception {
        mvc.perform(get("/admin/disputes/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dispute"))
                .andExpect(model().attributeExists("events"));
        mvc.perform(get("/admin/disputes/999999")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "demo-admin", roles = "ADMIN")
    void adminCanMoveADisputeToUnderReview() throws Exception {
        mvc.perform(post("/admin/disputes/1/status").with(csrf()).param("status", "UNDER_REVIEW"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/disputes/1"));
    }

    @Test
    @WithMockUser(username = "demo-admin", roles = "ADMIN")
    void rejectingWithoutANoteShowsAnError() throws Exception {
        mvc.perform(post("/admin/disputes/1/status").with(csrf()).param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dispute"))
                .andExpect(model().attributeHasErrors("form"));
    }

    @Test
    @WithMockUser(username = "demo-admin", roles = "ADMIN")
    void closedDisputesCannotBeReopened() throws Exception {
        mvc.perform(post("/admin/disputes/3/status").with(csrf()).param("status", "UNDER_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dispute"))
                .andExpect(model().attributeHasErrors("form"));
    }
}
