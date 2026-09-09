package com.hidewnd.winds.jx3.model;

import java.util.ArrayList;
import java.util.List;

/** 官方补丁图；按实际升级边选择链路，禁止按版本差计算数量。 */
public record PatchManifest(String latestVersion, List<PatchFile> patches) {
    public List<PatchFile> chain(String from, String to) {
        List<PatchFile> result = new ArrayList<>();
        String current = from;
        while (!current.equals(to)) {
            String start = current;
            List<PatchFile> next =
                    patches.stream()
                            .filter(
                                    p ->
                                            p.from().equals(start)
                                                    && compareVersions(p.to(), start) > 0
                                                    && compareVersions(p.to(), to) <= 0)
                            .toList();
            if (next.size() != 1) {
                throw new IllegalStateException("补丁链缺失或存在歧义：" + current + " -> " + to);
            }
            PatchFile patch = next.getFirst();
            result.add(patch);
            current = patch.to();
        }
        return List.copyOf(result);
    }

    public static int compareVersions(String left, String right) {
        String[] a = left.split("\\."), b = right.split("\\.");
        if (a.length != 4 || b.length != 4) {
            throw new IllegalArgumentException("版本号必须包含四段");
        }
        for (int i = 0; i < 4; i++) {
            int cmp = Long.compare(Long.parseLong(a[i]), Long.parseLong(b[i]));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }
}
