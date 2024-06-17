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

package org.apache.hyracks.storage.am.lsm.rtree;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import org.apache.hyracks.api.dataflow.value.ISerializerDeserializer;
import org.apache.hyracks.api.dataflow.value.ITypeTraits;
import org.apache.hyracks.api.exceptions.ErrorCode;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.data.std.accessors.DoubleBinaryComparatorFactory;
import org.apache.hyracks.data.std.accessors.IntegerBinaryComparatorFactory;
import org.apache.hyracks.data.std.primitive.DoublePointable;
import org.apache.hyracks.data.std.primitive.IntegerPointable;
import org.apache.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import org.apache.hyracks.dataflow.common.comm.io.ArrayTupleReference;
import org.apache.hyracks.dataflow.common.data.marshalling.DoubleSerializerDeserializer;
import org.apache.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import org.apache.hyracks.dataflow.common.utils.TupleUtils;
import org.apache.hyracks.storage.am.common.api.IPrimitiveValueProviderFactory;
import org.apache.hyracks.storage.am.common.api.ITreeIndex;
import org.apache.hyracks.storage.am.common.impls.NoOpIndexAccessParameters;
import org.apache.hyracks.storage.am.common.updatememo.UpdateMemoConfig;
import org.apache.hyracks.storage.am.config.AccessMethodTestsConfig;
import org.apache.hyracks.storage.am.rtree.AbstractRTreeExamplesTest;
import org.apache.hyracks.storage.am.rtree.frames.RTreePolicyType;
import org.apache.hyracks.storage.am.rtree.util.RTreeUtils;
import org.apache.hyracks.storage.common.IIndexAccessor;
import org.apache.hyracks.storage.common.buffercache.CleaningConfig;
import org.junit.Test;

public abstract class AbstractLSMRTreeExamplesTest extends AbstractRTreeExamplesTest {
    private String dataPath;
    private int numData;
    private String dataIndex;
    private String dataBoundary;
    private String queryPoints;

    public AbstractLSMRTreeExamplesTest(Boolean lum, Boolean flush, Boolean merge, Boolean update, Boolean vacuum,
            String policy, String numComp, Integer updateTh, Integer vacuumTh, String dataPath, Integer numData,
            String dataIndex, String dataBoundary, String queryPoints) {
        //    	super();
        UpdateMemoConfig.USE_LOCAL_UM = lum;

        UpdateMemoConfig.CLEAN_UPON_FLUSHING = flush;
        UpdateMemoConfig.CLEAN_UPON_MERGING = merge;

        UpdateMemoConfig.CLEAN_UPON_UPDATE = update;
        UpdateMemoConfig.VACUUM_CLEAN = vacuum;

        UpdateMemoConfig.SELECTED_MERGE_POLICY = policy;
        UpdateMemoConfig.NUM_COMPONENTS = numComp;

        CleaningConfig.UPDATE_THRESHOLD = updateTh;
        CleaningConfig.VACUUM_THRESHOLD = vacuumTh;

        this.dataPath = dataPath;
        this.numData = numData;
        this.dataIndex = dataIndex;
        this.dataBoundary = dataBoundary;
        this.queryPoints = queryPoints;
    }

    /**
     * Test the LSM component filters.
     */
    @Test
    public void additionalFilteringingExample() throws Exception {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Testing LSMRTree or LSMRTreeWithAntiMatterTuples component filters.");
        }

