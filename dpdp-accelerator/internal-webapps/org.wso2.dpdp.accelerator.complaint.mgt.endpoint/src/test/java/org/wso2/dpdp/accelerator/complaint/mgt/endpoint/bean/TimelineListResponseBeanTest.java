package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TimelineListResponseBeanTest {

    @Test
    void allArgsConstructorPopulatesEveryField() {
        List<ComplaintTimelineEntryResponseBean> data = List.of(new ComplaintTimelineEntryResponseBean());
        PageMetadataBean metadata = new PageMetadataBean(1, 0, 1, 20);

        TimelineListResponseBean bean = new TimelineListResponseBean(data, metadata);

        assertSame(data, bean.getData());
        assertSame(metadata, bean.getMetadata());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        TimelineListResponseBean bean = new TimelineListResponseBean();
        List<ComplaintTimelineEntryResponseBean> data = List.of();
        PageMetadataBean metadata = new PageMetadataBean();
        bean.setData(data);
        bean.setMetadata(metadata);

        assertEquals(data, bean.getData());
        assertSame(metadata, bean.getMetadata());
    }
}
