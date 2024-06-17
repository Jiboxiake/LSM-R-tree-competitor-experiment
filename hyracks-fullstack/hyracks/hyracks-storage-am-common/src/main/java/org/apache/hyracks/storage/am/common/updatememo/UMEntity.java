package org.apache.hyracks.storage.am.common.updatememo;

import java.util.concurrent.atomic.AtomicInteger;

public class UMEntity<T> {
    private T ts;
    private AtomicInteger cnt;

    public UMEntity(T ts) {
        this.ts = ts;
        this.cnt = new AtomicInteger(1);
    }

    public AtomicInteger getCnt() {
        return this.cnt;
    }

    public void plusCnt() {
        this.cnt.incrementAndGet();
    }

    public boolean minusCnt() {
        if (this.cnt.decrementAndGet() == 0) {
            return true;
        } else {
            return false;
        }
    }

    public void addCnt(int newCnt) {
        this.cnt.getAndAdd(newCnt);
    }

    public T getTS() {
        return this.ts;
    }

    public void updateTS(T newTS) {
        this.ts = newTS;
    }

    //public void addTS(double newTS) {
    //this.ts += newTS;
    //}

    public boolean newerThan(UMEntity<T> e) {

        if ((Integer) this.ts > (Integer) e.ts)
            return true;
        else
            return false;
    }

    public boolean newerThan(T ts) {

        if ((Integer) this.ts > (Integer) ts)
            return true;
        else
            return false;

    }

    public boolean lessThan(UMEntity<T> e) {

        if ((Integer) this.ts < (Integer) e.ts)
            return true;
        else
            return false;

    }

    public boolean lessThan(T ts) {

        if ((Integer) this.ts < (Integer) ts)
            return true;
        else
            return false;

    }
}
