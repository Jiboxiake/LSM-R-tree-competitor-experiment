/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hyracks.storage.am.btree;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import org.apache.hyracks.api.dataflow.value.ISerializerDeserializer;
import org.apache.hyracks.api.dataflow.value.ITypeTraits;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.data.std.accessors.UTF8StringBinaryComparatorFactory;
import org.apache.hyracks.data.std.primitive.IntegerPointable;
import org.apache.hyracks.data.std.primitive.UTF8StringPointable;
import org.apache.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import org.apache.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import org.apache.hyracks.storage.am.common.IIndexTestWorkerFactory;
import org.apache.hyracks.storage.am.common.IndexMultiThreadTestDriver;
import org.apache.hyracks.storage.am.common.TestWorkloadConf;
import org.apache.hyracks.storage.am.common.api.ITreeIndex;
import org.apache.hyracks.storage.am.common.updatememo.UpdateMemoConfig;
import org.apache.hyracks.storage.am.config.AccessMethodTestsConfig;
import org.apache.hyracks.storage.common.IIndex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

@SuppressWarnings("rawtypes")
public abstract class OrderedIndexMultiThreadTest {

    protected final Logger LOGGER = LogManager.getLogger();

    // Machine-specific number of threads to use for testing.
    protected final int REGULAR_NUM_THREADS = Runtime.getRuntime().availableProcessors();
    // Excessive number of threads for testing.
    protected final int EXCESSIVE_NUM_THREADS = Runtime.getRuntime().availableProcessors() * 4;
    protected final int NUM_OPERATIONS = AccessMethodTestsConfig.BTREE_MULTITHREAD_NUM_OPERATIONS;

    protected ArrayList<TestWorkloadConf> workloadConfs = getTestWorkloadConf();

    protected abstract void setUp() throws HyracksDataException;

    protected abstract void tearDown() throws HyracksDataException;

    protected abstract IIndex createIndex(ITypeTraits[] typeTraits, IBinaryComparatorFactory[] cmpFactories,
            int[] bloomFilterKeyFields) throws HyracksDataException;

    protected abstract ITreeIndex createSecondaryTreeIndex(ITypeTraits[] typeTraits,
            IBinaryComparatorFactory[] cmpFactories, int[] bloomFilterKeyFields, ITypeTraits[] filterTypeTraits,
            IBinaryComparatorFactory[] filterCmpFactories, int[] btreeFields, int[] filterFields)
            throws HyracksDataException;

    protected abstract IIndexTestWorkerFactory getWorkerFactory();

    protected abstract ArrayList<TestWorkloadConf> getTestWorkloadConf();

    protected abstract String getIndexTypeName();

