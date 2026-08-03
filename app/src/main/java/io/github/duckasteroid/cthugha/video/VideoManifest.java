package io.github.duckasteroid.cthugha.video;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VideoManifest(
    // Free-text note at the top of the hand-authored manifest — preserved (rather than silently
    // dropped) so a metadata edit that rewrites the file doesn't lose it. Absent in manifests
    // that don't set one.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("$comment") String comment,
    @JsonProperty("themes") Map<String, String> themes,
    @JsonProperty("videos") List<VideoEntry> videos
) {

    public VideoManifest(Map<String, String> themes, List<VideoEntry> videos) {
        this(null, themes, videos);
    }
}
