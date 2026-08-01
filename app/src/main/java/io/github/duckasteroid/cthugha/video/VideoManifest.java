package io.github.duckasteroid.cthugha.video;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VideoManifest(
    @JsonProperty("themes") Map<String, String> themes,
    @JsonProperty("videos") List<VideoEntry> videos
) {}
