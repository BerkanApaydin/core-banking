package com.bank.app.account.adapter.in.web;

import com.bank.app.account.application.dto.SimulationFundingCapability;
import com.bank.app.common.adapter.in.api.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("/accounts/capabilities")
public class AccountCapabilitiesController {

    private final SimulationFundingCapability fundingCapability;

    public AccountCapabilitiesController(SimulationFundingCapability fundingCapability) {
        this.fundingCapability = fundingCapability;
    }

    @GetMapping
    @Operation(summary = "Gets simulated account funding capabilities for the active deployment")
    public SimulationFundingCapability getCapabilities() {
        return fundingCapability;
    }
}