        LOGGER.error("==========================================================================");
        LOGGER.error("Test params: LSMRTree with UM");
        LOGGER.error(
                "  - policy : " + UpdateMemoConfig.SELECTED_MERGE_POLICY + " : " + UpdateMemoConfig.NUM_COMPONENTS);
        LOGGER.error("  - Disk page size : " + AccessMethodTestsConfig.LSM_RTREE_DISK_PAGE_SIZE);
        LOGGER.error("  - Disk num pages : " + AccessMethodTestsConfig.LSM_RTREE_DISK_NUM_PAGES);
        LOGGER.error("  - Mem page size : " + AccessMethodTestsConfig.LSM_RTREE_MEM_PAGE_SIZE);
        LOGGER.error("  - Mem num pages : " + AccessMethodTestsConfig.LSM_RTREE_MEM_NUM_PAGES);
        LOGGER.error("  - data path	: " + this.dataPath);
        LOGGER.error("  -      #	: " + this.numData);
        LOGGER.error("  -      bdry	: " + this.dataBoundary);
        LOGGER.error("  -      indx	: " + this.dataIndex);
        LOGGER.error("  -      qry	: " + this.queryPoints);
        LOGGER.error(" ");
        LOGGER.error("  - use local um      : " + UpdateMemoConfig.USE_LOCAL_UM);
        LOGGER.error("  - clean-upon-flush  : " + UpdateMemoConfig.CLEAN_UPON_FLUSHING);
        LOGGER.error("  - clean-upon-merge  : " + UpdateMemoConfig.CLEAN_UPON_MERGING);
        LOGGER.error("  - buffered cleaning : " + UpdateMemoConfig.CLEAN_UPON_UPDATE);
        LOGGER.error("  -      threshold    : " + CleaningConfig.UPDATE_THRESHOLD);
        LOGGER.error("  - vacuum cleaning   : " + UpdateMemoConfig.VACUUM_CLEAN);
        LOGGER.error("  -      threshold    : " + CleaningConfig.VACUUM_THRESHOLD);
        LOGGER.error(" ");

        UpdateMemoConfig.reset();

        // Declare fields.
        int fieldCount = 6;
        ITypeTraits[] typeTraits = new ITypeTraits[fieldCount];
        typeTraits[0] = DoublePointable.TYPE_TRAITS;
        typeTraits[1] = DoublePointable.TYPE_TRAITS;
        typeTraits[2] = DoublePointable.TYPE_TRAITS;
        typeTraits[3] = DoublePointable.TYPE_TRAITS;
        typeTraits[4] = IntegerPointable.TYPE_TRAITS;
        typeTraits[5] = IntegerPointable.TYPE_TRAITS;
        // Declare field serdes.
        ISerializerDeserializer[] fieldSerdes =
                { DoubleSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
                        DoubleSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
                        IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE };

        // Declare RTree keys.
        int rtreeKeyFieldCount = 4;
        IBinaryComparatorFactory[] rtreeCmpFactories = new IBinaryComparatorFactory[rtreeKeyFieldCount];
        rtreeCmpFactories[0] = DoubleBinaryComparatorFactory.INSTANCE;
        rtreeCmpFactories[1] = DoubleBinaryComparatorFactory.INSTANCE;
        rtreeCmpFactories[2] = DoubleBinaryComparatorFactory.INSTANCE;
        rtreeCmpFactories[3] = DoubleBinaryComparatorFactory.INSTANCE;

        // Declare BTree keys, this will only be used for LSMRTree
        int btreeKeyFieldCount;
        IBinaryComparatorFactory[] btreeCmpFactories;
        int[] btreeFields = null;
        if (rTreeType == RTreeType.LSMRTREE) {
            //Parameters look different for LSM RTREE from LSM RTREE WITH ANTI MATTER TUPLES
            //        	LOGGER.error("LSMRTreeUM");
            btreeKeyFieldCount = 2;
            btreeCmpFactories = new IBinaryComparatorFactory[btreeKeyFieldCount];
            btreeCmpFactories[0] = IntegerBinaryComparatorFactory.INSTANCE;
            btreeCmpFactories[1] = IntegerBinaryComparatorFactory.INSTANCE;
            btreeFields = new int[btreeKeyFieldCount];
            for (int i = 0; i < btreeKeyFieldCount; i++) {
                btreeFields[i] = rtreeKeyFieldCount + i;
            }

        } else {
            LOGGER.error("??????????????????????!!!!!!!!!!!!!!!!ERRORROROR");
            btreeKeyFieldCount = 6;
            btreeCmpFactories = new IBinaryComparatorFactory[btreeKeyFieldCount];
            btreeCmpFactories[0] = IntegerBinaryComparatorFactory.INSTANCE;
            btreeCmpFactories[1] = IntegerBinaryComparatorFactory.INSTANCE;
            btreeCmpFactories[2] = DoubleBinaryComparatorFactory.INSTANCE;
            btreeCmpFactories[3] = DoubleBinaryComparatorFactory.INSTANCE;
            btreeCmpFactories[4] = DoubleBinaryComparatorFactory.INSTANCE;
            btreeCmpFactories[5] = DoubleBinaryComparatorFactory.INSTANCE;
        }

