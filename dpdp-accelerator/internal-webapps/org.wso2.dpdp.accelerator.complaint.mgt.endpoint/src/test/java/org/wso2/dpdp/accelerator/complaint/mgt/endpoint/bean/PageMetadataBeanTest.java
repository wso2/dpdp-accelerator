package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageMetadataBeanTest {

    @Test
    void allArgsConstructorPopulatesEveryField() {
        PageMetadataBean bean = new PageMetadataBean(42, 10, 5, 20);

        assertEquals(42, bean.getTotal());
        assertEquals(10, bean.getOffset());
        assertEquals(5, bean.getCount());
        assertEquals(20, bean.getLimit());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        PageMetadataBean bean = new PageMetadataBean();
        bean.setTotal(1);
        bean.setOffset(2);
        bean.setCount(3);
        bean.setLimit(4);

        assertEquals(1, bean.getTotal());
        assertEquals(2, bean.getOffset());
        assertEquals(3, bean.getCount());
        assertEquals(4, bean.getLimit());
    }
}
