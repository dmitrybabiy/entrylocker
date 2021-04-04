package com.company.entitylocker;


import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.company.entitylocker.entities.IntValueEntity;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class EntityLockerTest {

    @Mock
    private Appender<ILoggingEvent> mockLogAppender;

    @BeforeEach
    void init() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(mockLogAppender);
    }

    @Test
    @SneakyThrows
    void two_threads_for_two_int_entities() {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        EntityLocker entityLocker = new EntityLocker(true);

        IntValueEntity<Integer> entity1 = new IntValueEntity<>(1, 0);
        IntValueEntity<Integer> entity2 = new IntValueEntity<>(2, 0);

        executorService.submit(new TwoEntityIncrementJob<>(entityLocker, entity1, entity2, 10));
        executorService.submit(new TwoEntityIncrementJob<>(entityLocker, entity2, entity1, 10));

        executorService.shutdown();

        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(20, entity1.getValue(), "10 * 2");
        assertEquals(20, entity2.getValue(), "10 * 2");
    }

    @Test
    @SneakyThrows
    void two_threads_for_int_and_string_entities() {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        EntityLocker entityLocker = new EntityLocker(true);

        IntValueEntity<Integer> entity1 = new IntValueEntity<>(1, 0);
        IntValueEntity<String> entity2 = new IntValueEntity<>("abc", 0);

        executorService.submit(new TwoEntityIncrementJob<>(entityLocker, entity1, entity2, 10));
        executorService.submit(new TwoEntityIncrementJob<>(entityLocker, entity2, entity1, 10));

        executorService.shutdown();

        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(20, entity1.getValue(), "10 * 2");
        assertEquals(20, entity2.getValue(), "10 * 2");
    }

    @Test
    void entitylocker_provides_reentrant_lock() {
        EntityLocker entityLocker = new EntityLocker(true);
        IntValueEntity<Integer> entity1 = new IntValueEntity<>(1, 0);

        entityLocker.lock(entity1);
        entityLocker.lock(entity1);
        entity1.inc();
        entityLocker.unlock();

        entityLocker.lock(entity1);
        entityLocker.lock(entity1);
        entity1.inc();
        entityLocker.unlock();

        assertEquals(2, entity1.getValue(), "Two incs");
    }

    @ParameterizedTest
    @CsvSource({
            "100, 200, 1",
            "200, 100, 2"
    })
    @SneakyThrows
    void entitylocker_with_lock_timeout(long lockTimeMs, long jobTimeMs, int expectedValue) {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        EntityLocker entityLocker = new EntityLocker(true);
        IntValueEntity<Integer> entity1 = new IntValueEntity<>(1, 0);

        executorService.submit(new OneEntityTryLockJob<>(entityLocker, entity1, lockTimeMs, jobTimeMs));
        executorService.submit(new OneEntityTryLockJob<>(entityLocker, entity1, lockTimeMs, jobTimeMs));

        executorService.shutdown();

        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(expectedValue, entity1.getValue());
    }

    @Test
    @SneakyThrows
    void deadlock_prevented() {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        EntityLocker entityLocker = new EntityLocker(true);

        IntValueEntity<Integer> entity1 = new IntValueEntity<>(1, 0);
        IntValueEntity<Integer> entity2 = new IntValueEntity<>(2, 0);

        executorService.submit(new TwoEntityInnerLockJob<>(entityLocker, entity1, entity2));
        executorService.submit(new TwoEntityInnerLockJob<>(entityLocker, entity2, entity1));

        executorService.shutdown();

        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));

        ArgumentCaptor<LoggingEvent> logCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(mockLogAppender).doAppend(logCaptor.capture());

        assertEquals("Deadlock detected, expected entities lock order: {}",
                logCaptor.getValue().getMessage());
    }

    @Test
    @SneakyThrows
    void tree_threads_no_false_deadlock() {
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        EntityLocker entityLocker = new EntityLocker(true);

        IntValueEntity<Integer> entity1 = new IntValueEntity<>(1, 0);
        IntValueEntity<Integer> entity2 = new IntValueEntity<>(2, 0);

        executorService.submit(new OneEntityIncrementJob<>(entityLocker, entity1, 1));
        Thread.sleep(50);
        executorService.submit(new OneEntityIncrementJob<>(entityLocker, entity2, 1));
        Thread.sleep(50);
        executorService.submit(new TwoEntityInnerLockJob<>(entityLocker, entity2, entity1));

        executorService.shutdown();

        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(2, entity1.getValue(), "Two incs");
        assertEquals(2, entity2.getValue(), "Two incs");
    }

    @Test
    @SneakyThrows
    void false_deadlock_example() {
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        EntityLocker entityLocker = new EntityLocker(true);

        IntValueEntity<Integer> entity1 = new IntValueEntity<>(1, 0);
        IntValueEntity<Integer> entity2 = new IntValueEntity<>(2, 0);
        IntValueEntity<Integer> entity3 = new IntValueEntity<>(3, 0);
        IntValueEntity<Integer> entity4 = new IntValueEntity<>(4, 0);

        executorService.submit(new TwoEntityInnerLockJob<>(entityLocker, entity1, entity2));
        Thread.sleep(50);
        executorService.submit(new TwoEntityInnerLockJob<>(entityLocker, entity3, entity4));
        Thread.sleep(50);
        executorService.submit(new TwoEntityInnerLockJob<>(entityLocker, entity3, entity1));

        executorService.shutdown();

        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));

        ArgumentCaptor<LoggingEvent> logCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(mockLogAppender).doAppend(logCaptor.capture());

        assertEquals("Deadlock detected, expected entities lock order: [1, 2, 3, 4]",
                logCaptor.getValue().getFormattedMessage());
    }

}
