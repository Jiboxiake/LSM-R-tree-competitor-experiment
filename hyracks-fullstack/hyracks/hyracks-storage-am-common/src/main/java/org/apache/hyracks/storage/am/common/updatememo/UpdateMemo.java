package org.apache.hyracks.storage.am.common.updatememo;

import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UpdateMemo<T> {
    private ConcurrentHashMap<T, UMEntity<T>> um;
    private static final Logger LOGGER = LogManager.getLogger();

    public UpdateMemo() {
        this.um = new ConcurrentHashMap<T, UMEntity<T>>();
    }

    private void update_count() {
        if (UpdateMemoConfig.MAX_UM_SIZE < this.um.size()) {
            UpdateMemoConfig.MAX_UM_SIZE = this.um.size();
        }
    }

    public void insertOrUpdate(T key, UMEntity<T> newEntity) {
        UMEntity<T> prevVal = um.putIfAbsent(key, newEntity);
        if (prevVal != null) {
            if (prevVal.lessThan(newEntity)) {
                prevVal.updateTS((T) newEntity.getTS());
            }
            prevVal.plusCnt();
        }
        //// single
        //if(this.um.containsKey(key)) {
        //if(this.um.get(key).newerThan(newEntity)) {
        //this.um.get(key).updateTS((T)newEntity.getTS());
        //}
        //			
        //this.um.get(key).addCnt(newEntity.getCnt());
        //}
        //else {
        //this.um.put(key, newEntity);
        //}

    }

    public void insertOrUpdate(T key, T ts) {
        //		LOGGER.warn("key: " + key + ", ts: " + ts);
        UMEntity<T> prevVal = um.putIfAbsent(key, new UMEntity<T>(ts));
        //		LOGGER.warn("prevVal: " + prevVal);
        //		LOGGER.warn(this.um.size());
        if (prevVal != null) {
            //			val exists.. --> do CILS (Compare If Less, then Swap)
            if (prevVal.lessThan(ts)) {
                //				swap
                prevVal.updateTS(ts);
            }
            prevVal.plusCnt();

        }

        this.update_count();

        //		// single
        //		if(this.um.containsKey(key)){
        //			// update entity
        //			this.um.get(key).updateTS(ts);
        //			this.um.get(key).plusCnt();
        //		}
        //		else{
        //			// put new entity
        //			this.um.put(key, new UMEntity<T>(ts));
        //		}
    }

    //	return true if the tuple with key needs to be cleaned
    public boolean cleanEntity(T key, T ts) {

        //		if((Integer)key == 1) {
        //			LOGGER.error("111111111" + " / " + ts);
        //		}
        //		else {
        ////			LOGGER.error(key );
        //		}
        //		LOGGER.error("cleaning: " + key + ", ts: " + ts);

        UMEntity<T> e = this.um.get(key);
        if (e == null) {
            return false;
        } else {
            if (e.newerThan(ts)) {
                if (e.minusCnt()) {
                    this.um.remove(key);
                }
                this.update_count();
                return true;
            } else {
                //				LOGGER.error("CANNOT BE HAPPEND??");
                return false;
            }
        }

        //		if(this.hasNewerEntity(key, ts)) {
        ////			LOGGER.error(this.um.get(key));
        //			if(this.um.get(key).minusCnt()) {
        ////				LOGGER.error(((Integer)key)+" removed:: compare " + (Integer)ts + " to " + (Integer)this.um.get(key).getTS());
        ////				LOGGER.error("removing: " + key + ", ts: " + this.um.get(key).getTS());
        //				this.um.remove(key);
        //				
        //				
        //			}
        ////			else {
        ////				LOGGER.error(((Integer)key)+" cleaned:: compare " + (Integer)ts + " to " + (Integer)this.um.get(key).getTS());
        ////			}
        //			
        //			return true;
        //		}
        //		else {
        //			return false;
        //		}
    }

    public boolean hasKey(T key) {
        if (this.um.containsKey(key)) {
            return true;
        } else {
            return false;
        }
    }

    public boolean hasNewerEntity(T key, T ts) {
        if (this.um.containsKey(key)) {
            if (this.um.get(key).newerThan(ts)) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public Set<Entry<T, UMEntity<T>>> getEntrySet() {
        return this.um.entrySet();
    }

    public int getSize() {
        return this.um.size();
    }

    public void reset() {
        this.um = new ConcurrentHashMap<T, UMEntity<T>>();
        //		LOGGER.warn("local UM reset");
    }

    public String print() {
        String ret = "";
        for (T k : this.um.keySet()) {
            String key = k.toString();
            String value = this.um.get(k).getTS() + " " + this.um.get(k).getCnt();
            ret += key + ": " + value;
        }
        return ret;
    }
}
