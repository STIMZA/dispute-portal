package com.example.disputeportal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Role;
import com.example.disputeportal.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer-creation API end to end, using the dev-profile keys: the provisioner key (scope customers:create)
 * and the loader key (scope transactions:load). Each test is rolled back so created accounts don't leak.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerProvisionApiTest {

    private static final String URL = "/api/v1/customers/batch";
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private PasswordEncoder encoder;

    @Value("${app.api.provisioner.key}")
    private String provisionerKey;
    @Value("${app.api.loader.key}")
    private String loaderKey;

    private static String body(String username, String fullName, String password) {
        return "{\"customers\":[{\"username\":\"%s\",\"fullName\":\"%s\",\"password\":\"%s\"}]}"
                .formatted(username, fullName, password);
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
        send(null, body("api.user1", "Api User", PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void aKeyThatMayOnlyLoadTransactionsCannotCreateCustomers() throws Exception {
        // Authenticated, but without the customers:create scope: 403, not 401.
        send(loaderKey, body("api.user1", "Api User", PASSWORD)).andExpect(status().isForbidden());
        assertThat(customers.findByUsername("api.user1")).isEmpty();
    }

    @Test
    void theProvisionerKeyCannotLoadTransactions() throws Exception {
        mvc.perform(post("/api/v1/transactions/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactions\":[]}")
                        .header("Authorization", "Bearer " + provisionerKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsAnAccountThatCanLogInAndIsAlwaysACustomer() throws Exception {
        // An attempt to ask for the admin role is simply ignored: the API only ever creates customers.
        send(provisionerKey, "{\"customers\":[{\"username\":\"api.user1\",\"fullName\":\"Api User\","
                + "\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        Customer saved = customers.findByUsername("api.user1").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void replayingTheSameRequestIsADuplicate() throws Exception {
        send(provisionerKey, body("api.user2", "Api User", PASSWORD)).andExpect(status().isOk());

        send(provisionerKey, body("api.user2", "Api User", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.duplicates").value(1));
    }

    @Test
    void anExistingUsernameWithADifferentPasswordIsAConflictAndNeverEchoesPasswords() throws Exception {
        send(provisionerKey, body("api.user3", "Api User", PASSWORD)).andExpect(status().isOk());

        send(provisionerKey, body("api.user3", "Api User", "another long password!"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].path").value("customers[0].username"))
                .andExpect(content().string(not(containsString("another long password!"))))
                .andExpect(content().string(not(containsString(PASSWORD))));
    }

    @Test
    void existingAdminsCannotBeTouched() throws Exception {
        send(provisionerKey, body("demo-admin", "Hijack", PASSWORD)).andExpect(status().isConflict());
    }

    @Test
    void weakOrBadFieldsAreReportedWithoutEchoingThePassword() throws Exception {
        send(provisionerKey, body("api.user4", "Api User", "short"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("customers[0].password"))
                .andExpect(content().string(not(containsString("short\""))));

        send(provisionerKey, body("Api.User5", "Api User", PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("customers[0].username"));
    }

    @Test
    void emptyBatchesAreRejected() throws Exception {
        send(provisionerKey, "{\"customers\":[]}").andExpect(status().isUnprocessableEntity());
    }
}
