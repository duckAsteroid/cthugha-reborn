package io.github.duckasteroid.cthugha.dump;

import com.asteroid.duck.opengl.util.keys.Key;
import com.asteroid.duck.opengl.util.keys.KeyCombination;

import java.util.stream.Collectors;
import java.util.stream.Stream;

class KeyFormatUtil {

    /** Formats a KeyCombination as "SHIFT+K", "CTRL+SHIFT+PRINT_SCREEN", etc. */
    static String format(KeyCombination combo) {
        String mods = combo.modifiers().stream()
                .map(Key::name)
                .sorted()
                .collect(Collectors.joining("+"));
        String keys = combo.keys().stream()
                .map(Key::name)
                .collect(Collectors.joining("+"));
        return mods.isEmpty() ? keys : mods + "+" + keys;
    }
}
