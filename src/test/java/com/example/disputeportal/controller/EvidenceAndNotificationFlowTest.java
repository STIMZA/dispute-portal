package com.example.disputeportal.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evidence upload/download and notifications end to end, against the seeded demo data: dispute 1 is thandi's open
 * (SUBMITTED) dispute, dispute 3 is already resolved, and demo-admin is the only admin. Each test is rolled back,
 * which also removes any file written to the evidence folder.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EvidenceAndNotificationFlowTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7};

    @Autowired
    private MockMvc mvc;

    private MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG);
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void customerUploadsEvidenceSeesItAndDownloadsItAsAnAttachment() throws Exception {
        mvc.perform(multipart("/disputes/1/evidence").file(png("receipt.png")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/disputes/1"))
                .andExpect(flash().attributeExists("successMessage"));

        mvc.perform(get("/disputes/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("receipt.png")));

        // The first evidence row created in this test's transaction is the newest id; read it via the page link.
        String page = mvc.perform(get("/disputes/1")).andReturn().getResponse().getContentAsString();
        String marker = "/disputes/1/evidence/";
        int start = page.indexOf(marker) + marker.length();
        String evidenceId = page.substring(start, page.indexOf('"', start));

        mvc.perform(get("/disputes/1/evidence/" + evidenceId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().bytes(PNG));
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void uploadWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(multipart("/disputes/1/evidence").file(png("receipt.png")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void wrongFileTypeIsRefusedWithAMessage() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "invoice.pdf", "application/pdf",
                new byte[] {'M', 'Z', 1, 2, 3});

        mvc.perform(multipart("/disputes/1/evidence").file(exe).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(flash().attributeCount(1));
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void missingFileIsRefusedWithAMessage() throws Exception {
        mvc.perform(multipart("/disputes/1/evidence").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void closedDisputeTakesNoEvidenceAndOffersNoForm() throws Exception {
        mvc.perform(multipart("/disputes/3/evidence").file(png("late.png")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));

        mvc.perform(get("/disputes/3"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no more evidence can be added")))
                .andExpect(content().string(not(containsString("multipart/form-data"))));
    }

    @Test
    @WithMockUser(username = "sipho", roles = "CUSTOMER")
    void anotherCustomerCannotUploadToOrDownloadFromTheDispute() throws Exception {
        mvc.perform(multipart("/disputes/1/evidence").file(png("receipt.png")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/disputes/1/evidence/1")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "demo-admin", roles = "ADMIN")
    void adminsCannotUseTheCustomerUploadPage() throws Exception {
        mvc.perform(multipart("/disputes/1/evidence").file(png("receipt.png")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "demo-admin", roles = "ADMIN")
    void adminSeesTheEvidenceListOnTheReviewPage() throws Exception {
        mvc.perform(get("/admin/disputes/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Evidence from the customer")));
        mvc.perform(get("/admin/disputes/1/evidence/999999")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "demo-admin", roles = "ADMIN")
    void adminsAreToldWhenTheDisputeHasSeededNotifications() throws Exception {
        mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("is waiting for review")))
                .andExpect(content().string(containsString("Review dispute")));
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void customerSeesDecisionNotificationsAndCanMarkThemRead() throws Exception {
        mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your dispute on")))
                .andExpect(content().string(containsString("Mark all as read")))
                .andExpect(content().string(containsString("View dispute")));

        mvc.perform(post("/notifications/read-all").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"))
                .andExpect(flash().attributeExists("successMessage"));

        mvc.perform(get("/notifications"))
                .andExpect(content().string(not(containsString("Mark as read"))))
                .andExpect(content().string(not(containsString("unread"))));
    }

    @Test
    @WithMockUser(username = "thandi", roles = "CUSTOMER")
    void markingSomeoneElsesNotificationIsNotFound() throws Exception {
        mvc.perform(post("/notifications/999999/read").with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void notificationsRequireSignIn() throws Exception {
        mvc.perform(get("/notifications")).andExpect(status().is3xxRedirection());
    }
}
