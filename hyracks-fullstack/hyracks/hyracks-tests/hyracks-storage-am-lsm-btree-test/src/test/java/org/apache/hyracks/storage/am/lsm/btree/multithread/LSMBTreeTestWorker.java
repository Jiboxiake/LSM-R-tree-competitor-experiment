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

package org.apache.hyracks.storage.am.lsm.btree.multithread;

import org.apache.hyracks.api.exceptions.ErrorCode;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import org.apache.hyracks.dataflow.common.comm.io.ArrayTupleReference;
import org.apache.hyracks.dataflow.common.data.accessors.ITupleReference;
import org.apache.hyracks.storage.am.btree.impls.RangePredicate;
import org.apache.hyracks.storage.am.common.AbstractIndexTestWorker;
import org.apache.hyracks.storage.am.common.TestOperationSelector;
import org.apache.hyracks.storage.am.common.TestOperationSelector.TestOperation;
import org.apache.hyracks.storage.am.common.datagen.DataGenThread;
import org.apache.hyracks.storage.am.lsm.btree.impls.LSMBTree;
import org.apache.hyracks.storage.am.lsm.btree.impls.LSMBTreeOpContext;
import org.apache.hyracks.storage.am.lsm.common.impls.LSMTreeIndexAccessor;
import org.apache.hyracks.storage.common.IIndex;
import org.apache.hyracks.storage.common.IIndexCursor;
import org.apache.hyracks.storage.common.MultiComparator;

public class LSMBTreeTestWorker extends AbstractIndexTestWorker {
    private final LSMBTree lsmBTree;
    private final int numKeyFields;
    private final ArrayTupleBuilder deleteTb;
    private final ArrayTupleReference deleteTuple = new ArrayTupleReference();

    public LSMBTreeTestWorker(DataGenThread dataGen, TestOperationSelector opSelector, IIndex index, int numBatches)
            throws HyracksDataException {
        super(dataGen, opSelector, index, numBatches);
        lsmBTree = (LSMBTree) index;
        numKeyFields = lsmBTree.getComparatorFactories().length;
        deleteTb = new ArrayTupleBuilder(numKeyFields);
    }

    public LSMBTreeTestWorker(DataGenThread dataGen, TestOperationSelector opSelector, IIndex index, int numBatches,
            int selfNum, int totalNum, String inputPrefix, int[] indexes, String method) throws HyracksDataException {
        super(dataGen, opSelector, index, numBatches, selfNum, totalNum, inputPrefix, indexes, method);
        lsmBTree = (LSMBTree) index;
        numKeyFields = lsmBTree.getComparatorFactories().length;
        deleteTb = new ArrayTupleBuilder(numKeyFields);
    }

    @Override
    public void performOp(ITupleReference tuple, TestOperation op) throws HyracksDataException {
        LSMTreeIndexAccessor accessor = (LSMTreeIndexAccessor) indexAccessor;
        IIndexCursor searchCursor = accessor.createSearchCursor(false);
        LSMBTreeOpContext concreteCtx = (LSMBTreeOpContext) accessor.getCtx();
        MultiComparator cmp = concreteCtx.getCmp();
        RangePredicate rangePred = new RangePredicate(tuple, tuple, true, true, cmp, cmp);

        switch (op) {
            case INSERT:
                try {
                    accessor.insert(tuple);
                } catch (HyracksDataException e) {
                    if (e.getErrorCode() != ErrorCode.DUPLICATE_KEY) {
                        // Ignore duplicate keys, since we get random tuples.
                        throw e;
                    }
                }
                break;

            case DELETE:
                // Create a tuple reference with only key fields.
                deleteTb.reset();
                for (int i = 0; i < numKeyFields; i++) {
                    deleteTb.addField(tuple.getFieldData(i), tuple.getFieldStart(i), tuple.getFieldLength(i));
                }
                deleteTuple.reset(deleteTb.getFieldEndOffsets(), deleteTb.getByteArray());
                try {
                    accessor.delete(deleteTuple);
                } catch (HyracksDataException e) {
                    // Ignore non-existant keys, since we get random tuples.
                    if (e.getErrorCode() != ErrorCode.UPDATE_OR_DELETE_NON_EXISTENT_KEY) {
                        throw e;
                    }
                }
                break;

            case UPDATE:
                try {
                    accessor.update(tuple);
                } catch (HyracksDataException e) {
                    if (e.getErrorCode() != ErrorCode.UPDATE_OR_DELETE_NON_EXISTENT_KEY
                            && e.getErrorCode() != ErrorCode.INDEX_NOT_UPDATABLE) {
                        // Ignore non-existant keys, since we get random tuples.
                        // Ignore not updateable exception due to numKeys == numFields.
                        throw e;
                    }
                }
                break;

            case POINT_SEARCH:
                //                searchCursor.close();
                rangePred.setLowKey(tuple, true);
                rangePred.setHighKey(tuple, true);
                accessor.search(searchCursor, rangePred);
                consumeCursorTuples(searchCursor);
                break;

            case SCAN:
                searchCursor.close();
                rangePred.setLowKey(null, true);
                rangePred.setHighKey(null, true);
                accessor.search(searchCursor, rangePred);
                consumeCursorTuples(searchCursor);
                break;
            case MERGE:
                accessor.scheduleMerge(lsmBTree.getDiskComponents());
                break;

            default:
                throw new HyracksDataException("Op " + op.toString() + " not supported.");
        }
    }
}
