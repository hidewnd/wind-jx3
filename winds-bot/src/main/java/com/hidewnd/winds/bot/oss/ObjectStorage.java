package com.hidewnd.winds.bot.oss;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ObjectStorage {

    String upload(MultipartFile file, String objectName) throws IOException;
}
