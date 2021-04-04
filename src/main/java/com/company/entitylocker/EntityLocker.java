package com.company.entitylocker;

import com.company.entitylocker.entities.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Entity locker provides lock/unlock methods for designating the boundaries of protected code.
 * Optionally allows to prevent possible deadlocks, by eliminating circular wait based on strict inner locking order.
 */
public class EntityLocker {
    private final static Logger log = LoggerFactory.getLogger(EntityLocker.class);

    // entity id -> lock
    private final ConcurrentMap<Object, ReentrantLock> entityLockers = new ConcurrentHashMap<>();
    private final ThreadLocal<Stack<Object>> lockedEntityIdsStack = ThreadLocal.withInitial(Stack::new);

    // deadlock related fields
    private boolean deadlockVerify = false;
    // entity id -> expected inner lock order for preventing deadlock
    private final ConcurrentMap<Object, Integer> entitiesLockOrder = new ConcurrentHashMap<>();
    private final AtomicInteger maxLockOrder = new AtomicInteger(0);

    public EntityLocker() {
    }

    public EntityLocker(boolean deadlockVerify) {
        this.deadlockVerify = deadlockVerify;
    }

    public <T> void lock(Entity<T> entity) {
        T entityId = entity.getId();
        ReentrantLock lock = entityLockers.computeIfAbsent(entityId, k -> new ReentrantLock());

        if (deadlockVerify) {
            validateLockOrderAndPreventDeadlock(entityId);
        }
        if (!lock.isHeldByCurrentThread()) {
            lock.lock();
            lockedEntityIdsStack.get().push(entityId);
        }
    }

    public <T> boolean tryLock(Entity<T> entity, long time, TimeUnit timeUnit) throws InterruptedException {
        T entityId = entity.getId();
        ReentrantLock lock = entityLockers.computeIfAbsent(entityId, k -> new ReentrantLock());
        if (deadlockVerify) {
            validateLockOrderAndPreventDeadlock(entityId);
        }
        if (!lock.isHeldByCurrentThread()) {
            boolean result = lock.tryLock(time, timeUnit);
            if (result) {
                lockedEntityIdsStack.get().push(entityId);
            }
            return result;
        } else {
            return true;
        }
    }

    public void unlock() {
        Object entityId = lockedEntityIdsStack.get().pop();
        ReentrantLock lock = entityLockers.get(entityId);
        if (lock != null) {
            lock.unlock();
        }
    }

    /**
     * Deadlock preventing based on Eliminating circular wait,
     * In case of possible deadlock method throws exception with entitiesLockOrder content, as expected inner lock order,
     * hint for developer
     */
    private <T> void validateLockOrderAndPreventDeadlock(T entityId) {
        if (!lockedEntityIdsStack.get().empty()) {
            Object prevEntityId = lockedEntityIdsStack.get().peek();
            int prevOrderNo = entitiesLockOrder.computeIfAbsent(prevEntityId, k -> maxLockOrder.addAndGet(1));
            int curOrderNo = entitiesLockOrder.computeIfAbsent(entityId, k -> maxLockOrder.addAndGet(1));
            if (curOrderNo < prevOrderNo) {
                List<Object> entitiesSortedByLockOrder = entitiesLockOrder.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
                log.error("Deadlock detected, expected entities lock order: {}", entitiesSortedByLockOrder);
                throw new RuntimeException(String.format("Deadlock detected, expected entities lock order: %s",
                        entitiesSortedByLockOrder));
            }
        }
    }

}