    protected void runTest(ISerializerDeserializer[] fieldSerdes, int numKeys, int numThreads, TestWorkloadConf conf,
            String dataMsg, String inputPrefix, int[] indexes, String method)
            throws InterruptedException, HyracksDataException {
        setUp();

        //        if (LOGGER.isInfoEnabled()) {
        //            String indexTypeName = getIndexTypeName();
        //            LOGGER.info(indexTypeName + " MultiThread Test:\nData: " + dataMsg + "; Threads: " + numThreads
        //                    + "; Workload: " + conf.toString() + ".");
        //        }

        int fieldCount = 3;
        ITypeTraits[] typeTraits = new ITypeTraits[fieldCount];
        typeTraits[0] = UTF8StringPointable.TYPE_TRAITS;
        //        typeTraits[0] = DoublePointable.TYPE_TRAITS;
        //        typeTraits[0] = IntegerPointable.TYPE_TRAITS;
        typeTraits[1] = IntegerPointable.TYPE_TRAITS;
        typeTraits[2] = IntegerPointable.TYPE_TRAITS;

        int keyFieldCount = 1;
        IBinaryComparatorFactory[] cmpFactories = new IBinaryComparatorFactory[keyFieldCount];
        cmpFactories[0] = UTF8StringBinaryComparatorFactory.INSTANCE;
        //        cmpFactories[0] = DoubleBinaryComparatorFactory.INSTANCE;
        //        cmpFactories[0] = IntegerBinaryComparatorFactory.INSTANCE;
        //        cmpFactories[1] = IntegerBinaryComparatorFactory.INSTANCE;

        int btreeKeyFieldCount;
        IBinaryComparatorFactory[] btreeCmpFactories;
        int[] btreeFields = null;
        btreeKeyFieldCount = 1;
        btreeCmpFactories = new IBinaryComparatorFactory[btreeKeyFieldCount];
        btreeCmpFactories[0] = UTF8StringBinaryComparatorFactory.INSTANCE;
        //        btreeCmpFactories[0] = DoubleBinaryComparatorFactory.INSTANCE;
        //        btreeCmpFactories[0] = IntegerBinaryComparatorFactory.INSTANCE;
        btreeFields = new int[btreeKeyFieldCount];
        for (int i = 0; i < btreeKeyFieldCount; i++) {
            btreeFields[i] = keyFieldCount + i;
        }

        ITreeIndex index = createSecondaryTreeIndex(typeTraits, cmpFactories, null, null, null, btreeFields, null);

        //        ITypeTraits[] typeTraits = SerdeUtils.serdesToTypeTraits(fieldSerdes);
        //        IBinaryComparatorFactory[] cmpFactories = SerdeUtils.serdesToComparatorFactories(fieldSerdes, numKeys);
        //
        //        // This is only used for the LSM-BTree.
        //        int[] bloomFilterKeyFields = new int[numKeys];
        //        for (int i = 0; i < numKeys; ++i) {
        //            bloomFilterKeyFields[i] = i;
        //        }
        //
        //        IIndex index = createIndex(typeTraits, cmpFactories, bloomFilterKeyFields);
        IIndexTestWorkerFactory workerFactory = getWorkerFactory();

        // 4 batches per thread.
        int batchSize = (NUM_OPERATIONS / numThreads) / 4;

        //      if (LOGGER.isWarnEnabled()) {
        //	      LOGGER.warn("NUM_OPERATIONS: " + NUM_OPERATIONS + ", batchSize: " + batchSize);
        //	  }

        IndexMultiThreadTestDriver driver =
                new IndexMultiThreadTestDriver(index, workerFactory, fieldSerdes, conf.ops, conf.opProbs);
        driver.init();
        long[] times = driver.run(numThreads, 1, NUM_OPERATIONS, batchSize, inputPrefix, indexes, method);
        //        index.validate();
        driver.deinit();

        //        if (LOGGER.isInfoEnabled()) {
        //            LOGGER.info("BTree MultiThread Test Time: " + times[0] + "ms");
        //        }
        if (LOGGER.isWarnEnabled()) {
            boolean cf = UpdateMemoConfig.CLEAN_UPON_FLUSHING;
            boolean cm = UpdateMemoConfig.CLEAN_UPON_MERGING;
            boolean cu = UpdateMemoConfig.CLEAN_UPON_UPDATE;
            boolean vc = UpdateMemoConfig.VACUUM_CLEAN;
            String strat = "(" + cf + "," + cm + "," + cu + "," + vc + ")";

            String indexTypeName = getIndexTypeName();
            LOGGER.warn(indexTypeName + " MultiThread Test: ---------------------------------"
                    + "------------------ Data: " + dataMsg + ";  Workload: " + conf.toString() + ".");
            LOGGER.warn("----------------------------------------------"
                    + "-------------------------------->  strategy: " + "UM" + strat + ", threads: " + numThreads);
            LOGGER.warn("----------------------------------------------"
                    + "-------------------------------->  inputPrefix: " + inputPrefix);
            LOGGER.warn("----------------------------------------------"
                    + "-------------------------------->  indexes: " + Arrays.toString(indexes));
            LOGGER.warn("----------------------------------------------"
                    + "-------------------------------->  BTree MultiThread Test Time: " + times[0] + " ms");
            LOGGER.warn("----------------------------------------------" + "-------------------------------->  #Flush: "
                    + (UpdateMemoConfig.NUM_FLUSH - 1) + " #Merge: " + UpdateMemoConfig.NUM_MERGE);
            LOGGER.warn("----------------------------------------------"
                    + "-------------------------------->  MAX MEMO SIZE: " + UpdateMemoConfig.MAX_UM_SIZE);
        }

        tearDown();
    }