        // create value providers
        IPrimitiveValueProviderFactory[] valueProviderFactories =
                RTreeUtils.createPrimitiveValueProviderFactories(rtreeCmpFactories.length, DoublePointable.FACTORY);

        //        int[] rtreeFields = { 0, 1, 2, 3 }; 
        ////        		,4 };
        //        ITypeTraits[] filterTypeTraits = { IntegerPointable.TYPE_TRAITS };
        //        IBinaryComparatorFactory[] filterCmpFactories = { IntegerBinaryComparatorFactory.INSTANCE };
        //        int[] filterFields = { 4 };
        ////        int[] filterFields = { 5 };

        ITreeIndex treeIndex = createTreeIndex(typeTraits, rtreeCmpFactories, btreeCmpFactories, valueProviderFactories,
                RTreePolicyType.RTREE, null, btreeFields, null, null, null);
        //                RTreePolicyType.RTREE, rtreeFields, btreeFields, filterTypeTraits, filterCmpFactories, filterFields);
        treeIndex.create();
        treeIndex.activate();

        //        gowalla
        //        A/U, id, time, lat, lon, locId
        //        U,38375,2010-04-28T14:40:41Z,19426424,-99196072,497351
        String data = this.dataPath;
        String[] ins = this.dataIndex.split(",");
        int[] index = new int[ins.length];
        for (int in = 0; in < ins.length; in++) {
            index[in] = Integer.parseInt(ins[in]);
        }

