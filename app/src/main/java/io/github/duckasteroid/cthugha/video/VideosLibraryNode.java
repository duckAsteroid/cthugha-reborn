package io.github.duckasteroid.cthugha.video;

import io.github.duckasteroid.cthugha.display.phase.VideoPhase;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;

import java.util.List;
import java.util.Random;

/** Root node for the "Videos" tab: a thumbnail grid of every manifest video plus a Random action. */
public class VideosLibraryNode extends ParamNode {

    public VideosLibraryNode(VideoPhase videoPhase) {
        super("Videos");
        withUiHint(UiHint.ICON, "film");

        VideoLibrary lib = videoPhase.videoLibrary();
        List<VideoEntry> entries = lib.entries();
        if (entries.isEmpty()) return;

        List<String> names = entries.stream().map(VideoEntry::title).toList();

        EnumParameter<String> selector = new EnumParameter<>("Video", names);
        selector.withUiHint(UiHint.CONTROL_TYPE, UiHint.GRID);
        selector.withPreviewUrls(i -> "/api/v1/videos/preview/" + entries.get(i).file());
        selector.withGroups(i -> lib.primaryTheme(entries.get(i)));
        selector.withTags(i -> entries.get(i).tags());
        selector.withDescription("Picks which video plays as the background overlay. Selecting one loads it immediately.");
        selector.withNoAnimate();

        int startIdx = entries.indexOf(videoPhase.currentEntry());
        // Sync to whatever VideoPhase already auto-picked at startup, before attaching the
        // change listener below, so this initial sync doesn't itself trigger a reload.
        if (startIdx >= 0) {
            selector.setValue(startIdx);
        }
        selector.addChangeListener(() -> videoPhase.loadVideo(entries.get(selector.getValue().intValue())));

        AbstractAction random = new AbstractAction("Random", ctx -> selector.setValue(new Random().nextInt(entries.size())));
        random.withUiHint(UiHint.ICON, "shuffle");
        random.withDescription("Loads a random video from the library.");

        addChild(random);
        addChild(selector);
    }
}
