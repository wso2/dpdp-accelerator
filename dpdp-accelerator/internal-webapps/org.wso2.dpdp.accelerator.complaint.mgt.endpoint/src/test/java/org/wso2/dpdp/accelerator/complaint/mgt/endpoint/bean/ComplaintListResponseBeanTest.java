package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ComplaintListResponseBeanTest {

    @Test
    void allArgsConstructorPopulatesEveryField() {
        List<ComplaintRecordBean> data = List.of(new ComplaintRecordBean());
        PageMetadataBean metadata = new PageMetadataBean(1, 0, 1, 10);

        ComplaintListResponseBean bean = new ComplaintListResponseBean(data, metadata);

        assertSame(data, bean.getData());
        assertSame(metadata, bean.getMetadata());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintListResponseBean bean = new ComplaintListResponseBean();
        List<ComplaintRecordBean> data = List.of();
        PageMetadataBean metadata = new PageMetadataBean();
        bean.setData(data);
        bean.setMetadata(metadata);

        assertEquals(data, bean.getData());
        assertSame(metadata, bean.getMetadata());
    }
}
