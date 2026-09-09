package com.hidewnd.winds.jx3.model;

/** 官方补丁清单中的一条升级边。 */
public record PatchFile(String from, String to, String fileName) {}
