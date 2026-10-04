package cn.superhuang.data.scalpel.business.filedataset.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3FileObjectStorageTest {
    private final S3Client client=mock(S3Client.class);
    private final S3FileObjectStorage storage=new S3FileObjectStorage(client,"private-bucket","files");

    @Test
    void uploadBudgetIsPerRequestAndDoesNotChangeOrdinaryUploads() {
        when(client.putObject(any(PutObjectRequest.class),any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        var content=new ByteArrayInputStream(new byte[]{1,2,3});
        Duration timeout=Duration.ofSeconds(7);
        assertEquals("etag",storage.store("preview/data",content,3,"application/octet-stream",timeout).eTag());
        storage.store("regular-upload",new ByteArrayInputStream(new byte[0]),0,null);

        var requests=ArgumentCaptor.forClass(PutObjectRequest.class);
        var bodies=ArgumentCaptor.forClass(RequestBody.class);
        verify(client,times(2)).putObject(requests.capture(),bodies.capture());
        var timed=requests.getAllValues().getFirst();
        assertEquals("private-bucket",timed.bucket());assertEquals("files/preview/data",timed.key());
        assertEquals("application/octet-stream",timed.contentType());
        assertEquals(3L,bodies.getAllValues().getFirst().optionalContentLength().orElseThrow());
        assertEquals(timeout,timed.overrideConfiguration().orElseThrow().apiCallTimeout().orElseThrow());
        assertEquals(timeout,timed.overrideConfiguration().orElseThrow().apiCallAttemptTimeout().orElseThrow());
        assertTrue(requests.getAllValues().getLast().overrideConfiguration().isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void openingBudgetKeepsStreamingResponseMetadataAndAbort() throws Exception {
        ResponseInputStream<GetObjectResponse> response=mock(ResponseInputStream.class);
        when(response.response()).thenReturn(GetObjectResponse.builder().contentLength(12L).contentType("image/png").build());
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(response);
        Duration timeout=Duration.ofSeconds(5);
        try(var content=storage.open("preview/overview",timeout)) {
            assertSame(response,content.inputStream());assertEquals(12,content.contentLength());
            assertEquals("image/png",content.contentType());content.abort();
        }
        try(var ignored=storage.open("regular-read")) { }
        var requests=ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client,times(2)).getObject(requests.capture());
        var timed=requests.getAllValues().getFirst();
        assertEquals("files/preview/overview",timed.key());
        assertEquals(timeout,timed.overrideConfiguration().orElseThrow().apiCallTimeout().orElseThrow());
        assertEquals(timeout,timed.overrideConfiguration().orElseThrow().apiCallAttemptTimeout().orElseThrow());
        assertTrue(requests.getAllValues().getLast().overrideConfiguration().isEmpty());
        verify(response).abort();verify(response,times(2)).close();
    }

    @Test
    void invalidBudgetsNeverIssueAnS3Request() {
        assertThrows(IllegalArgumentException.class,() -> storage.store("preview",new ByteArrayInputStream(new byte[0]),
                0,null,Duration.ZERO));
        assertThrows(IllegalArgumentException.class,() -> storage.open("preview",Duration.ofSeconds(-1)));
        assertThrows(NullPointerException.class,() -> storage.open("preview",null));
        verifyNoInteractions(client);
    }

    @Test
    void positiveSubMillisecondUploadBudgetDoesNotDisableTheSdkTimer() {
        when(client.putObject(any(PutObjectRequest.class),any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        storage.store("preview",new ByteArrayInputStream(new byte[0]),0,null,Duration.ofNanos(1));
        var request=ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(),any(RequestBody.class));
        var options=request.getValue().overrideConfiguration().orElseThrow();
        assertEquals(Duration.ofMillis(1),options.apiCallTimeout().orElseThrow());
        assertEquals(Duration.ofMillis(1),options.apiCallAttemptTimeout().orElseThrow());
    }

    @Test
    void timeoutFailurePreservesTheStorageErrorContractAndItsCause() {
        var timeout=ApiCallTimeoutException.builder().message("request budget expired").build();
        when(client.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenThrow(timeout);
        var error=assertThrows(FileStorageException.class,() -> storage.store("preview",new ByteArrayInputStream(new byte[0]),
                0,null,Duration.ofSeconds(1)));
        assertSame(timeout,error.getCause());assertEquals("S3 对象上传失败",error.getMessage());
    }

    @Test
    void cleanupRequestsConsumeOneBudgetAcrossPagesInsteadOfRestartingIt() {
        when(client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder().build());
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(S3Object.builder().key("files/preview/a").build())
                        .isTruncated(true).nextContinuationToken("page2").build(),
                ListObjectsV2Response.builder().contents(S3Object.builder().key("files/preview/b").build())
                        .isTruncated(false).build());
        Duration budget=Duration.ofSeconds(5);
        storage.deletePrefix("preview",budget);
        var listings=ArgumentCaptor.forClass(ListObjectsV2Request.class);
        var deletions=ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(client,times(2)).listObjectsV2(listings.capture());
        verify(client,times(2)).deleteObjects(deletions.capture());
        assertEquals("files/preview/",listings.getAllValues().getFirst().prefix());
        assertEquals("page2",listings.getAllValues().getLast().continuationToken());
        Duration previous=budget;
        for(int index=0;index<2;index++) {
            var listing=listings.getAllValues().get(index).overrideConfiguration().orElseThrow();
            Duration listBudget=listing.apiCallTimeout().orElseThrow();
            assertFalse(listBudget.isZero());assertTrue(listBudget.compareTo(previous)<0);
            assertEquals(listBudget,listing.apiCallAttemptTimeout().orElseThrow());
            var deletion=deletions.getAllValues().get(index).overrideConfiguration().orElseThrow();
            Duration deleteBudget=deletion.apiCallTimeout().orElseThrow();
            assertFalse(deleteBudget.isZero());assertTrue(deleteBudget.compareTo(listBudget)<0);
            assertEquals(deleteBudget,deletion.apiCallAttemptTimeout().orElseThrow());
            previous=deleteBudget;
        }
    }

    @Test
    void expiredCleanupStopsBeforeIssuingTheNextRequest() {
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenAnswer(invocation -> {
            Thread.sleep(300);
            return ListObjectsV2Response.builder().contents(S3Object.builder().key("files/preview/a").build()).build();
        });
        assertThrows(FileStorageException.class,() -> storage.deletePrefix("preview",Duration.ofMillis(200)));
        verify(client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(client,never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void ordinaryCleanupKeepsExistingSdkDefaults() {
        when(client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder().build());
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(S3Object.builder().key("files/regular/a").build()).build());
        storage.deletePrefix("regular");
        var listing=ArgumentCaptor.forClass(ListObjectsV2Request.class);
        var deletion=ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(client).listObjectsV2(listing.capture());verify(client).deleteObjects(deletion.capture());
        assertTrue(listing.getValue().overrideConfiguration().isEmpty());
        assertTrue(deletion.getValue().overrideConfiguration().isEmpty());
    }

    @Test
    void partialObjectDeletionFailureIsNotReportedAsSuccessfulCleanup() {
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(S3Object.builder().key("files/preview/a").build())
                        .isTruncated(true).nextContinuationToken("page2").build());
        when(client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().code("AccessDenied").key("files/preview/a").build()).build());
        var failure=assertThrows(FileStorageException.class,() -> storage.deletePrefix("preview",Duration.ofSeconds(1)));
        assertEquals("S3 目录前缀中部分对象删除失败",failure.getMessage());
        verify(client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(client).deleteObjects(any(DeleteObjectsRequest.class));
    }
}
