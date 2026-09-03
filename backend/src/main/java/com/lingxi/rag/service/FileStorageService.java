package com.lingxi.rag.service;

import com.lingxi.common.exception.BizException;
import com.lingxi.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * MinIO 对象存储封装。
 */
@Slf4j
@Service
public class FileStorageService {

    private final AppProperties properties;
    private volatile MinioClient client;

    public FileStorageService(AppProperties properties) {
        this.properties = properties;
    }

    private MinioClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    AppProperties.Storage storage = properties.getStorage();
                    client = MinioClient.builder()
                            .endpoint(storage.getEndpoint())
                            .credentials(storage.getAccessKey(), storage.getSecretKey())
                            .build();
                }
            }
        }
        return client;
    }

    public void ensureBucket() {
        try {
            String bucket = properties.getStorage().getBucket();
            boolean exists = client().bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建 MinIO 桶: {}", bucket);
            }
        } catch (Exception e) {
            throw new BizException(503, "对象存储不可用：" + e.getMessage());
        }
    }

    public String upload(Long documentId, String filename, byte[] content, String contentType) {
        ensureBucket();
        String objectKey = "kb/" + documentId + "/" + filename;
        try (InputStream in = new ByteArrayInputStream(content)) {
            client().putObject(PutObjectArgs.builder()
                    .bucket(properties.getStorage().getBucket())
                    .object(objectKey)
                    .stream(in, content.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
            return objectKey;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(503, "文件上传失败：" + e.getMessage());
        }
    }

    public byte[] download(String objectKey) {
        try (InputStream in = client().getObject(GetObjectArgs.builder()
                .bucket(properties.getStorage().getBucket())
                .object(objectKey).build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new BizException(503, "文件下载失败：" + e.getMessage());
        }
    }

    public void delete(String objectKey) {
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getStorage().getBucket())
                    .object(objectKey).build());
        } catch (Exception e) {
            log.warn("MinIO 对象删除失败 key={}: {}", objectKey, e.getMessage());
        }
    }

    public String readText(String objectKey) {
        return new String(download(objectKey), StandardCharsets.UTF_8);
    }
}
