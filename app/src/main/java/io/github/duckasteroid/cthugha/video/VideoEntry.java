package io.github.duckasteroid.cthugha.video;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record VideoEntry(
    @JsonProperty("file")    String file,
    @JsonProperty("title")   String title,
    @JsonProperty("source")  String source,
    @JsonProperty("license") String license,
    @JsonProperty("tags")    List<String> tags
) {}
