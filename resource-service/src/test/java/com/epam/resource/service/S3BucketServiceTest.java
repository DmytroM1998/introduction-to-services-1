package com.epam.resource.service;

import com.epam.resource.dto.StorageDetails;
import com.epam.resource.entity.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
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
    }

    @Test
    void saveFileInS3StorageBucket_binaryInput_uploadsBytesAndContentType() throws IOException {
        byte[] audio = {0x49, 0x44, 0x33, 0, (byte) 0xff};

        service.saveFileInS3StorageBucket("upload-tests", "resources/test.mp3", audio);

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
        StorageDetails storage = storage(StorageType.STAGING, "upload-tests", "resources/staging/");
        String first = service.generateMp3ResourceObjectKey(storage.getPath());
        String second = service.generateMp3ResourceObjectKey(storage.getPath());

        assertThat(first).matches("resources/staging/[0-9a-f-]{36}\\.mp3").isNotEqualTo(second);
        assertThat(service.getResourceStorageBucketLocation(storage, first))
                .isEqualTo("s3://upload-tests/" + first);
    }

    @Test
    void deleteObjectOnRollback_deleteFails_suppressesCleanupError() {
        var original = new IllegalStateException("upload failed");
        var cleanup = new IllegalStateException("S3 delete failed");
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(cleanup);

        service.deleteObjectOnRollback("upload-tests", "resources/test.mp3", original);

        verify(s3).deleteObject(DeleteObjectRequest.builder()
                .bucket("upload-tests").key("resources/test.mp3").build());
        assertThat(original.getSuppressed()).containsExactly(cleanup);
    }

    @Test
    void copyObjectToStorage_copiesObjectAcrossBucketsAndPreservesRelativeKey() {
        StorageDetails staging = storage(StorageType.STAGING, "staging-bucket", "incoming/staging/");
        StorageDetails permanent = storage(StorageType.PERMANENT, "permanent-bucket", "audio/permanent/");

        String location = service.copyObjectToStorage(
                "s3://staging-bucket/incoming/staging/folder/file.mp3", staging, permanent);

        ArgumentCaptor<CopyObjectRequest> request = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3).copyObject(request.capture());
        assertThat(request.getValue().copySource()).isEqualTo("staging-bucket/incoming/staging/folder/file.mp3");
        assertThat(request.getValue().bucket()).isEqualTo("permanent-bucket");
        assertThat(request.getValue().key()).isEqualTo("audio/permanent/folder/file.mp3");
        assertThat(location).isEqualTo("s3://permanent-bucket/audio/permanent/folder/file.mp3");
    }

    private static StorageDetails storage(StorageType type, String bucket, String path) {
        StorageDetails storage = new StorageDetails();
        storage.setStorageType(type);
        storage.setBucket(bucket);
        storage.setPath(path);
        return storage;
    }
}
