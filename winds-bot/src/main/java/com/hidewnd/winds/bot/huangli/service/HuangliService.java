package com.hidewnd.winds.bot.huangli.service;

import com.hidewnd.winds.bot.huangli.event.HuangliUpdatedEvent;
import com.hidewnd.winds.bot.huangli.model.HuangliInfo;
import com.hidewnd.winds.bot.huangli.repository.HuangliRepository;
import com.hidewnd.winds.bot.oss.ObjectStorage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

@Service
public class HuangliService {

    private final ObjectStorage objectStorage;
    private final HuangliRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public HuangliService(
            ObjectStorage objectStorage,
            HuangliRepository repository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.objectStorage = objectStorage;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public HuangliInfo update(MultipartFile file, LocalDate date) throws IOException {
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("仅支持上传图片");
        }
        String imageExtension = detectImageExtension(file);
        if (imageExtension == null) {
            throw new IllegalArgumentException("仅支持 PNG、JPEG、GIF 或 WebP 图片");
        }

        LocalDate targetDate = date == null ? LocalDate.now(clock) : date;
        String formattedDate = targetDate.toString();
        String objectName = "huangli/" + formattedDate + imageExtension;
        String url = objectStorage.upload(file, objectName);
        HuangliInfo saved = repository.save(new HuangliInfo(formattedDate, url));
        eventPublisher.publishEvent(new HuangliUpdatedEvent(saved.getDate(), saved.getUrl()));
        return saved;
    }

    private String detectImageExtension(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (isPng(header)) {
                return ".png";
            }
            if (isJpeg(header)) {
                return ".jpg";
            }
            if (isGif(header)) {
                return ".gif";
            }
            if (isWebp(header)) {
                return ".webp";
            }
            return null;
        }
    }

    private boolean isPng(byte[] header) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        return startsWith(header, signature);
    }

    private boolean isJpeg(byte[] header) {
        return startsWith(header, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    }

    private boolean isGif(byte[] header) {
        return startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII));
    }

    private boolean isWebp(byte[] header) {
        return header.length >= 12
                && startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P';
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
