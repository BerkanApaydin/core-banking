package com.bank.app.account.application.dto;

/** Whether this deployment permits users to create accounts with simulated starting funds. */
public record SimulationFundingCapability(boolean initialFundingEnabled) {
}
