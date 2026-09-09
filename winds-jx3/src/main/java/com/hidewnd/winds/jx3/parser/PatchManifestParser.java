package com.hidewnd.winds.jx3.parser;

import com.hidewnd.winds.jx3.model.PatchFile;
import com.hidewnd.winds.jx3.model.PatchManifest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 解析官方补丁清单，升级链选择由 PatchManifest 负责。 */
public final class PatchManifestParser {
    private static final Pattern FILE =
            Pattern.compile(
                    "jx3hd_v4_c_(\\d+\\.\\d+\\.\\d+\\.\\d+)-to-(\\d+\\.\\d+\\.\\d+\\.\\d+)_zhcn_hd(?:_patch\\.exe|\\.patch)");

    private PatchManifestParser() {}

    public static PatchManifest parse(String text) {
        String latest = null;
        List<PatchFile> patches = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (line.startsWith("LatestVersion=")) {
                latest = line.substring(14).trim();
            }
            if (line.matches("Patch_\\d+=.*")) {
                String name = line.substring(line.indexOf('=') + 1).trim();
                var match = FILE.matcher(name);
                if (!match.matches()) {
                    throw new IllegalArgumentException("官方补丁文件名格式变化");
                }
                patches.add(new PatchFile(match.group(1), match.group(2), name));
            }
        }
        if (latest == null || !latest.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") || patches.isEmpty()) {
            throw new IllegalArgumentException("官方更新清单缺少版本或补丁");
        }
        return new PatchManifest(latest, List.copyOf(patches));
    }
}
