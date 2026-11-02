package com.epam.resource.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3BucketServiceTest {

    @Mock
    S3Client s3;

    private S3BucketService service;

    @BeforeEach
    void setUp() {
        service = new S3BucketService(s3);
        ReflectionTestUtils.setField(service, "storageBucket", "upload-tests");
    }

    @Test
    void saveFileInS3StorageBucket_binaryInput_uploadsBytesAndContentType() throws IOException {
        byte[] audio = {0x49, 0x44, 0x33, 0, (byte) 0xff};

        service.saveFileInS3StorageBucket("resources/test.mp3", audio);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("upload-tests");
        assertThat(request.getValue().key()).isEqualTo("resources/test.mp3");
        assertThat(request.getValue().contentType()).isEqualTo("audio/mpeg");
        try (InputStream stream = body.getValue().contentStreamProvider().newStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(audio);
        }
    }

    @Test
    void generatedMp3Keys_haveUniqueNamesAndMatchingStorageLocations() {
        String first = service.generateMp3ResourceObjectKey();
        String second = service.generateMp3ResourceObjectKey();

        assertThat(first).matches("resources/[0-9a-f-]{36}\\.mp3").isNotEqualTo(second);
        assertThat(service.getResourceStorageBucketLocation(first)).isEqualTo("s3://upload-tests/" + first);
    }

    @Test
    void deleteObjectOnRollback_deleteFails_suppressesCleanupError() {
        var original = new IllegalStateException("upload failed");
        var cleanup = new IllegalStateException("S3 delete failed");
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(cleanup);

        service.deleteObjectOnRollback("resources/test.mp3", original);

        verify(s3).deleteObject(DeleteObjectRequest.builder()
                .bucket("upload-tests").key("resources/test.mp3").build());
        assertThat(original.getSuppressed()).containsExactly(cleanup);
    }
}
