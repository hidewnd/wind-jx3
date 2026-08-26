package com.hidewnd.winds.bot.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Component
public class AliyunOssObjectStorage implements ObjectStorage {

    private final OSS client;
    private final AliyunOssProperties properties;

    public AliyunOssObjectStorage(OSS client, AliyunOssProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String upload(MultipartFile file, String objectName) throws IOException {
        Assert.hasText(properties.getBucket(), "aliyun.oss.bucket 不能为空");
        Assert.hasText(properties.getPublicUrl(), "aliyun.oss.public-url 不能为空");

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        try (InputStream input = file.getInputStream()) {
            PutObjectRequest request = new PutObjectRequest(
                    properties.getBucket(), objectName, input, metadata);
            client.putObject(request);
        }

        return properties.getPublicUrl().replaceAll("/+$", "") + "/" + objectName;
    }
}
