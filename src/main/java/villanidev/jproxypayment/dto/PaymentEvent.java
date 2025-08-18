package villanidev.jproxypayment.dto;

import java.time.Instant;

public record PaymentEvent(PaymentRequest request, Instant timestamp, int attempts) {
    PaymentEvent(PaymentRequest request, Instant timestamp) {
        this(request, timestamp, 0);
    }
}
