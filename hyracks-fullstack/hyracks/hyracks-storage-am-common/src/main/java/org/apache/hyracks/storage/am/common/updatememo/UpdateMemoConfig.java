package org.apache.hyracks.storage.am.common.updatememo;

import java.util.concurrent.atomic.AtomicInteger;

public class UpdateMemoConfig {
    public static AtomicInteger ATOMIC_TS = new AtomicInteger(0);

    public static boolean USE_LOCAL_UM = false;

    public static boolean CLEAN_UPON_FLUSHING = true;
    public static boolean CLEAN_UPON_MERGING = true;

    public static boolean CLEAN_UPON_UPDATE = true;
    public static boolean VACUUM_CLEAN = true;

    public static int NUM_UNIQUE_ID = 100000;

    public static String SELECTED_MERGE_POLICY = "constant-merge";
    public static String NUM_COMPONENTS = "5";
    public static String MERGABLE = "1048576"; // 1MB
    //	public static String MERGABLE = "536870912"; 	// 512MB
    //	public static String MERGABLE = "1073741824"; 	// 1GB

    public static int MAX_UM_SIZE = -1;
    public static int NUM_FLUSH = 0;
    public static int NUM_MERGE = 0;
    public static long sumFlushTime = 0L;
    public static long sumMergeTime = 0L;

    public static void reset() {
        NUM_FLUSH = 0;
        NUM_MERGE = 0;
        sumFlushTime = 0L;
        sumMergeTime = 0L;
    }

}
