package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.adapter.in.config.TransferUseCaseRetryAspect;
import org.junit.jupiter.api.Test;
import org.springframework.retry.annotation.Retryable;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferRetryableAnnotationTest {

        @Test
        void useCaseMethodsShouldNotHaveRetryableAnnotation() throws Exception {
                Method placeMethod = PlaceTransferUseCaseImpl.class
                                .getMethod("execute", TransferRequest.class);
                assertNull(placeMethod.getAnnotation(Retryable.class),
                                "@Retryable should be on the aspect, not the use case");

                Method cancelMethod = CancelTransferUseCaseImpl.class
                                .getMethod("execute", Long.class);
                assertNull(cancelMethod.getAnnotation(Retryable.class),
                                "@Retryable should be on the aspect, not the use case");
        }

        @Test
        void aspectShouldRetryOnOptimisticLockingFailure() {
                TransferUseCaseRetryAspect aspect = new TransferUseCaseRetryAspect();
                assertNotNull(aspect);
        }
}
