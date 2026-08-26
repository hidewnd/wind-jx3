package com.hidewnd.winds.bot.huangli.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "huangli")
public class HuangliInfo {

    @Id
    private String date;
    private String url;
}
