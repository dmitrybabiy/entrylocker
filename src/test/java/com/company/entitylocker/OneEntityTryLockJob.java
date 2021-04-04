package com.company.entitylocker;

import com.company.entitylocker.entities.IntValueEntity;
import lombok.SneakyThrows;

import java.util.concurrent.TimeUnit;

public class OneEntityTryLockJob<T, U> implements Runnable {
    private final EntityLocker entityLocker;
    private final IntValueEntity<T> entity1;
    private final long lockTimeMs;
    private final long jobTimeMs;

    public OneEntityTryLockJob(EntityLocker entityLocker, IntValueEntity<T> entity1, long lockTimeMs, long jobTimeMs) {
        this.entityLocker = entityLocker;
        this.entity1 = entity1;
        this.lockTimeMs = lockTimeMs;
        this.jobTimeMs = jobTimeMs;
    }

    @SneakyThrows
    @Override
    public void run() {
        if (entityLocker.tryLock(entity1, lockTimeMs, TimeUnit.MILLISECONDS)) {
            try {
                entity1.inc();
                System.out.println(Thread.currentThread().getName() + " : " + entity1);
                Thread.sleep(jobTimeMs);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                entityLocker.unlock();
            }
        }
    }
}
