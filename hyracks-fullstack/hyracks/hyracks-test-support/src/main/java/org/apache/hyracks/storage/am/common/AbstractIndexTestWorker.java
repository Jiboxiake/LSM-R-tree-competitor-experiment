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

package org.apache.hyracks.storage.am.common;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Random;

import org.apache.hyracks.api.dataflow.value.ISerializerDeserializer;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.common.comm.io.ArrayTupleReference;
import org.apache.hyracks.dataflow.common.data.accessors.ITupleReference;
import org.apache.hyracks.dataflow.common.data.marshalling.DoubleSerializerDeserializer;
import org.apache.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import org.apache.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import org.apache.hyracks.dataflow.common.utils.TupleUtils;
import org.apache.hyracks.storage.am.common.TestOperationSelector.TestOperation;
import org.apache.hyracks.storage.am.common.datagen.DataGenThread;
import org.apache.hyracks.storage.am.common.impls.IndexAccessParameters;
import org.apache.hyracks.storage.am.common.updatememo.UpdateMemoConfig;
import org.apache.hyracks.storage.common.IIndex;
import org.apache.hyracks.storage.common.IIndexAccessor;
import org.apache.hyracks.storage.common.IIndexCursor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractIndexTestWorker extends Thread implements ITreeIndexTestWorker {
    private final Random rnd;
    private final DataGenThread dataGen;
    private final TestOperationSelector opSelector;
    private final int numBatches;
    private HashMap<Integer, Integer> keys;
    private int selfNum = -1;
    private int totalNum = -1;
    private String inputPrefix = "";
    private int[] indexes = {};
    private String testMethod = "";

    protected IIndexAccessor indexAccessor;

    private static final Logger LOGGER = LogManager.getLogger();

    public AbstractIndexTestWorker(DataGenThread dataGen, TestOperationSelector opSelector, IIndex index,
            int numBatches) throws HyracksDataException {
        this.dataGen = dataGen;
        this.opSelector = opSelector;
        this.numBatches = numBatches;
        this.rnd = new Random();
        IndexAccessParameters actx =
                new IndexAccessParameters(TestOperationCallback.INSTANCE, TestOperationCallback.INSTANCE);
        this.indexAccessor = index.createAccessor(actx);

        keys = new HashMap<Integer, Integer>();
    }

    public AbstractIndexTestWorker(DataGenThread dataGen, TestOperationSelector opSelector, IIndex index,
            int numBatches, int selfNum, int totalNum, String inputPrefix, int[] indexes, String testMethod)
            throws HyracksDataException {
        this.dataGen = dataGen;
        this.opSelector = opSelector;
        this.numBatches = numBatches;
        this.rnd = new Random();
        this.selfNum = selfNum;
        this.totalNum = totalNum;
        this.testMethod = testMethod;

        this.inputPrefix = inputPrefix;
        this.indexes = indexes;

        IndexAccessParameters actx =
                new IndexAccessParameters(TestOperationCallback.INSTANCE, TestOperationCallback.INSTANCE);
        this.indexAccessor = index.createAccessor(actx);

        keys = new HashMap<Integer, Integer>();
    }

    @Override
    public void run() {
        try {
            LOGGER.warn("WORKERSTARTED: " + this.testMethod);
            //        	////////////////////////////////////////////////////////   LSM   B-tree ./.............................
            if (this.testMethod == "LSMB") {
                ISerializerDeserializer[] fieldSerdes =
                        { new UTF8StringSerializerDeserializer(), IntegerSerializerDeserializer.INSTANCE,
                                //        			{ IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE,
                                IntegerSerializerDeserializer.INSTANCE };

                int id;
                String code;
                //	          	int code;
                if (selfNum != -1) {
                    //          		FileReader fr = new FileReader("/Users/nujwoo/Documents/workspaces/research/lsmrum/data/gowalla_"
                    //          		FileReader fr = new FileReader("/Users/nujwoo/Documents/workspaces/research/lsmrum/data/berlin_"
                    //          		FileReader fr = new FileReader("/Users/nujwoo/Documents/workspaces/research/lsmrum/data/chicago19_"
                    FileReader fr = new FileReader(this.inputPrefix + totalNum + "_" + selfNum + ".dat");
                    //          		int[] indexes = {2, 4, 5};

                    BufferedReader buffer = new BufferedReader(fr, 16384);
                    String line = "";
                    while (true) {
                        try {
                            line = buffer.readLine();
                        }

                        // Catch block to handle exceptions
                        catch (IOException e) {

                            // Print the line where exception
                            // occurred
                            e.printStackTrace();
                        }

                        if (line == null)
                            break;

                        String items[] = line.split(",");
                        id = Integer.valueOf(items[indexes[0]]);
                        //	          			code = Integer.valueOf(items[indexes[1]]);
                        code = items[indexes[1]];

                        ITupleReference tuple = (ArrayTupleReference) TupleUtils.createTuple(fieldSerdes, code, id,
                                UpdateMemoConfig.ATOMIC_TS.getAndIncrement());

                        TestOperation op = null;
                        //	          			LOGGER.warn(line + " --> " + (UpdateMemoConfig.ATOMIC_TS.get() - 1));
                        if (!keys.containsKey(id)) {
                            keys.put(id, 1);
                            op = TestOperation.INSERT;
                        } else {
                            //	              			if(Math.random() < 0.01) {
                            ////	              			if(items[0] == "S") {
                            //	              				op = TestOperation.POINT_SEARCH;
                            ////	              				LOGGER.warn("SEARCH: " + code);
                            //	              			}
                            //	              			else {
                            op = TestOperation.UPDATE;
                            //	              			}

                        }

                        performOp(tuple, op);

                    }
                }
            } else if (this.testMethod == "LSMR") {
                ////////////////////////////////////////////////////////LSM   R-tree ./.............................
                ISerializerDeserializer[] fieldSerdes =
                        { DoubleSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
                                DoubleSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
                                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE };
                double x, y;
                int id;

                if (selfNum != -1) {
                    //FileReader fr = new FileReader("/Users/nujwoo/Documents/workspaces/research/lsmrum/data/gowalla_"
                    //FileReader fr = new FileReader("/Users/nujwoo/Documents/workspaces/research/lsmrum/data/berlin_"
                    //FileReader fr = new FileReader("/Users/nujwoo/Documents/workspaces/research/lsmrum/data/chicago19_"
                    FileReader fr = new FileReader(this.inputPrefix + totalNum + "_" + selfNum + ".dat");
                    //int[] indexes = {2, 4, 5};

                    BufferedReader buffer = new BufferedReader(fr, 16384);
                    String line = "";
                    while (true) {
                        try {
                            line = buffer.readLine();
                        }

                        // Catch block to handle exceptions
                        catch (IOException e) {
                            // Print the line where exception
                            // occurred
                            e.printStackTrace();
                        }

                        if (line == null)
                            break;

                        String items[] = line.split(",");
                        id = Integer.valueOf(items[indexes[0]]);
                        x = Double.valueOf(items[indexes[1]]);
                        y = Double.valueOf(items[indexes[2]]);

                        ITupleReference tuple = (ArrayTupleReference) TupleUtils.createTuple(fieldSerdes, x, y, x, y,
                                id, UpdateMemoConfig.ATOMIC_TS.getAndIncrement());

                        TestOperation op = null;
                        if (!keys.containsKey(id)) {
                            keys.put(id, 1);
                            op = TestOperation.INSERT;
                        } else {
                            op = TestOperation.UPDATE;
                        }

                        performOp(tuple, op);

                    }
                }
            }

            //////////////////////////////////////////////////////////      R-tree ./.............................        	

            //        	for (int i = 0; i < numBatches; i++) {
            //        		TupleBatch batch = dataGen.getBatch();
            //        		for (int j = 0; j < batch.size(); j++) {
            //        			
            //        			double x = 0.1 * (i+1) * (j+1);
            //        			double y = 0.1 * (i+1) * (j+1);
            //        			int id = i + j;
            //        			ITupleReference tuple = (ArrayTupleReference) TupleUtils.createTuple(fieldSerdes, 
            //            				x, y, 
            //            				x, y, 
            //            				id, UpdateMemoConfig.ATOMIC_TS.getAndIncrement());
            //        			
            //        			TestOperation op = null;
            //        			if(!keys.containsKey(id)) {
            //            			keys.put(id, 1);
            //            			op = TestOperation.INSERT;
            //            		}
            //            		else {
            //            			op = TestOperation.UPDATE;
            //            		}
            //        			
            //        			performOp(tuple, op);
            //        			
            //        		}
            //        		dataGen.releaseBatch(batch);
            //        	}

            //        	// original
            //            for (int i = 0; i < numBatches; i++) {
            //                TupleBatch batch = dataGen.getBatch();
            //                for (int j = 0; j < batch.size(); j++) {
            //                    TestOperation op = opSelector.getOp(rnd.nextInt());
            //                    ITupleReference tuple = batch.get(j);
            //                    
            ////                  //////////////////////////
            //                    ISerializerDeserializer[] fieldSerdes ={ 
            //                            DoubleSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
            //                            DoubleSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
            //                            IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE};
            //                    	
            ////                	String rec = TupleUtils.printTuple(tuple, fieldSerdes);
            ////                		if (LOGGER.isWarnEnabled()) {
            ////            			LOGGER.warn("--> " + rec);
            ////          			}
            ////                  //////////////////////////
            //                		
            //            		byte[] tupleData = tuple.getFieldData(0);
            //            		double x = ByteBuffer.wrap(tupleData, tuple.getFieldStart(0), tuple.getFieldLength(0)).getDouble();
            //            		double y = ByteBuffer.wrap(tupleData, tuple.getFieldStart(1), tuple.getFieldLength(1)).getDouble();
            //                	int id = ByteBuffer.wrap(tupleData, tuple.getFieldStart(4), tuple.getFieldLength(4)).getInt();
            //                	if(id < 0)
            //                		id = -id;
            //                	id = id % UpdateMemoConfig.NUM_UNIQUE_ID;
            //            		tuple = (ArrayTupleReference) TupleUtils.createTuple(fieldSerdes, 
            //            				x, y, 
            //            				x, y, 
            //            				id, UpdateMemoConfig.ATOMIC_TS.getAndIncrement());
            //            		
            //            		if(!keys.containsKey(id)) {
            //            			keys.put(id, 1);
            //            			op = TestOperation.INSERT;
            //            		}
            //            		else {
            //            			op = TestOperation.UPDATE;
            //            		}
            ////            		String rec = TupleUtils.printTuple(tuple, fieldSerdes);
            ////            		if (LOGGER.isWarnEnabled()) {
            ////            			LOGGER.warn("----> " + rec);
            ////            		}		
            //                    		
            //                    performOp(tuple, op);
            //                }
            //                dataGen.releaseBatch(batch);
            //            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void consumeCursorTuples(IIndexCursor cursor) throws HyracksDataException {
        try {
            ISerializerDeserializer[] fieldSerdes =
                    { new UTF8StringSerializerDeserializer(), IntegerSerializerDeserializer.INSTANCE,
                            //			{ IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE,
                            IntegerSerializerDeserializer.INSTANCE };
            //	    	LOGGER.warn("?------------------");
            try {
                while (cursor.hasNext()) {
                    cursor.next();
                    ITupleReference frameTuple = cursor.getTuple();
                    String rec = TupleUtils.printTuple(frameTuple, fieldSerdes);
                    //		            LOGGER.warn(rec);
                }
                //		        LOGGER.warn("------------------");
            } finally {
                cursor.close();
            }
        } finally {
            cursor.destroy();
        }
    }
}
