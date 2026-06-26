package com.bank.app.common.adapter.in.outbox;

import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.bank.app.common.adapter.in.config.OutboxProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxPort outboxPort;
    private final OutboxProcessor outboxProcessor;
    private final OutboxProperties outboxProperties;

    public OutboxPoller(OutboxPort outboxPort, OutboxProcessor outboxProcessor, OutboxProperties outboxProperties) {
        this.outboxPort = outboxPort;
        this.outboxProcessor = outboxProcessor;
        this.outboxProperties = outboxProperties;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:2000}")
    public void pollAndProcessEvents() {
        int partitionCount = outboxProperties.partitionCount();
        int batchSize = outboxProperties.batchSize();
        int maxRetries = outboxProperties.maxRetries();
        if (partitionCount <= 0) {
            processPartition(-1, batchSize, maxRetries);
        } else {
            for (int p = 0; p < partitionCount; p++) {
                processPartition(p, batchSize, maxRetries);
            }
        }
    }

    private void processPartition(int partition, int batchSize, int maxRetries) {
        List<EventEntry> unprocessedEvents = outboxPort.findAndLockUnprocessed(batchSize, partition);

        if (unprocessedEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} unprocessed outbox events", unprocessedEvents.size());

        for (EventEntry event : unprocessedEvents) {
            try {
                outboxProcessor.processEvent(event);
            } catch (Exception e) {
                outboxProcessor.recordFailure(event, e.getCause() != null ? e.getCause() : e, maxRetries);
            }
        }
    }
}
