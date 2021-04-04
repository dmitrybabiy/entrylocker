package com.company.entitylocker.entities;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IntValueEntity<T> implements Entity<T> {
    private final T id;
    private Integer value;

    @Override
    public T getId() {
        return id;
    }

    public void inc() {
        ++value;
    }

    @Override
    public String toString() {
        return "IntValueEntity{" +
                "id=" + id +
                ", value=" + value +
                '}';
    }
}
