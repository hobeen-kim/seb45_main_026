package com.server.module.s3.service;

import com.server.global.exception.businessexception.s3exception.S3DeleteException;
import com.server.global.exception.businessexception.s3exception.S3FileNotVaildException;
import com.server.global.exception.businessexception.s3exception.S3KeyException;
import com.server.module.s3.service.dto.FileType;
import com.server.module.s3.service.dto.ImageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CustomSignerRequest;
import software.amazon.awssdk.services.cloudfront.url.SignedUrl;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.UTF_8;

@Service
public class AwsServiceImpl implements AwsService {

    private static final String VIDEO_TYPE = "video/mp4";
    private static final String VIDEO_BUCKET_NAME = "itprometheus-videos";

    CloudFrontUtilities cloudFrontUtilities = CloudFrontUtilities.create();

    private final String KEY_PAIR_ID = "K2LLBSJU34F9A";

    @Value("${pem.location}")
    private  String PRIVATE_KEY_PATH;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public AwsServiceImpl(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public String getFileUrl(Long memberId, String fileName, FileType fileType) {

        if(fileName == null) return null;

        if(fileType.isRequiredAuth()) {
            Instant tenSecondsLater = getInstantDuration(60);

            return getFilePresignedUrl(fileType.getLocation(memberId, fileName), tenSecondsLater);
        }

        return fileType.getLocation(memberId, fileName);
    }

    @Override
    public String getImageUploadUrl(Long memberId, String fileName, FileType fileType, ImageType imageType) {

        //todo : VIDEO 가 들어오지 못하게 컴파일로 막아야하는데... 어떻게 해야할까?
        if(fileType.equals(FileType.VIDEO)) {
            return getUploadVideoUrl(memberId, fileName);
        }

        if(fileName == null) return null;

        Duration duration = Duration.ofMinutes(10);

        URL presignedPutObjectUrl = getPresignedPutImageObjectUrl(
                fileType.s3Location(memberId, fileName),
                imageType.getDescription(),
                duration);

        return URLDecoder.decode(presignedPutObjectUrl.toString(), UTF_8);
    }

    @Override
    public String getUploadVideoUrl(Long memberId, String fileName) {

        if(fileName == null) return null;

        Duration duration = Duration.ofMinutes(10);

        URL presignedPutObjectUrl = getPresignedPutVideoObjectUrl(
                memberId + "/" + fileName,
                VIDEO_TYPE,
                duration);

        return URLDecoder.decode(presignedPutObjectUrl.toString(), UTF_8);
    }

    @Override
    public void deleteFile(Long memberId, String fileName, FileType fileType) {

        checkValidFile(fileName);

        deleteFile(fileType.s3Location(memberId, fileName));
    }

    private void checkValidFile(String fileName) {
        if(fileName == null) {
            throw new S3FileNotVaildException();
        }
    }

    private Instant getInstantDuration(int secondsToAdd) {
        Instant now = Instant.now();
        return now.plusSeconds(secondsToAdd);
    }

    private String getFilePresignedUrl(String location, Instant tenSecondsLater) {
        CustomSignerRequest customSignerRequest = null;
        try {
            customSignerRequest = CustomSignerRequest.builder()
                    .resourceUrl(location)
                    .expirationDate(tenSecondsLater)
                    .keyPairId(KEY_PAIR_ID)
                    .privateKey(Path.of(PRIVATE_KEY_PATH))
                    .build();
        } catch (Exception e) {
            throw new S3KeyException();
        }

        SignedUrl signedUrlWithCustomPolicy = cloudFrontUtilities.getSignedUrlWithCustomPolicy(customSignerRequest);

        return URLDecoder.decode(signedUrlWithCustomPolicy.url(), UTF_8);
    }

    private URL getPresignedPutVideoObjectUrl(String fileName, String contentType, Duration duration) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(VIDEO_BUCKET_NAME)
                .key(fileName)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(duration)
                .putObjectRequest(objectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url();
    }

    private URL getPresignedPutImageObjectUrl(String location, String contentType, Duration duration) {

        String bucketName = location.split("/")[0];
        String path = location.substring(location.indexOf("/") + 1);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(path)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(duration)
                .putObjectRequest(objectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url();
    }

    private void deleteFile(String location) {

        String bucketName = location.split("/")[0];
        String path = location.substring(location.indexOf("/") + 1);

        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(path)
                .build();

        DeleteObjectResponse deleteObjectResponse = s3Client.deleteObject(deleteObjectRequest);

        check204Response(deleteObjectResponse);
    }

    private void check204Response(DeleteObjectResponse deleteObjectResponse) {
        if (deleteObjectResponse.sdkHttpResponse().statusCode() != 204) {
            throw new S3DeleteException();
        }
    }
}
