package villanidev.jproxypayment.service.processorgateway;

import villanidev.jproxypayment.dto.PaymentEvent;
import villanidev.jproxypayment.exception.PaymentProcessingException;

import java.util.concurrent.CompletableFuture;

public interface PaymentProcessor {
    CompletableFuture<Void> processPayment(PaymentEvent task) throws PaymentProcessingException;
    double getFee();
}
