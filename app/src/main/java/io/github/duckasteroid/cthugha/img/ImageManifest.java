package io.github.duckasteroid.cthugha.img;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageManifest(
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("$comment") String comment,
    @JsonProperty("images")   List<ImageEntry> images
) {

    public ImageManifest(List<ImageEntry> images) {
        this(null, images);
    }
}
