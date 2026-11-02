package com.epam.resource.component;

import com.epam.resource.repository.ResourceRepository;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.S3Client;

@CucumberContextConfiguration
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "eureka.client.enabled=false",
        "resource.storage.bucket=resources",
        "resource.rabbitmq.exchange=resource.uploaded",
        "song.service.ref.name=song-service"
})
@AutoConfigureMockMvc
public class CucumberSpringConfiguration {

    @MockitoBean
    ResourceRepository resourceRepository;

    @MockitoBean
    S3Client s3Client;

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    @MockitoBean
    RestTemplate restTemplate;
}
