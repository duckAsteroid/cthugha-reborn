package io.github.duckasteroid.cthugha.quote;

import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/** Root node for the "Quotes" tab: a searchable list of every loaded quote plus a Random action. */
public class QuotesLibraryNode extends ParamNode {

    public QuotesLibraryNode(List<Quote> quotes, JCthugha cthugha) {
        super("Quotes");
        withUiHint(UiHint.ICON, "quote");

        List<String> labels = quotes.stream().map(QuotesLibraryNode::label).collect(Collectors.toList());
        Random rng = new Random();

        EnumParameter<String> selector = new EnumParameter<>("Quote", labels);
        selector.withUiHint(UiHint.CONTROL_TYPE, UiHint.LIST);
        selector.withNoPersist();
        selector.withDescription("Picks which quote is currently shown. Selecting one displays it immediately.");
        selector.addChangeListener(() -> cthugha.showQuote(quotes.get(selector.getValue().intValue())));

        AbstractAction random = new AbstractAction("Random", ctx -> selector.setValue(rng.nextInt(quotes.size())));
        random.withUiHint(UiHint.ICON, "shuffle");
        random.withDescription("Shows a random quote from the library.");

        addChild(random);
        addChild(selector);
    }

    private static String label(Quote quote) {
        String text = quote.quote().replace('\n', ' ').trim();
        if (text.length() > 60) {
            text = text.substring(0, 60) + "…";
        }
        return text + " — " + quote.author();
    }
}
