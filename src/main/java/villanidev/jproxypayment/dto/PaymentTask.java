package villanidev.jproxypayment.dto;

import java.time.Instant;

public record PaymentTask(PaymentRequest request, Instant timestamp, int attempts) {
    PaymentTask(PaymentRequest request, Instant timestamp) {
        this(request, timestamp, 0);
    }
}
