package io.github.duckasteroid.cthugha.video;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record VideoEntry(
    @JsonProperty("file")            String file,
    @JsonProperty("title")           String title,
    @JsonProperty("source")          String source,
    @JsonProperty("license")         String license,
    @JsonProperty("tags")            List<String> tags,
    @JsonProperty("durationSeconds") Double durationSeconds,
    @JsonProperty("chapters")        List<Chapter> chapters
) {

    /** A named, non-overlapping sub-range of the video, in seconds from the start. */
    public record Chapter(
        @JsonProperty("name")  String name,
        @JsonProperty("start") double start,
        @JsonProperty("end")   double end
    ) {}

    /** Overrides the generated accessor so callers never have to null-check — absent in the manifest just means no chapters. */
    public List<Chapter> chapters() {
        return chapters != null ? chapters : List.of();
    }
}
