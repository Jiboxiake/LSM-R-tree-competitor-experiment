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

package org.apache.hyracks.storage.am.lsm.invertedindex.search;

import java.util.ArrayList;

import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.exceptions.ErrorCode;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.storage.am.common.api.IIndexOperationContext;
import org.apache.hyracks.storage.am.lsm.invertedindex.api.IInPlaceInvertedIndex;
import org.apache.hyracks.storage.am.lsm.invertedindex.api.IInvertedIndexSearchModifier;
import org.apache.hyracks.storage.am.lsm.invertedindex.api.InvertedListCursor;
import org.apache.hyracks.storage.common.IIndexCursor;

/**
 * Conducts T-Occurrence searches on inverted lists.
 */
public class TOccurrenceSearcher extends AbstractTOccurrenceSearcher {

    protected final ArrayList<InvertedListCursor> invListCursors = new ArrayList<>();

    public TOccurrenceSearcher(IInPlaceInvertedIndex invIndex, IHyracksTaskContext ctx) throws HyracksDataException {
        super(invIndex, ctx);
    }

    @Override
    public void search(IIndexCursor resultCursor, InvertedIndexSearchPredicate searchPred, IIndexOperationContext ictx)
            throws HyracksDataException {
        prepareSearch();
        tokenizeQuery(searchPred);
        int numQueryTokens = queryTokenAppender.getTupleCount();

        invListCursors.clear();
        invListCursorCache.reset();
        for (int i = 0; i < numQueryTokens; i++) {
            searchKey.reset(queryTokenAppender, i);
            InvertedListCursor invListCursor = invListCursorCache.getNext();
            invIndex.openInvertedListCursor(invListCursor, searchKey, ictx);
            invListCursors.add(invListCursor);
        }

        IInvertedIndexSearchModifier searchModifier = searchPred.getSearchModifier();
        occurrenceThreshold = searchModifier.getOccurrenceThreshold(numQueryTokens);
        if (occurrenceThreshold <= 0) {
            throw HyracksDataException.create(ErrorCode.OCCURRENCE_THRESHOLD_PANIC_EXCEPTION);
        }
        int numPrefixLists = searchModifier.getNumPrefixLists(occurrenceThreshold, invListCursors.size());

        // For a single inverted list case, we don't need to call merge() method since elements from a single inverted
        // list cursor will be the final answer.
        if (numQueryTokens == 1 && occurrenceThreshold == 1) {
            singleInvListCursor = invListCursors.get(0);
            singleInvListCursor.prepareLoadPages();
            singleInvListCursor.loadPages();
            isSingleInvertedList = true;
            isFinishedSearch = true;
        } else {
            finalSearchResult.reset();
            isFinishedSearch =
                    invListMerger.merge(invListCursors, occurrenceThreshold, numPrefixLists, finalSearchResult);
            searchResultBuffer = finalSearchResult.getNextFrame();
            searchResultTupleIndex = 0;
            searchResultFta.reset(searchResultBuffer);
        }

        if (isFinishedSearch) {
            invListMerger.close();
            finalSearchResult.finalizeWrite();
        }
        // Some or all output was generated by the merger. Let the result cursor fetch the output.
        resultCursor.open(null, searchPred);
    }

    /**
     * Continues a search process if it was paused because the output buffer (one frame) of the final result was full.
     * This method should not be called for a single inverted list case since there cannot be multiple inverted list
     * cursors for a single keyword.
     *
     * @return true only if all processing for the final list is done.
     *         false otherwise.
     * @throws HyracksDataException
     */
    @Override
    public boolean continueSearch() throws HyracksDataException {
        if (isFinishedSearch) {
            return true;
        }
        isFinishedSearch = invListMerger.continueMerge();
        searchResultBuffer = finalSearchResult.getNextFrame();
        searchResultTupleIndex = 0;
        searchResultFta.reset(searchResultBuffer);
        if (isFinishedSearch) {
            invListMerger.close();
            finalSearchResult.finalizeWrite();
        }
        return isFinishedSearch;
    }

}