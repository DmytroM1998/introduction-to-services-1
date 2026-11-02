package com.epam.resource_processor;

import com.epam.resource_processor.dto.SongDto;
import com.epam.resource_processor.service.CommunicationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
		"eureka.client.enabled=false",
		"spring.cloud.stream.bindings.resourceMessageListener-in-0.destination=resource.uploaded"
})
@Import(TestChannelBinderConfiguration.class)
class ResourceProcessorApplicationTests {

	@Autowired
	InputDestination input;

	@MockitoBean
	CommunicationService communicationService;

	@Test
	void contextLoads_testBinderConfigured_exposesInputDestination() {
		assertThat(input).isNotNull();
	}

	@Test
	void uploadedJsonResourceId_fetchesParsesAndSubmitsMetadata() throws IOException {
		try (InputStream sample = getClass().getResourceAsStream("/valid-sample.mp3")) {
			assertThat(sample).isNotNull();
			when(communicationService.requestResourceBinary(42L)).thenReturn(sample.readAllBytes());
		}

		input.send(MessageBuilder.withPayload("42".getBytes(StandardCharsets.UTF_8))
				.setHeader("contentType", "application/json")
				.build(), "resource.uploaded");

		ArgumentCaptor<SongDto> song = ArgumentCaptor.forClass(SongDto.class);
		var order = inOrder(communicationService);
		order.verify(communicationService).requestResourceBinary(42L);
		order.verify(communicationService).saveSongMetadata(eq(42L), song.capture());
		order.verifyNoMoreInteractions();
		assertThat(song.getValue())
				.extracting(SongDto::getId, SongDto::getName, SongDto::getArtist,
						SongDto::getAlbum, SongDto::getDuration, SongDto::getYear)
				.containsExactly(42L, "Test Title", "Test Artist", "Test Album", "00:07", "2025");
	}

}
