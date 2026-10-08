package com.bank.app.account.adapter.in.web;

import com.bank.app.account.application.dto.SimulationFundingCapability;
import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.SuspendAccountUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Direct unit coverage for the thin admin/capabilities adapters.
 *
 * <p>PIT reported both endpoints as NO_COVERAGE: the MockMvc tests live in
 * the {@code app} module, outside this module's {@code targetTests}.
 */
@ExtendWith(MockitoExtension.class)
class AccountAdminControllersTest {

    @Mock private SuspendAccountUseCase suspendAccountUseCase;

    @Test
    void capabilitiesShouldReturnWiredBean() {
        var capability = new SimulationFundingCapability(true);
        var controller = new AccountCapabilitiesController(capability);

        assertThat(controller.getCapabilities()).isSameAs(capability);
    }

    @Test
    void suspendShouldReturnStatusBody() {
        when(suspendAccountUseCase.suspend(9L)).thenReturn(new AccountInfo(9L, 100L, "TRY", "SUSPENDED"));
        var controller = new AccountAdminController(suspendAccountUseCase);

        var response = controller.suspendAccount(9L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().status()).isEqualTo("SUSPENDED");
    }
}
