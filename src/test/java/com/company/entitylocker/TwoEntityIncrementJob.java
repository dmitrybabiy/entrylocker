package com.company.entitylocker;

import com.company.entitylocker.entities.IntValueEntity;

import java.util.concurrent.ThreadLocalRandom;

public class TwoEntityIncrementJob<T, U> implements Runnable {
    EntityLocker entityLocker;
    IntValueEntity<T> entity1;
    IntValueEntity<U> entity2;
    int nIterations;

    public TwoEntityIncrementJob(EntityLocker entityLocker, IntValueEntity<T> entity1, IntValueEntity<U> entity2, int nIterations) {
        this.entityLocker = entityLocker;
        this.entity1 = entity1;
        this.entity2 = entity2;
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

            entityLocker.lock(entity2);
            try {
                entity2.inc();
                System.out.println(Thread.currentThread().getName() + " : " + entity2);
                Thread.sleep(ThreadLocalRandom.current().nextInt(100));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                entityLocker.unlock();
            }
        }
    }
}
