package com.bank.app;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationSmokeIntegrationTest extends AbstractSpringBootIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        assertThat(restTemplate).isNotNull();
    }

    @Test
    void shouldRegisterUser() {
        AuthRequest request = new AuthRequest("smoke_test_user", "Test1234");

        ResponseEntity<Void> registerResponse = restTemplate.postForEntity(
                "/api/v1/auth/register", request, Void.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void shouldCompleteRegistrationAndCreateAccountFlow() {
        String username = "flow_test_" + System.currentTimeMillis();
        String password = "Flow1234";

        ResponseEntity<Void> registerResponse = restTemplate.postForEntity(
                "/api/v1/auth/register",
                new AuthRequest(username, password),
                Void.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new AuthRequest(username, password),
                AuthResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        assertThat(loginResponse.getBody().token()).isNotBlank();

        String token = loginResponse.getBody().token();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<AccountResponse> accountResponse = restTemplate.exchange(
                "/api/v1/accounts",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "userId", loginResponse.getBody().userId(),
                        "iban", "TR330006200000000000000001",
                        "ownerName", "Flow Test",
                        "initialBalance", new BigDecimal("1000.00"),
                        "currency", "TRY"
                ), headers),
                AccountResponse.class);
        assertThat(accountResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(accountResponse.getBody()).isNotNull();
        assertThat(accountResponse.getBody().iban()).isEqualTo("TR330006200000000000000001");
        assertThat(accountResponse.getBody().balance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void shouldCompleteRegistrationAndCreateAccountAndTransferFlow() {
        String userA = "transfer_sender_" + System.currentTimeMillis();
        String userB = "transfer_receiver_" + System.currentTimeMillis();

        ResponseEntity<Void> regA = restTemplate.postForEntity("/api/v1/auth/register",
                new AuthRequest(userA, "Sender1234"), Void.class);
        assertThat(regA.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> regB = restTemplate.postForEntity("/api/v1/auth/register",
                new AuthRequest(userB, "Receiver1234"), Void.class);
        assertThat(regB.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<AuthResponse> loginA = restTemplate.postForEntity("/api/v1/auth/login",
                new AuthRequest(userA, "Sender1234"), AuthResponse.class);
        assertThat(loginA.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AuthResponse> loginB = restTemplate.postForEntity("/api/v1/auth/login",
                new AuthRequest(userB, "Receiver1234"), AuthResponse.class);
        assertThat(loginB.getStatusCode()).isEqualTo(HttpStatus.OK);

        Long userIdA = loginA.getBody().userId();
        Long userIdB = loginB.getBody().userId();

        HttpHeaders headersA = new HttpHeaders();
        headersA.setBearerAuth(loginA.getBody().token());

        HttpHeaders headersB = new HttpHeaders();
        headersB.setBearerAuth(loginB.getBody().token());

        ResponseEntity<AccountResponse> accountA = restTemplate.exchange("/api/v1/accounts",
                HttpMethod.POST, new HttpEntity<>(Map.of(
                        "userId", userIdA,
                        "iban", "TR330006200000000000000003",
                        "ownerName", "Sender",
                        "initialBalance", new BigDecimal("5000.00"),
                        "currency", "TRY"
                ), headersA), AccountResponse.class);
        assertThat(accountA.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<AccountResponse> accountB = restTemplate.exchange("/api/v1/accounts",
                HttpMethod.POST, new HttpEntity<>(Map.of(
                        "userId", userIdB,
                        "iban", "TR330006200000000000000004",
                        "ownerName", "Receiver",
                        "initialBalance", new BigDecimal("0"),
                        "currency", "TRY"
                ), headersB), AccountResponse.class);
        assertThat(accountB.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String senderIban = accountA.getBody().iban();
        String receiverIban = accountB.getBody().iban();

        ResponseEntity<TransferResponse> transferResponse = restTemplate.exchange("/api/v1/transfers",
                HttpMethod.POST, new HttpEntity<>(Map.of(
                        "senderIban", senderIban,
                        "receiverIban", receiverIban,
                        "amount", new BigDecimal("500.00"),
                        "currency", "TRY"
                ), headersA), TransferResponse.class);
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(transferResponse.getBody()).isNotNull();
        assertThat(transferResponse.getBody().id()).isPositive();
        assertThat(transferResponse.getBody().status()).isEqualTo("COMPLETED");
        assertThat(transferResponse.getBody().amount()).isEqualByComparingTo("500.00");
        assertThat(transferResponse.getBody().senderIban()).isEqualTo(senderIban);
        assertThat(transferResponse.getBody().receiverIban()).isEqualTo(receiverIban);
    }

    @Test
    void shouldReturnUnauthorizedWhenLoginWithWrongPassword() {
        restTemplate.postForEntity("/api/v1/auth/register",
                new AuthRequest("wrong_pw_user", "Valid1234"), Void.class);

        ResponseEntity<ProblemDetail> response = restTemplate.postForEntity("/api/v1/auth/login",
                new AuthRequest("wrong_pw_user", "WrongPassword"),
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturnBadRequestWhenRegisteringDuplicateUsername() {
        restTemplate.postForEntity("/api/v1/auth/register",
                new AuthRequest("dup_user", "Test1234"), Void.class);

        ResponseEntity<ProblemDetail> response = restTemplate.postForEntity("/api/v1/auth/register",
                new AuthRequest("dup_user", "Test1234"),
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturnBadRequestWhenInsufficientBalanceForTransfer() {
        String sender = "poor_sender_" + System.currentTimeMillis();
        String receiver = "rich_receiver_" + System.currentTimeMillis();

        restTemplate.postForEntity("/api/v1/auth/register", new AuthRequest(sender, "Test1234"), Void.class);
        restTemplate.postForEntity("/api/v1/auth/register", new AuthRequest(receiver, "Test1234"), Void.class);

        ResponseEntity<AuthResponse> loginSender = restTemplate.postForEntity("/api/v1/auth/login",
                new AuthRequest(sender, "Test1234"), AuthResponse.class);
        ResponseEntity<AuthResponse> loginReceiver = restTemplate.postForEntity("/api/v1/auth/login",
                new AuthRequest(receiver, "Test1234"), AuthResponse.class);

        HttpHeaders headersSender = new HttpHeaders();
        headersSender.setBearerAuth(loginSender.getBody().token());
        HttpHeaders headersReceiver = new HttpHeaders();
        headersReceiver.setBearerAuth(loginReceiver.getBody().token());

        restTemplate.exchange("/api/v1/accounts", HttpMethod.POST, new HttpEntity<>(Map.of(
                "userId", loginSender.getBody().userId(),
                "iban", "TR330006200000000000000005",
                "ownerName", "Poor",
                "initialBalance", new BigDecimal("100.00"),
                "currency", "TRY"
        ), headersSender), AccountResponse.class);

        restTemplate.exchange("/api/v1/accounts", HttpMethod.POST, new HttpEntity<>(Map.of(
                "userId", loginReceiver.getBody().userId(),
                "iban", "TR330006200000000000000006",
                "ownerName", "Rich",
                "initialBalance", new BigDecimal("99999.00"),
                "currency", "TRY"
        ), headersReceiver), AccountResponse.class);

        ResponseEntity<ProblemDetail> response = restTemplate.exchange("/api/v1/transfers",
                HttpMethod.POST, new HttpEntity<>(Map.of(
                        "senderIban", "TR330006200000000000000005",
                        "receiverIban", "TR330006200000000000000006",
                        "amount", new BigDecimal("99999.00"),
                        "currency", "TRY"
                ), headersSender), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturnNotFoundWhenTransferToNonExistentIban() {
        String user = "noiban_" + System.currentTimeMillis();

        restTemplate.postForEntity("/api/v1/auth/register",
                new AuthRequest(user, "Test1234"), Void.class);

        ResponseEntity<AuthResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                new AuthRequest(user, "Test1234"), AuthResponse.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().token());

        restTemplate.exchange("/api/v1/accounts", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "userId", login.getBody().userId(),
                        "iban", "TR330006200000000000000007",
                        "ownerName", "HasAccount",
                        "initialBalance", new BigDecimal("5000.00"),
                        "currency", "TRY"
                ), headers), AccountResponse.class);

        ResponseEntity<ProblemDetail> response = restTemplate.exchange("/api/v1/transfers",
                HttpMethod.POST, new HttpEntity<>(Map.of(
                        "senderIban", "TR330006200000000000000007",
                        "receiverIban", "TR990006200000000000000999",
                        "amount", new BigDecimal("100.00"),
                        "currency", "TRY"
                ), headers), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
