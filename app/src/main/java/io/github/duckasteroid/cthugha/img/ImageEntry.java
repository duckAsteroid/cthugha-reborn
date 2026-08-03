package io.github.duckasteroid.cthugha.img;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ImageEntry(
    @JsonProperty("file")    String file,
    @JsonProperty("title")   String title,
    @JsonProperty("source")  String source,
    @JsonProperty("license") String license,
    @JsonProperty("tags")    List<String> tags
) {

    /** Overrides the generated accessor so callers never have to null-check — absent in the manifest just means no tags. */
    public List<String> tags() {
        return tags != null ? tags : List.of();
    }
}
