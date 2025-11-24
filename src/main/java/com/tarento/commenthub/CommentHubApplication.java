package com.tarento.commenthub;


import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.tarento.commenthub",
        "org.igot.common"
})
public class CommentHubApplication {

  public static void main(String[] args) {
    SpringApplication.run(CommentHubApplication.class, args);
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate(getClientHttpRequestFactory());
  }

  private ClientHttpRequestFactory getClientHttpRequestFactory() {
    int timeout = 45000;
    RequestConfig config = RequestConfig.custom()
        .setResponseTimeout(Timeout.ofMilliseconds(timeout))
        .build();
    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
    cm.setMaxTotal(2000);
    cm.setDefaultMaxPerRoute(500);
    CloseableHttpClient client = HttpClients.custom()
        .setDefaultRequestConfig(config)
        .setConnectionManager(cm)
        .build();
    return new HttpComponentsClientHttpRequestFactory(client);
  }

}