        FileInputStream fstream = new FileInputStream(data);
        DataInputStream in = new DataInputStream(fstream);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));

        long start = 0L;
        long end = 0L;
        long time = 0L;
        ArrayList<Long> times = new ArrayList<Long>();
        ArrayTupleBuilder tb = new ArrayTupleBuilder(fieldCount);
        ArrayTupleReference tuple = new ArrayTupleReference();
        IIndexAccessor indexAccessor = treeIndex.createAccessor(NoOpIndexAccessParameters.INSTANCE);

        int id;
        double x, y;
        int ts;
        int i = 0;
        boolean isInsert = false;
        String line = "";
        while ((line = br.readLine()) != null && line.length() > 0) {
            i++;
            //			LOGGER.error(line);
            String items[] = line.split(",");

            ts = Integer.valueOf(items[index[0]]);
            id = Integer.valueOf(items[index[1]]);
            x = Double.valueOf(items[index[2]]);
            y = Double.valueOf(items[index[3]]);
            if (items[0].equals("I")) {
                isInsert = true;
            } else {
                isInsert = false;
            }
            //			TupleUtils.createIntegerTuple(tb, tuple, x, y, x, y, id, ts);
            tuple = (ArrayTupleReference) TupleUtils.createTuple(fieldSerdes, x, y, x, y, id, ts);

            try {
                start = System.currentTimeMillis();
                if (isInsert)
                    indexAccessor.insert(tuple);
                else {
                    indexAccessor.update(tuple);
                    //					indexAccessor.delete(tuple2);
                    //					indexAccessor.insert(tuple);
                }
                end = System.currentTimeMillis();
                time += end - start;

            } catch (HyracksDataException e) {

                if (e.getErrorCode() != ErrorCode.DUPLICATE_KEY) {
                    throw e;
                }
            }
            if (ts % (this.numData / 10) == 0) {
                //				LOGGER.error(ts + " " + x + " " + y);
                LOGGER.error(String.format("%.2f", ((double) ts / this.numData * 100.0)) + "%");
                times.add(time);
            }

        }
        times.add(time);

        long searchStart = 0L;
        long searchEnd = 0L;
        long searchTime = 0L;

        String[] boundaries = this.dataBoundary.split(",");
        double minX = Double.valueOf(boundaries[0]);
        double maxX = Double.valueOf(boundaries[1]);
        double minY = Double.valueOf(boundaries[2]);
        double maxY = Double.valueOf(boundaries[3]);

        ArrayList<Double[]> searchPoints = new ArrayList<Double[]>();
        String[] points = this.queryPoints.split("/");
        for (int j = 0; j < points.length; j++) {
            String[] tmp = points[j].split(",");
            searchPoints.add(new Double[] { Double.valueOf(tmp[0]), Double.valueOf(tmp[1]) });
        }

        ArrayList<Long> searchTimes = new ArrayList<Long>();
        ArrayTupleBuilder keyTb = new ArrayTupleBuilder(rtreeKeyFieldCount);
        ArrayTupleReference key = new ArrayTupleReference();

        int multiplier = 8;
        for (int j = 1; j < multiplier; j++) {
            searchTime = 0L;
            LOGGER.error("Search : " + String.format("%.4f", ((j * j * j * 0.001) * (j * j * j * 0.001) * 100)) + " %");
            for (int k = 0; k < searchPoints.size(); k++) {
                //		    	TupleUtils.createIntegerTuple(keyTb, key, -500*i, -500*i, 500*i, 500*i);
                double xx = searchPoints.get(k)[0];
                double yy = searchPoints.get(k)[1];
                double sizeX = ((maxX - minX) * j * j * j * 0.001) / 2.0;
                double sizeY = ((maxY - minY) * j * j * j * 0.001) / 2.0;

                //	    		LOGGER.error((xx - sizeX) + ", " + (yy - sizeY) + " to " +  (xx + sizeX) + ", " +  (yy + sizeY));
                TupleUtils.createDoubleTuple(keyTb, key, xx - sizeX, yy - sizeY, xx + sizeX, yy + sizeY);
                //		    	tuple = (ArrayTupleReference) TupleUtils.createTuple(fieldSerdes, xx - sizeX, yy - sizeY, xx + sizeX, yy + sizeY);

                searchStart = System.currentTimeMillis();
                rangeSearch(rtreeCmpFactories, indexAccessor, fieldSerdes, key, null, null);
                searchEnd = System.currentTimeMillis();
                searchTime += searchEnd - searchStart;
            }

            searchTimes.add(searchTime / searchPoints.size());
        }

        treeIndex.deactivate();
        treeIndex.destroy();

        LOGGER.error("-------------------------------------------------------------------");
        String lines = "";
        for (int j = 0; j < times.size(); j++)
            lines += times.get(j) + ", ";
        LOGGER.error(i + " inserts in " + lines + " ms");

        String reg = "(";
        for (int j = 1; j < multiplier; j++) {
            reg += String.format("%.4f", ((j * j * j * 0.001) * (j * j * j * 0.001) * 100)) + " %, ";
        }
        reg += ")";
        lines = "";
        for (int j = 0; j < searchTimes.size(); j++)
            lines += searchTimes.get(j) + ", ";
        LOGGER.error(reg + " searched in " + lines + " ms");

        double af = 0.0;
        double am = 0.0;
        if (UpdateMemoConfig.NUM_FLUSH == 0)
            LOGGER.error("No Flush");
        else
            af = UpdateMemoConfig.sumFlushTime / UpdateMemoConfig.NUM_FLUSH;

        if (UpdateMemoConfig.NUM_MERGE == 0)
            LOGGER.error("No Merge");
        else
            am = UpdateMemoConfig.sumMergeTime / UpdateMemoConfig.NUM_MERGE;

        LOGGER.error("#Flush: " + UpdateMemoConfig.NUM_FLUSH + ", Sum Flush time: " + UpdateMemoConfig.sumFlushTime
                + ", Avg. Flush time: " + af + " ms // #Merge: " + UpdateMemoConfig.NUM_MERGE + ", Sum Merge time: "
                + UpdateMemoConfig.sumMergeTime + " ms, Avg. Merge time: " + am + " ms.");

        LOGGER.error("-------------------------------------------------------------------");

        //        int ts = 0;
        //        long insertStart = 0L;
        //        long insertEnd = 0L;
        //        long insertTime = 0L;
        //        
        //        ArrayList<Long> insertTimes = new ArrayList<Long>();
        //
        ////        long start = System.currentTimeMillis();
        ////        if (LOGGER.isInfoEnabled()) {
        //        
        ////        }
        //        ArrayTupleBuilder tb = new ArrayTupleBuilder(fieldCount);
        //        ArrayTupleReference tuple = new ArrayTupleReference();
        //        IIndexAccessor indexAccessor = treeIndex.createAccessor(NoOpIndexAccessParameters.INSTANCE);
        //        
        //        int numInserts = 100;
        //        int rangeDivider = 429480;
        //        LOGGER.error("Inserting into tree... w/ " + numInserts + " tuples");
        //        
        //        ArrayList<ArrayList<Integer>> arr = new ArrayList<ArrayList<Integer>>();
        //        for (int i = 0; i < numInserts; i++) {
        ////            int p1x = rnd.nextInt();
        ////            int p1y = rnd.nextInt();
        ////            int p2x = rnd.nextInt();
        ////            int p2y = rnd.nextInt();
        //
        ////            int pk = 5;
        ////            int filter = i;
        //        	int p1x = rnd.nextInt()/rangeDivider;
        //            int p1y = rnd.nextInt()/rangeDivider;
        //            int pk1 = i;
        //            ts += 1;
        //            
        //            ArrayList<Integer> a = new ArrayList<Integer>();
        //	        a.add(p1x);
        //	        a.add(p1y);
        ////	        a.add(p1x);
        ////	        a.add(p1y);
        //	        a.add(pk1);
        ////	        a.add(ts);
        //	        arr.add(a);
        //
        ////            TupleUtils.createIntegerTuple(tb, tuple, Math.min(p1x, p2x), Math.min(p1y, p2y), Math.max(p1x, p2x),
        ////                    Math.max(p1y, p2y), pk, filter);
        //	        LOGGER.error(i + " inserted");
        //	        TupleUtils.createIntegerTuple(tb, tuple, Math.min(p1x, p1x), Math.min(p1y, p1y), Math.max(p1x, p1x),
        //	                  Math.max(p1y, p1y), pk1, ts);
        //	        
        ////          if (LOGGER.isInfoEnabled() && i <= 3) {
        ////            String rec = TupleUtils.printTuple(tuple, fieldSerdes);
        ////            LOGGER.info(rec);
        ////          }
        //	        
        //            try {
        //            	insertStart = System.currentTimeMillis();
        //                indexAccessor.insert(tuple);
        //                insertEnd = System.currentTimeMillis();
        //                insertTime += insertEnd - insertStart;
        ////                indexAccessor.insert(tuple);
        //            } catch (HyracksDataException e) {
        //                if (e.getErrorCode() != ErrorCode.DUPLICATE_KEY) {
        //                    throw e;
        //                }
        //            }
        //            
        //            if((i+1) % 2000 == 0) {
        //            	insertTimes.add(insertTime);
        //            }
        //        }
        ////        long end = System.currentTimeMillis();
        ////        if (LOGGER.isInfoEnabled()) {
        ////            LOGGER.info(numInserts + " inserts in " + (end - start) + "ms");
        ////        }
        //        
        //        
        //        
        //        
        //        
        //        
        //       
        //        
        //        
        //        
        //
        //        
        //        
        //        long updateStart = 0L;
        //        long updateEnd = 0L;
        //        long updateTime = 0L;
        //        int numUpdates = 5;
        //        ArrayTupleReference tuple2 = new ArrayTupleReference();
        //        LOGGER.error("Updating tree...");
        //        for (int i = 0; i < arr.size()*numUpdates; i++) {
        ////        	ArrayList<Integer> t = arr.get(i);
        //        	ts += 1;
        ////	        TupleUtils.createIntegerTuple(tb, tuple, t.get(0), t.get(1), t.get(0), t.get(1), t.get(2), ts);
        //	        
        //	        int p1x = rnd.nextInt()/rangeDivider;
        //            int p1y = rnd.nextInt()/rangeDivider;
        //            TupleUtils.createIntegerTuple(tb, tuple2, p1x, p1y, p1x, p1y, i%numInserts, ts);
        ////	        TupleUtils.createIntegerTuple(tb, tuple2, p1x, p1y, p1x, p1y, t.get(2), ts);
        ////            TupleUtils.createIntegerTuple(tb, tuple2, p1x, p1y, p1x, p1y, 1, ts);
        //	//          if (LOGGER.isInfoEnabled()) {
        //	//	            String rec = TupleUtils.printTuple(tuple, fieldSerdes);
        //	//	            LOGGER.info(rec);
        //	//          }
        ////            String rec = TupleUtils.printTuple(tuple2, fieldSerdes);
        ////            LOGGER.error(rec);
        //            	LOGGER.error("Update: " + (i%numInserts));
        //            try {
        //	        	  
        //            	updateStart = System.currentTimeMillis();
        ////            	indexAccessor.delete(tuple);
        //            	indexAccessor.update(tuple2);
        //            	updateEnd = System.currentTimeMillis();
        //            	updateTime += updateEnd - updateStart;
        //	//              delDone++;
        //            } catch (HyracksDataException e) {
        //            	if (e.getErrorCode() != ErrorCode.UPDATE_OR_DELETE_NON_EXISTENT_KEY) {
        //            		throw e;
        //            	}
        //            }
        //	//          if (insDoneCmp[i] != delDone) {
        //	//              if (LOGGER.isInfoEnabled()) {
        //	//                  LOGGER.info("INCONSISTENT STATE, ERROR IN DELETION EXAMPLE.");
        //	//                  LOGGER.info("INSDONECMP: " + insDoneCmp[i] + " " + delDone);
        //	//              }
        //	//              break;
        //	//          }
        //	    }
        //        
        //        
        //        
        //        
        //        
        //        
        //        
        // 
        //        
        //        
        //        
        //        
        ////        if (LOGGER.isInfoEnabled()) {
        ////            LOGGER.info("Deleting from tree...");
        ////        }
        //        long deleteStart = 0L;
        //        long deleteEnd = 0L;
        //        long deleteTime = 0L;
        //        LOGGER.error("Deleting tree...");
        //        for (int i = 0; i < arr.size(); i++) {
        //        	ArrayList<Integer> t = arr.get(i);
        //        	ts += 1;
        //	        TupleUtils.createIntegerTuple(tb, tuple, t.get(0), t.get(1), t.get(0), t.get(1), t.get(2), ts);
        ////	          if (LOGGER.isInfoEnabled()) {
        ////		            String rec = TupleUtils.printTuple(tuple, fieldSerdes);
        ////		            LOGGER.info(rec);
        ////	          }
        //	        LOGGER.error("Delete: " + t.get(2));
        //            try {
        //	        	  
        //            	deleteStart = System.currentTimeMillis();
        //            	indexAccessor.delete(tuple);
        //            	deleteEnd = System.currentTimeMillis();
        //            	deleteTime += deleteEnd - deleteStart;
        //	//              delDone++;
        //            } catch (HyracksDataException e) {
        //            	if (e.getErrorCode() != ErrorCode.UPDATE_OR_DELETE_NON_EXISTENT_KEY) {
        //            		throw e;
        //            	}
        //            }
        //	//          if (insDoneCmp[i] != delDone) {
        //	//              if (LOGGER.isInfoEnabled()) {
        //	//                  LOGGER.info("INCONSISTENT STATE, ERROR IN DELETION EXAMPLE.");
        //	//                  LOGGER.info("INSDONECMP: " + insDoneCmp[i] + " " + delDone);
        //	//              }
        //	//              break;
        //	//          }
        //	    }
        //        
        //        
        //        
        //        
        //        
        //        
        ////        for (int i = 0; i < numInserts; i++) {
        //////          int p1x = rnd.nextInt();
        //////          int p1y = rnd.nextInt();
        //////          int p2x = rnd.nextInt();
        //////          int p2y = rnd.nextInt();
        ////
        //////          int pk = 5;
        //////          int filter = i;
        ////        	int p1x = rnd.nextInt()/rangeDivider;
        ////        	int p1y = rnd.nextInt()/rangeDivider;
        ////        	int pk1 = i;
        ////        	ts += 1;
        ////	        
        ////	        TupleUtils.createIntegerTuple(tb, tuple, Math.min(p1x, p1x), Math.min(p1y, p1y), Math.max(p1x, p1x),
        ////	                  Math.max(p1y, p1y), pk1, ts);
        ////	        
        ////	        
        ////	        try {
        //////	          	insertStart = System.currentTimeMillis();
        ////	            indexAccessor.insert(tuple);
        //////	            insertEnd = System.currentTimeMillis();
        //////	            insertTime += insertEnd - insertStart;
        ////	//              indexAccessor.insert(tuple);
        ////	        } catch (HyracksDataException e) {
        ////	            if (e.getErrorCode() != ErrorCode.DUPLICATE_KEY) {
        ////	                throw e;
        ////	            }
        ////	        }
        ////      }
        //
        //
        ////        
        ////
        //////        scan(indexAccessor, fieldSerdes);
        //////        diskOrderScan(indexAccessor, fieldSerdes);
        //////
        //////        // Build key.
        //        ArrayTupleBuilder keyTb = new ArrayTupleBuilder(rtreeKeyFieldCount);
        //        ArrayTupleReference key = new ArrayTupleReference();
        //////        TupleUtils.createIntegerTuple(keyTb, key, -1000, -1000, 1000, 1000);
        //////
        //////        // Build min filter key.
        //////        ArrayTupleBuilder minFilterTb = new ArrayTupleBuilder(filterFields.length);
        //////        ArrayTupleReference minTuple = new ArrayTupleReference();
        //////        TupleUtils.createIntegerTuple(minFilterTb, minTuple, 400);
        //////
        //////        // Build max filter key.
        //////        ArrayTupleBuilder maxFilterTb = new ArrayTupleBuilder(filterFields.length);
        //////        ArrayTupleReference maxTuple = new ArrayTupleReference();
        //////        TupleUtils.createIntegerTuple(maxFilterTb, maxTuple, 500);
        //////
        ////        rangeSearch(rtreeCmpFactories, indexAccessor, fieldSerdes, key, minTuple, maxTuple);
        ////        
        //        long searchStart = 0L;
        //        long searchEnd = 0L;
        //        long searchTime = 0L;
        //        ArrayList<Long> searchTimes = new ArrayList<Long>();
        //        
        //        for(int i = 1; i < 2; i++) {
        //        	
        //        	TupleUtils.createIntegerTuple(keyTb, key, -500*i, -500*i, 500*i, 500*i);
        //        	searchTime = 0L;
        //        	
        //        	for(int j = 0; j < 10; j++) {
        //        		searchStart = System.currentTimeMillis();
        //        		rangeSearch(rtreeCmpFactories, indexAccessor, fieldSerdes, key, null, null);
        //	        	searchEnd = System.currentTimeMillis();
        //	        	searchTime += searchEnd - searchStart;
        //        	}
        //        	
        //        	searchTimes.add(searchTime / 10); 
        //        }
        //        
        //        
        //        if (LOGGER.isErrorEnabled()) {
        //        	String lines = "";
        //        	for(int i = 0; i < insertTimes.size(); i++)
        //        		lines += insertTimes.get(i) + ", ";
        //            LOGGER.error(numInserts + " inserts in " + lines + " ms");
        //        }
        //        
        //        
        //        if (LOGGER.isErrorEnabled()) {
        //            LOGGER.error(arr.size() + " deleted in " + (deleteTime) + " ms");
        //        }
        //        
        //        if (LOGGER.isErrorEnabled()) {
        //            LOGGER.error(arr.size() + " updated in " + (updateTime) + " ms");
        //        }
        //        
        ////        if (LOGGER.isErrorEnabled()) {
        ////        	String lines = "";
        ////        	for(int i = 0; i < searchTimes.size(); i++) {
        ////        		lines += searchTimes.get(i) + ", ";
        ////        	}
        ////            LOGGER.error("Searched in " + (lines) + " ms");
        ////        }

        //        treeIndex.deactivate();
        //        treeIndex.destroy();
    }

}
