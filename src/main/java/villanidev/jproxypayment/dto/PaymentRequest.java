package villanidev.jproxypayment.dto;

import java.util.UUID;

public record PaymentRequest(UUID correlationId, double amount) {}