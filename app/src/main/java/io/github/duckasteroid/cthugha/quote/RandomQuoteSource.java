package io.github.duckasteroid.cthugha.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import io.github.duckasteroid.cthugha.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomQuoteSource {

    private static final Logger LOG = LoggerFactory.getLogger(RandomQuoteSource.class);
    private static final String DEFAULT_FILE = "quotes.json";

    private final List<Quote> quotes;
    private final Random rnd = new Random();

    public RandomQuoteSource() {
        ObjectMapper mapper = new ObjectMapper();
        CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, Quote.class);

        List<String> files = Config.singleton().getConfigs(
                Constants.SECTION, Constants.KEY_QUOTE_FILES, new String[]{DEFAULT_FILE});

        List<Quote> all = new ArrayList<>();
        for (String file : files) {
            try {
                List<Quote> loaded = mapper.readValue(new File(file), listType);
                all.addAll(loaded);
                LOG.info("Loaded {} quotes from {}", loaded.size(), file);
            } catch (Exception e) {
                LOG.warn("Failed to load quotes from {}: {}", file, e.getMessage());
            }
        }

        if (all.isEmpty()) {
            all = List.of(new Quote("My programming is terrible", "Chris Senior"));
        }
        this.quotes = all;
    }

    public Quote nextQuote() {
        return quotes.get(rnd.nextInt(quotes.size()));
    }
}
