package com.yavanika.banking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BankingFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    // ----------------------------------------------------------------- helpers

    private String register(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@test.com";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    private String openAccount(String token, String type, String initial) throws Exception {
        String body = mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + type + "\",\"initialDeposit\":" + initial + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accountNumber").asText();
    }

    private String json(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------- tests

    @Test
    void depositWithdrawAndTransferUpdateBalancesAndStatement() throws Exception {
        String alice = register("Alice");
        String bob = register("Bob");
        String aliceAcc = openAccount(alice, "SAVINGS", "5000.00");
        String bobAcc = openAccount(bob, "CURRENT", "0");

        mvc.perform(post("/api/accounts/" + aliceAcc + "/deposit").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1500.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.balanceAfter").value(6500.50));

        mvc.perform(post("/api/accounts/" + aliceAcc + "/withdraw").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":500.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAfter").value(6000.00));

        mvc.perform(post("/api/transfers").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccount\":\"" + aliceAcc + "\",\"toAccount\":\"" + bobAcc
                                + "\",\"amount\":2000,\"description\":\"Rent share\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromBalanceAfter").value(4000.00));

        JsonNode bobAccount = mapper.readTree(json(bob, "/api/accounts/" + bobAcc));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                bobAccount.get("balance").decimalValue().compareTo(new java.math.BigDecimal("2000.00")));

        // Alice's statement: newest first -> TRANSFER_OUT, WITHDRAWAL, DEPOSIT, initial DEPOSIT
        mvc.perform(get("/api/accounts/" + aliceAcc + "/transactions").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.content[0].counterpartyAccount").value(bobAcc))
                .andExpect(jsonPath("$.content[0].description").value("Rent share"));

        // Bob sees the matching TRANSFER_IN with the same reference
        JsonNode aliceTxn = mapper.readTree(json(alice, "/api/accounts/" + aliceAcc + "/transactions"))
                .get("content").get(0);
        mvc.perform(get("/api/accounts/" + bobAcc + "/transactions").header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.content[0].reference").value(aliceTxn.get("reference").asText()));
    }

    @Test
    void savingsMinimumBalanceIsEnforced() throws Exception {
        String token = register("Carol");
        String acc = openAccount(token, "SAVINGS", "1000.00");

        mvc.perform(post("/api/accounts/" + acc + "/withdraw").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":600}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        mvc.perform(post("/api/accounts/" + acc + "/withdraw").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAfter").value(500.00));
    }

    @Test
    void failedTransferLeavesBothBalancesUnchanged() throws Exception {
        String dan = register("Dan");
        String eve = register("Eve");
        String danAcc = openAccount(dan, "CURRENT", "300.00");
        String eveAcc = openAccount(eve, "CURRENT", "100.00");

        mvc.perform(post("/api/transfers").header("Authorization", "Bearer " + dan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccount\":\"" + danAcc + "\",\"toAccount\":\"" + eveAcc + "\",\"amount\":301}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/accounts/" + danAcc).header("Authorization", "Bearer " + dan))
                .andExpect(jsonPath("$.balance").value(300.00));
        mvc.perform(get("/api/accounts/" + eveAcc).header("Authorization", "Bearer " + eve))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void customerCannotTouchSomeoneElsesAccount() throws Exception {
        String owner = register("Frank");
        String intruder = register("Grace");
        String ownerAcc = openAccount(owner, "CURRENT", "1000");
        String intruderAcc = openAccount(intruder, "CURRENT", "0");

        mvc.perform(get("/api/accounts/" + ownerAcc).header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/accounts/" + ownerAcc + "/withdraw").header("Authorization", "Bearer " + intruder)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/transfers").header("Authorization", "Bearer " + intruder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccount\":\"" + ownerAcc + "\",\"toAccount\":\"" + intruderAcc + "\",\"amount\":100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rulesAndValidation() throws Exception {
        String token = register("Heidi");
        String acc = openAccount(token, "CURRENT", "200000");

        mvc.perform(post("/api/accounts/" + acc + "/withdraw").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":150000}"))
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));

        mvc.perform(post("/api/accounts/" + acc + "/deposit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-5}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/accounts/" + acc + "/deposit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10.555}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/transfers").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccount\":\"" + acc + "\",\"toAccount\":\"" + acc + "\",\"amount\":10}"))
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));
    }

    @Test
    void frozenAccountBlocksTransactionsAndClosingNeedsZeroBalance() throws Exception {
        String token = register("Ivan");
        String acc = openAccount(token, "CURRENT", "50");

        mvc.perform(patch("/api/accounts/" + acc + "/status?status=FROZEN").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("FROZEN"));
        mvc.perform(post("/api/accounts/" + acc + "/deposit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10}"))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));

        mvc.perform(patch("/api/accounts/" + acc + "/status?status=CLOSED").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value("NON_ZERO_BALANCE"));
    }

    @Test
    void authIsRequiredAndCredentialsAreChecked() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());

        String email = "judy-" + UUID.randomUUID() + "@test.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Judy\",\"email\":\"" + email + "\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Judy\",\"email\":\"" + email + "\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
