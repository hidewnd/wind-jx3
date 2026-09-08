package com.hidewnd.winds.jx3;

import java.util.*;
import java.util.regex.Pattern;

/** 从官方清单中的实际补丁边构造升级链，禁止以版本差值猜测包数量。 */
public record PatchManifest(String latestVersion, List<Patch> patches) {
    private static final Pattern FILE = Pattern.compile("jx3hd_v4_c_(\\d+\\.\\d+\\.\\d+\\.\\d+)-to-(\\d+\\.\\d+\\.\\d+\\.\\d+)_zhcn_hd(?:_patch\\.exe|\\.patch)");

    public static PatchManifest parse(String text) {
        String latest = null;
        List<Patch> patches = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (line.startsWith("LatestVersion=")) latest = line.substring(14).trim();
            if (line.matches("Patch_\\d+=.*")) {
                String name = line.substring(line.indexOf('=') + 1).trim();
                var match = FILE.matcher(name);
                if (!match.matches()) throw new IllegalArgumentException("官方补丁文件名格式变化");
                patches.add(new Patch(match.group(1), match.group(2), name));
            }
        }
        if (latest == null || !latest.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") || patches.isEmpty())
            throw new IllegalArgumentException("官方更新清单缺少版本或补丁");
        return new PatchManifest(latest, List.copyOf(patches));
    }

    public List<Patch> chain(String from, String to) {
        List<Patch> result = new ArrayList<>();
        String current = from;
        while (!current.equals(to)) {
            String start = current;
            List<Patch> next = patches.stream().filter(p -> p.from().equals(start)
                    && compareVersions(p.to(), start) > 0 && compareVersions(p.to(), to) <= 0).toList();
            if (next.size() != 1) throw new IllegalStateException("补丁链缺失或存在歧义：" + current + " -> " + to);
            Patch patch = next.getFirst();
            result.add(patch);
            current = patch.to();
        }
        return List.copyOf(result);
    }

    public static int compareVersions(String left, String right) {
        String[] a = left.split("\\."), b = right.split("\\.");
        if (a.length != 4 || b.length != 4) throw new IllegalArgumentException("版本号必须包含四段");
        for (int i = 0; i < 4; i++) {
            int cmp = Long.compare(Long.parseLong(a[i]), Long.parseLong(b[i]));
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    public record Patch(String from, String to, String fileName) {}
}