    @Test
    public void btreeOneDimensionString() throws Exception {

        int numKeys = 2;
        //        IPrimitiveValueProviderFactory[] valueProviderFactories =
        //                RTreeUtils.createPrimitiveValueProviderFactories(numKeys, DoublePointable.FACTORY);

        ISerializerDeserializer[] fieldSerdes = { new UTF8StringSerializerDeserializer(),
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE };
        //        	{ IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE,
        //      		IntegerSerializerDeserializer.INSTANCE};

        String dataMsg = "One Dimensions Of String Values";
        String[] dataPrefixes = {
                //    			"/Users/nujwoo/Documents/workspaces/research/lsmrum/data/pluscode/test2_",
                //    			"/Users/nujwoo/Documents/workspaces/research/lsmrum/data/pluscode/gowalla_small_pluscode_",
                //    			"/Users/nujwoo/Documents/workspaces/research/lsmrum/data/pluscode/gowalla_pluscode_",
                "/Users/nujwoo/Documents/workspaces/research/lsmrum/data/pluscode/berlin_pluscode_",
                //        		"/Users/nujwoo/Documents/workspaces/research/lsmrum/data/pluscode/random_",
        };
        int[][] inputIndexes = { { 2, 3, 4, 5 }, { 2, 3, 4, 5 }, { 2, 3, 4, 5 } };

        //    	int[] btree_pages = {32768, 131072, 524288};

        String method = "LSMB";

        TestWorkloadConf conf = workloadConfs.get(0);

        //    	int[] threads = {1, 2, 4, 8};
        int[] threads = { 4 };
        int i = 0;
        for (String inputPrefix : dataPrefixes) {
            for (int th : threads) {
                //    			for(int numPage : btree_pages) {
                //    				AccessMethodTestsConfig.LSM_BTREE_MEM_NUM_PAGES = numPage;
                UpdateMemoConfig.ATOMIC_TS.set(0);
                runTest(fieldSerdes, numKeys, th, conf, dataMsg, inputPrefix, inputIndexes[i], method);
                //    			}
            }

            i += 1;
        }

    }
    //    @Test
    //    public void oneIntKeyAndValue() throws InterruptedException, HyracksDataException {
    //        ISerializerDeserializer[] fieldSerdes = new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE,
    //                IntegerSerializerDeserializer.INSTANCE };
    //        int numKeys = 1;
    //        String dataMsg = "One Int Key And Value";
    //
    //        for (TestWorkloadConf conf : workloadConfs) {
    //            runTest(fieldSerdes, numKeys, REGULAR_NUM_THREADS, conf, dataMsg);
    //            runTest(fieldSerdes, numKeys, EXCESSIVE_NUM_THREADS, conf, dataMsg);
    //        }
    //    }
    //
    //    @Test
    //    public void oneStringKeyAndValue() throws InterruptedException, HyracksDataException {
    //        ISerializerDeserializer[] fieldSerdes = new ISerializerDeserializer[] { new UTF8StringSerializerDeserializer(),
    //                new UTF8StringSerializerDeserializer() };
    //        int numKeys = 1;
    //        String dataMsg = "One String Key And Value";
    //
    //        for (TestWorkloadConf conf : workloadConfs) {
    //            runTest(fieldSerdes, numKeys, REGULAR_NUM_THREADS, conf, dataMsg);
    //            runTest(fieldSerdes, numKeys, EXCESSIVE_NUM_THREADS, conf, dataMsg);
    //        }
    //    }
}
