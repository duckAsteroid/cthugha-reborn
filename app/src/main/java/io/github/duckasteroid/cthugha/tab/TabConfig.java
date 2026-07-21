package io.github.duckasteroid.cthugha.tab;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;

/**
 * Persistent configuration for a saved translation preset.
 *
 * <p>Serialised as {@code config.json} inside {@code tabs/<GeneratorSimpleName>/<slug>/}.
 * The generator class is implied by the directory; it is NOT stored in the JSON.</p>
 *
 * <p>The {@link #checksum} field is SHA-256(generatorFQN + canonical params JSON) and is
 * also embedded in each sibling {@code WxH.tab} binary so stale cached tabs are detected.</p>
 *
 * <p>Binary {@code .tab} files use little-endian byte order throughout (x86 native).</p>
 */
public class TabConfig {
    public String name;
    public String checksum;
    /** Flat path→value map; paths are slash-delimited names relative to the generator root. */
    public Map<String, Number> params;

    /** Folder name within the generator directory — set by {@link TabStore} on load, never serialised. */
    @JsonIgnore
    public String folderName;
}
