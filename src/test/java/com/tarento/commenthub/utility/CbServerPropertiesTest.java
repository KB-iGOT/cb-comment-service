package com.tarento.commenthub.utility;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CbServerProperties.class)
@TestPropertySource(properties = {"content-service-host=http://localhost:8080", "content-read-endpoint=/api/content/v1/read", "content-read-endpoint-fields=title,description", "default.content.properties=title,description,status", "notification.api.url=http://localhost:8081/notify"})
class CbServerPropertiesTest {

    @Autowired
    private CbServerProperties cbServerProperties;

    @Test
    void testAllPropertiesLoadedCorrectly() {
        assertEquals("http://localhost:8080", cbServerProperties.getContentHost());
        assertEquals("/api/content/v1/read", cbServerProperties.getContentReadEndPoint());
        assertEquals("title,description", cbServerProperties.getContentReadEndPointFields());
        assertEquals("title,description,status", cbServerProperties.getDefaultContentProperties());
        assertEquals("http://localhost:8081/notify", cbServerProperties.getNotificationApiUrl());
    }

    @Test
    void testSetters() {
        CbServerProperties props = new CbServerProperties();
        props.setContentHost("http://example.com");
        props.setContentReadEndPoint("/read");
        props.setContentReadEndPointFields("field1,field2");
        props.setDefaultContentProperties("prop1,prop2");
        props.setNotificationApiUrl("http://notify.example.com");

        assertEquals("http://example.com", props.getContentHost());
        assertEquals("/read", props.getContentReadEndPoint());
        assertEquals("field1,field2", props.getContentReadEndPointFields());
        assertEquals("prop1,prop2", props.getDefaultContentProperties());
        assertEquals("http://notify.example.com", props.getNotificationApiUrl());
    }
}
