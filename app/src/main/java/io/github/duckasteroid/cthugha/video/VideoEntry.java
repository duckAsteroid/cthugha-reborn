package io.github.duckasteroid.cthugha.video;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record VideoEntry(
    @JsonProperty("file")            String file,
    @JsonProperty("title")           String title,
    @JsonProperty("source")          String source,
    @JsonProperty("license")         String license,
    @JsonProperty("tags")            List<String> tags,
    // Omitted from the manifest (rather than written as "durationSeconds": null) on entries that
    // don't set it, so re-saving the manifest after an unrelated edit doesn't bloat every
    // existing entry with fields it never had.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("durationSeconds") Double durationSeconds,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("chapters")        List<Chapter> chapters,
    // Name of the chapter (matched against Chapter.name) to start playing from when this video
    // is selected, instead of the whole file. Null/absent, or a name with no matching chapter,
    // means "whole video" — see VideoPhase.resolveDefaultChapterIndex.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("defaultChapter")  String defaultChapter
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
