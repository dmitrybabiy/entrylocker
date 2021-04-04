package com.company.entitylocker;

import com.company.entitylocker.entities.IntValueEntity;

import java.util.concurrent.Callable;

public class TwoEntityInnerLockJob<T, U> implements Callable<Integer> {
    EntityLocker entityLocker;
    IntValueEntity<T> entity1;
    IntValueEntity<U> entity2;

    public TwoEntityInnerLockJob(EntityLocker entityLocker, IntValueEntity<T> entity1, IntValueEntity<U> entity2) {
        this.entityLocker = entityLocker;
        this.entity1 = entity1;
        this.entity2 = entity2;
    }

    @Override
    public Integer call() {
        entityLocker.lock(entity1);
        try {
            entity1.inc();
            System.out.println(Thread.currentThread().getName() + " : " + entity1);
            Thread.sleep(100);

            entityLocker.lock(entity2);
            try {
                entity2.inc();
                System.out.println(Thread.currentThread().getName() + " : " + entity2);
            } finally {
                entityLocker.unlock();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            entityLocker.unlock();
        }
        return 0;
    }

}
