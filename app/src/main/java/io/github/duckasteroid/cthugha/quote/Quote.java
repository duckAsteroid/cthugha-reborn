package io.github.duckasteroid.cthugha.quote;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Quote(
    @JsonProperty("quote")  String quote,
    @JsonProperty("author") String author
) {}
