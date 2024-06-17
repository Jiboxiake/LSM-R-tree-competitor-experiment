package org.apache.hyracks.storage.am.lsm.rtree;

import java.util.Random;

import org.apache.hyracks.api.dataflow.value.ISerializerDeserializer;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.storage.am.common.api.IPrimitiveValueProviderFactory;
import org.apache.hyracks.storage.am.config.AccessMethodTestsConfig;
import org.apache.hyracks.storage.am.lsm.rtree.util.LSMRTreeTestContext;
import org.apache.hyracks.storage.am.lsm.rtree.util.LSMRTreeTestHarness;
import org.apache.hyracks.storage.am.rtree.AbstractRTreeTestContext;
import org.apache.hyracks.storage.am.rtree.AbstractRTreeUpdateTest;
import org.apache.hyracks.storage.am.rtree.frames.RTreePolicyType;
import org.junit.After;
import org.junit.Before;

@SuppressWarnings("rawtypes")
public class LSMRTreeUpdateTest extends AbstractRTreeUpdateTest {

    private final LSMRTreeTestHarness harness = new LSMRTreeTestHarness();

    public LSMRTreeUpdateTest() {
        super(AccessMethodTestsConfig.LSM_RTREE_TEST_RSTAR_POLICY);
    }

    @Before
    public void setUp() throws HyracksDataException {
        harness.setUp();
    }

    @After
    public void tearDown() throws HyracksDataException {
        harness.tearDown();
    }

    @Override
    protected AbstractRTreeTestContext createTestContext(ISerializerDeserializer[] fieldSerdes,
            IPrimitiveValueProviderFactory[] valueProviderFactories, int numKeys, RTreePolicyType rtreePolicyType)
            throws Exception {
        return LSMRTreeTestContext.create(harness.getIOManager(), harness.getVirtualBufferCaches(),
                harness.getFileReference(), harness.getDiskBufferCache(), fieldSerdes, valueProviderFactories, numKeys,
                rtreePolicyType, harness.getBoomFilterFalsePositiveRate(), harness.getMergePolicy(),
                harness.getOperationTracker(), harness.getIOScheduler(), harness.getIOOperationCallbackFactory(),
                harness.getPageWriteCallbackFactory(), harness.getMetadataPageManagerFactory());
    }

    @Override
    protected Random getRandom() {
        return harness.getRandom();
    }
}
