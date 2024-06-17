package org.apache.hyracks.storage.am.rtree;

import org.apache.hyracks.api.dataflow.value.ISerializerDeserializer;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.common.data.accessors.ITupleReference;
import org.apache.hyracks.dataflow.common.data.marshalling.DoubleSerializerDeserializer;
import org.apache.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import org.apache.hyracks.storage.am.common.api.IPrimitiveValueProviderFactory;
import org.apache.hyracks.storage.am.config.AccessMethodTestsConfig;
import org.apache.hyracks.storage.am.rtree.frames.RTreePolicyType;

public abstract class AbstractRTreeUpdateTest extends AbstractRTreeTestDriver {

    private final RTreeTestUtils rTreeTestUtils;

    private static final int numInsertRounds = AccessMethodTestsConfig.RTREE_NUM_INSERT_ROUNDS;
    private static final int numDeleteRounds = AccessMethodTestsConfig.RTREE_NUM_DELETE_ROUNDS;

    public AbstractRTreeUpdateTest(boolean testRstarPolicy) {
        super(testRstarPolicy);
        this.rTreeTestUtils = new RTreeTestUtils();
    }

    @Override
    protected void runTest(ISerializerDeserializer[] fieldSerdes,
            IPrimitiveValueProviderFactory[] valueProviderFactories, int numKeys, ITupleReference key,
            RTreePolicyType rtreePolicyType) throws Exception {
        AbstractRTreeTestContext ctx = createTestContext(fieldSerdes, valueProviderFactories, numKeys, rtreePolicyType);
        ctx.getIndex().create();
        ctx.getIndex().activate();
        long totalTime = 0L;
        for (int i = 0; i < numInsertRounds; i++) {
            // We assume all fieldSerdes are of the same type. Check the first
            // one to determine which field types to generate.
            if (fieldSerdes[0] instanceof IntegerSerializerDeserializer) {
                rTreeTestUtils.insertIntTuples(ctx, numTuplesToInsert, getRandom());
            } else if (fieldSerdes[0] instanceof DoubleSerializerDeserializer) {
                rTreeTestUtils.insertDoubleTuples(ctx, numTuplesToInsert, getRandom());
            }
            int numTuplesPerDeleteRound =
                    (int) Math.ceil((float) ctx.getCheckTuples().size() / (float) numDeleteRounds);

            long start = System.nanoTime();

            for (int j = 0; j < numDeleteRounds; j++) {
                //            	LOGGER.warn("????????????????????????????????????????");
                rTreeTestUtils.updateDoubleTuples(ctx, numTuplesPerDeleteRound, getRandom());
                //                rTreeTestUtils.updateDoubleTuples(ctx, numTuplesPerDeleteRound, getRandom());
                //                rTreeTestUtils.checkScan(ctx);
                //                rTreeTestUtils.checkDiskOrderScan(ctx);
                //                rTreeTestUtils.checkRangeSearch(ctx, key);
                afterDeleteRound(ctx);
            }
            long end = System.nanoTime();
            LOGGER.error((end - start) / 1000 + " us");
            totalTime += end - start;
            afterInsertRound(ctx);
        }
        LOGGER.error("total: " + (totalTime / 1000) + " us");
        ctx.getIndex().deactivate();
        ctx.getIndex().destroy();
    }

    protected void afterInsertRound(AbstractRTreeTestContext ctx) throws HyracksDataException {

    }

    protected void afterDeleteRound(AbstractRTreeTestContext ctx) throws HyracksDataException {

    }

    @Override
    protected String getTestOpName() {
        return "Update";
    }
}
