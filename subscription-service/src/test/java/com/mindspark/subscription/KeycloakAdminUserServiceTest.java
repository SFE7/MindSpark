package com.mindspark.subscription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.mindspark.subscription.dto.CreateAdminRequest;
import com.mindspark.subscription.exception.AdminAlreadyExistsException;
import com.mindspark.subscription.exception.KeycloakAdminNotConfiguredException;
import com.mindspark.subscription.exception.KeycloakRoleNotFoundException;
import com.mindspark.subscription.service.KeycloakAdminUserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakAdminUserServiceTest {

    @Test
    @DisplayName("Story 65: When Keycloak admin credentials are NOT configured, throws KeycloakAdminNotConfiguredException")
    void unconfigured_throwsException() {
        KeycloakAdminUserService service = new KeycloakAdminUserService(
                RestClient.builder(),
                "http://localhost:8180",
                "mindspark",
                "",
                ""
        );

        assertThat(service.isConfigured()).isFalse();
        assertThatThrownBy(() -> service.createAdminUser(new CreateAdminRequest("a@b.com", "A", "B", "pass1234")))
                .isInstanceOf(KeycloakAdminNotConfiguredException.class);

        var listRes = service.getAdminUsers();
        assertThat(listRes.configured()).isFalse();
        assertThat(listRes.admins()).isEmpty();
    }

    @Test
    @DisplayName("Story 65: Successful admin provisioning creates user, fetches role, assigns role")
    void createAdminUser_success() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        KeycloakAdminUserService service = new KeycloakAdminUserService(
                builder,
                "http://localhost:8180",
                "mindspark",
                "mindspark-admin-cli",
                "secret123"
        );

        // 1. Token request
        server.expect(requestTo("http://localhost:8180/realms/mindspark/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\": \"mock-token\"}", MediaType.APPLICATION_JSON));

        // 2. User create request
        HttpHeaders locationHeader = new HttpHeaders();
        locationHeader.set("Location", "http://localhost:8180/admin/realms/mindspark/users/user-uuid-999");
        server.expect(requestTo("http://localhost:8180/admin/realms/mindspark/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).headers(locationHeader));

        // 3. Fetch 'admin' role
        server.expect(requestTo("http://localhost:8180/admin/realms/mindspark/roles/admin"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\": \"role-uuid-1\", \"name\": \"admin\"}", MediaType.APPLICATION_JSON));

        // 4. Assign role
        server.expect(requestTo("http://localhost:8180/admin/realms/mindspark/users/user-uuid-999/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        var res = service.createAdminUser(new CreateAdminRequest("newadmin@mindspark.com", "New", "Admin", "tempPass123!"));
        server.verify();

        assertThat(res.userId()).isEqualTo("user-uuid-999");
        assertThat(res.email()).isEqualTo("newadmin@mindspark.com");
        assertThat(res.role()).isEqualTo("admin");
        assertThat(res.temporaryPasswordRequired()).isTrue();
    }

    @Test
    @DisplayName("Story 65: When user email already exists, Keycloak 409 maps to AdminAlreadyExistsException")
    void createAdminUser_duplicateEmail_throwsConflict() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        KeycloakAdminUserService service = new KeycloakAdminUserService(
                builder,
                "http://localhost:8180",
                "mindspark",
                "mindspark-admin-cli",
                "secret123"
        );

        server.expect(requestTo("http://localhost:8180/realms/mindspark/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\": \"mock-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://localhost:8180/admin/realms/mindspark/users"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.createAdminUser(new CreateAdminRequest("existing@mindspark.com", "Jane", "Doe", "pass1234")))
                .isInstanceOf(AdminAlreadyExistsException.class);

        server.verify();
    }

    @Test
    @DisplayName("Story 65: When role 'admin' is missing, compensates with DELETE /users/{id} and throws KeycloakRoleNotFoundException")
    void createAdminUser_missingRole_rollsBack() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        KeycloakAdminUserService service = new KeycloakAdminUserService(
                builder,
                "http://localhost:8180",
                "mindspark",
                "mindspark-admin-cli",
                "secret123"
        );

        server.expect(requestTo("http://localhost:8180/realms/mindspark/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\": \"mock-token\"}", MediaType.APPLICATION_JSON));

        HttpHeaders locationHeader = new HttpHeaders();
        locationHeader.set("Location", "http://localhost:8180/admin/realms/mindspark/users/user-uuid-111");
        server.expect(requestTo("http://localhost:8180/admin/realms/mindspark/users"))
                .andRespond(withStatus(HttpStatus.CREATED).headers(locationHeader));

        // Missing role returns 404
        server.expect(requestTo("http://localhost:8180/admin/realms/mindspark/roles/admin"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // Compensating DELETE
        server.expect(requestTo("http://localhost:8180/admin/realms/mindspark/users/user-uuid-111"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatThrownBy(() -> service.createAdminUser(new CreateAdminRequest("missingrole@mindspark.com", "Sam", "Smith", "pass1234")))
                .isInstanceOf(KeycloakRoleNotFoundException.class);

        server.verify();
    }
}
