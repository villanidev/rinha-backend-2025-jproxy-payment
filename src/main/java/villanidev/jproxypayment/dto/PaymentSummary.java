package villanidev.jproxypayment.dto;

public record PaymentSummary(
        long defaultTotalRequests,
        double defaultTotalAmount,
        long fallbackTotalRequests,
        double fallbackTotalAmount
) {}