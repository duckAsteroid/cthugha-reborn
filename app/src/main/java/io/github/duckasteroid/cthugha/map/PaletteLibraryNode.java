package io.github.duckasteroid.cthugha.map;

import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class PaletteLibraryNode extends AbstractNode {

    public PaletteLibraryNode(MapFileReader reader, PaletteActionContext ctx) throws IOException {
        this(reader, ctx, () -> {});
    }

    public PaletteLibraryNode(MapFileReader reader, PaletteActionContext ctx, Runnable afterLoad) throws IOException {
        super("Palette");
        withUiHint(UiHint.ICON, "palette");

        List<Path> files = reader.paletteFiles().stream().sorted().collect(Collectors.toList());
        List<String> names = files.stream().map(PaletteLibraryNode::displayName).collect(Collectors.toList());
        Random rng = new Random();

        EnumParameter<String> selector = new EnumParameter<>("Map", names);
        selector.withUiHint(UiHint.CONTROL_TYPE, UiHint.CAROUSEL);
        selector.withPreviewUrls(i -> "/api/v1/maps/preview/" + names.get(i));

        // Sync the initial selection to whatever palette is already active
        PaletteMap current = ctx.currentPalette();
        if (current != null) {
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).toString().equals(current.getName())) {
                    selector.setValue(i);  // set before listener — no load triggered
                    break;
                }
            }
        }

        // Changing the selection loads the palette
        selector.addChangeListener(() -> {
            int idx = selector.getValue().intValue();
            try {
                ctx.loadPalette(reader.load(files.get(idx)));
                afterLoad.run();
            } catch (IOException e) {
                ctx.notify("Error loading palette: " + e.getMessage());
            }
        });

        AbstractAction random = new AbstractAction("Random", c -> selector.setValue(rng.nextInt(files.size())));
        random.withUiHint(UiHint.ICON, "shuffle");
        addChild(selector);
        addChild(random);
    }

    private static String displayName(Path path) {
        String fn = path.getFileName().toString().toUpperCase();
        return fn.endsWith(".MAP") ? fn.substring(0, fn.length() - 4) : fn;
    }
}
