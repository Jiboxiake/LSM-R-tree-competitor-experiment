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
package org.apache.asterix.app.message;

import java.util.ArrayList;

import org.apache.asterix.common.dataflow.ICcApplicationContext;
import org.apache.asterix.common.messaging.api.ICCMessageBroker;
import org.apache.asterix.common.messaging.api.ICcAddressedMessage;
import org.apache.asterix.common.messaging.api.INcResponse;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.hyracks.api.exceptions.HyracksDataException;

public class UdfResponseMessage implements ICcAddressedMessage, INcResponse {

    private static final long serialVersionUID = -4520773141458281271L;
    private final long reqId;
    private final Exception failure;

    public UdfResponseMessage(long reqId, Exception failure) {
        this.reqId = reqId;
        this.failure = failure;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setResult(MutablePair<ICCMessageBroker.ResponseState, Object> result) {
        ICCMessageBroker.ResponseState responseState = result.getLeft();
        if (failure != null) {
            result.setLeft(ICCMessageBroker.ResponseState.FAILURE);
            result.setRight(failure);
            return;
        }
        switch (responseState) {
            case UNINITIALIZED:
                // First to arrive
                result.setRight(new ArrayList<String>());
                // No failure, change state to success
                result.setLeft(ICCMessageBroker.ResponseState.SUCCESS);
                // Fallthrough
            case SUCCESS:
                break;
            default:
                break;

        }
    }

    @Override
    public void handle(ICcApplicationContext appCtx) throws HyracksDataException, InterruptedException {
        ICCMessageBroker broker = (ICCMessageBroker) appCtx.getServiceContext().getMessageBroker();
        broker.respond(reqId, this);
    }
}
