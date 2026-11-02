package com.epam.resource.service;

import com.epam.resource.config.RetryConfiguration;
import com.epam.resource.entity.ResourceEntity;
import com.epam.resource.exception.ErrorCodeException;
import com.epam.resource.repository.ResourceRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourcesServiceTest {

    private static final String KEY = "resources/upload.mp3";
    private static final String LOCATION = "s3://resources/" + KEY;
    private static final byte[] AUDIO = "ID3 test bytes".getBytes(StandardCharsets.UTF_8);

    @Mock
    RabbitMQService publisher;
    @Mock
    ResourceRepository repository;
    @Mock
    RestTemplate songClient;
    @Mock
    S3BucketService storage;
    @Mock
    HttpServletRequest unreadableRequest;

    private ResourcesService service;

    @BeforeEach
    void setUp() {
        var retry = new RetryConfiguration().retryTemplate();
        retry.setBackOffPolicy(new NoBackOffPolicy());
        service = new ResourcesService(publisher, repository, songClient, retry, storage);
    }

    @Test
    void validUploadWithAvailableDependencies_returnsIdAfterOrderedWrites() {
        ResourceEntity saved = givenStorageAndSavedResource();

        assertThat(service.processAndSaveResource(request())).isEqualTo(Map.of("id", 42L));

        var order = inOrder(storage, repository, publisher);
        order.verify(storage).generateMp3ResourceObjectKey();
        order.verify(storage).getResourceStorageBucketLocation(KEY);
        order.verify(storage).saveFileInS3StorageBucket(KEY, AUDIO);
        ArgumentCaptor<ResourceEntity> record = ArgumentCaptor.forClass(ResourceEntity.class);
        order.verify(repository).save(record.capture());
        assertThat(record.getValue().getLocation()).isEqualTo(LOCATION);
        assertThat(record.getValue().getId()).isNull();
        order.verify(publisher).sendResourceId(saved.getId());
        order.verifyNoMoreInteractions();
        verifyNoInteractions(songClient);
    }

    @Test
    void processAndSaveResource_unreadableRequest_throwsServerError() throws IOException {
        when(unreadableRequest.getInputStream()).thenThrow(new IOException("request disconnected"));

        assertThatThrownBy(() -> service.processAndSaveResource(unreadableRequest))
                .isInstanceOfSatisfying(ErrorCodeException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo(500);
                    assertThat(failure.getMessage()).isEqualTo("Something went wrong");
                });

        verifyNoInteractions(storage, repository, publisher, songClient);
    }

    @Test
    void processAndSaveResource_storageFailure_cleansUpWithoutSaving() {
        givenStorageLocation();
        RuntimeException failure = new IllegalStateException("storage unavailable");
        doThrow(failure).when(storage).saveFileInS3StorageBucket(KEY, AUDIO);

        assertThatThrownBy(() -> service.processAndSaveResource(request())).isSameAs(failure);

        verify(storage).deleteObjectOnRollback(KEY, failure);
        verifyNoInteractions(repository, publisher, songClient);
    }

    @Test
    void processAndSaveResource_databaseFailure_cleansUpWithoutPublishing() {
        givenStorageLocation();
        var failure = new DataIntegrityViolationException("save rejected");
        when(repository.save(any(ResourceEntity.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.processAndSaveResource(request())).isSameAs(failure);

        verify(repository).save(any(ResourceEntity.class));
        verify(repository, never()).delete(any(ResourceEntity.class));
        verify(storage).deleteObjectOnRollback(KEY, failure);
        verifyNoInteractions(publisher, songClient);
    }

    @Test
    void processAndSaveResource_transientDbFailure_retriesSaveOnly() {
        givenStorageLocation();
        ResourceEntity saved = savedResource();
        when(repository.save(any(ResourceEntity.class)))
                .thenThrow(new TransientDataAccessResourceException("temporary database failure"))
                .thenReturn(saved);

        assertThat(service.processAndSaveResource(request())).containsEntry("id", 42L);

        verify(repository, times(2)).save(any(ResourceEntity.class));
        verify(storage).saveFileInS3StorageBucket(KEY, AUDIO);
        verify(publisher).sendResourceId(42L);
        verify(storage, never()).deleteObjectOnRollback(any(), any());
    }

    @Test
    void processAndSaveResource_transientBrokerFailure_retriesPublishOnly() {
        givenStorageAndSavedResource();
        doThrow(new AmqpTimeoutException("temporary broker failure")).doNothing()
                .when(publisher).sendResourceId(42L);

        assertThat(service.processAndSaveResource(request())).containsEntry("id", 42L);

        verify(storage).saveFileInS3StorageBucket(KEY, AUDIO);
        verify(repository).save(any(ResourceEntity.class));
        verify(publisher, times(2)).sendResourceId(42L);
        verify(storage, never()).deleteObjectOnRollback(any(), any());
    }

    @Test
    void brokerRetriesExhaustedAfterPersistence_removesRecordThenObject() {
        ResourceEntity saved = givenStorageAndSavedResource();
        var failure = new AmqpTimeoutException("broker still unavailable");
        doThrow(failure).when(publisher).sendResourceId(42L);

        assertThatThrownBy(() -> service.processAndSaveResource(request())).isSameAs(failure);

        verify(publisher, times(3)).sendResourceId(42L);
        var cleanup = inOrder(repository, storage);
        cleanup.verify(storage).generateMp3ResourceObjectKey();
        cleanup.verify(storage).getResourceStorageBucketLocation(KEY);
        cleanup.verify(storage).saveFileInS3StorageBucket(KEY, AUDIO);
        cleanup.verify(repository).save(any(ResourceEntity.class));
        cleanup.verify(repository).delete(saved);
        cleanup.verify(storage).deleteObjectOnRollback(KEY, failure);
        verifyNoInteractions(songClient);
    }

    @Test
    void nonTransientPublishAndDbCleanupFail_preservesErrorsAndCleansS3() {
        ResourceEntity saved = givenStorageAndSavedResource();
        var failure = new AmqpException("invalid publisher configuration");
        var cleanupFailure = new DataIntegrityViolationException("delete rejected");
        doThrow(failure).when(publisher).sendResourceId(42L);
        doThrow(cleanupFailure).when(repository).delete(saved);

        assertThatThrownBy(() -> service.processAndSaveResource(request()))
                .isSameAs(failure).hasSuppressedException(cleanupFailure);

        verify(publisher).sendResourceId(42L);
        verify(repository).delete(saved);
        verify(storage).deleteObjectOnRollback(KEY, failure);
        verifyNoMoreInteractions(publisher);
    }

    private void givenStorageLocation() {
        when(storage.generateMp3ResourceObjectKey()).thenReturn(KEY);
        when(storage.getResourceStorageBucketLocation(KEY)).thenReturn(LOCATION);
    }

    private ResourceEntity givenStorageAndSavedResource() {
        givenStorageLocation();
        ResourceEntity saved = savedResource();
        when(repository.save(any(ResourceEntity.class))).thenReturn(saved);
        return saved;
    }

    private ResourceEntity savedResource() {
        ResourceEntity saved = new ResourceEntity(LOCATION);
        saved.setId(42L);
        return saved;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(AUDIO);
        request.setContentType("audio/mpeg");
        return request;
    }
}
