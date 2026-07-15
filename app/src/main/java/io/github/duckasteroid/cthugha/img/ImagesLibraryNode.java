package io.github.duckasteroid.cthugha.img;

import io.github.duckasteroid.cthugha.display.phase.FlashPhase;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/** Root node for the "Images" tab: a thumbnail grid of every flash image plus a Random action. */
public class ImagesLibraryNode extends ParamNode {

    public ImagesLibraryNode(FlashPhase flashPhase) throws IOException {
        super("Images");
        withUiHint(UiHint.ICON, "image");

        List<Path> files = flashPhase.imageFiles().stream().sorted().collect(Collectors.toList());
        List<String> names = files.stream().map(RandomImageSource::displayName).collect(Collectors.toList());
        List<String> groups = files.stream().map(flashPhase::groupOf).collect(Collectors.toList());
        Random rng = new Random();

        EnumParameter<String> selector = new EnumParameter<>("Image", names);
        selector.withUiHint(UiHint.CONTROL_TYPE, UiHint.GRID);
        selector.withPreviewUrls(i -> "/api/v1/images/preview/" + names.get(i));
        selector.withGroups(groups::get);
        selector.withNoPersist();
        selector.withDescription("Picks which image is flashed. Selecting one bakes it into the display immediately.");
        selector.addChangeListener(() -> flashPhase.requestFlash(files.get(selector.getValue().intValue())));

        AbstractAction random = new AbstractAction("Random", ctx -> selector.setValue(rng.nextInt(files.size())));
        random.withUiHint(UiHint.ICON, "shuffle");
        random.withDescription("Flashes a random image from the library.");

        addChild(random);
        addChild(selector);
    }
}
