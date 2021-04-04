package com.company.entitylocker;

import com.company.entitylocker.entities.IntValueEntity;

import java.util.concurrent.ThreadLocalRandom;

public class OneEntityIncrementJob<T> implements Runnable {
    EntityLocker entityLocker;
    IntValueEntity<T> entity1;
    int nIterations;

    public OneEntityIncrementJob(EntityLocker entityLocker, IntValueEntity<T> entity1, int nIterations) {
        this.entityLocker = entityLocker;
        this.entity1 = entity1;
        this.nIterations = nIterations;
    }

    @Override
    public void run() {
        while (nIterations-- > 0) {
            entityLocker.lock(entity1);
            try {
                entity1.inc();
                System.out.println(Thread.currentThread().getName() + " : " + entity1);
                Thread.sleep(ThreadLocalRandom.current().nextInt(100));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                entityLocker.unlock();
            }
        }
    }
}

