package villanidev.jproxypayment.service;

import villanidev.jproxypayment.dto.PaymentRequest;
import villanidev.jproxypayment.dto.PaymentTask;

import java.util.concurrent.CompletableFuture;

public interface PaymentProcessor {
    CompletableFuture<Void> processPayment(PaymentTask task) throws PaymentProcessingException;
    double getFee();
}
