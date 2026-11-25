package com.g4vrk.hologramAddon.hologram.text.impl;

import com.g4vrk.hologramAddon.hologram.text.HologramTextGenerator;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.text.TextFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders hologram lines from a player's retained inference history.
 *
 * <p>Supports {@code {statistic:avg-probability[:colored]}}, {@code max-probability},
 * {@code min-probability} and {@code {history:last-N}}.</p>
 */
@ApiStatus.Internal
public final class ReactHologramTextGenerator implements HologramTextGenerator<TrackedPlayer> {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^{}]+)}");
    private static final ThreadLocal<Map<String, DecimalFormat>> FORMATTERS = ThreadLocal.withInitial(HashMap::new);

    private final TextFormatter text;
    private final String probabilityFormat;
    private final Component historySeparator;
    private final CompiledLine[] compiledLines;
    private final boolean usesStatistic;

    public ReactHologramTextGenerator(
        final @NotNull ReactHologramTextConfiguration config,
        final @NotNull TextFormatter text
    ) {

        this.text = text;
        this.probabilityFormat = config.probabilityFormat();
        this.historySeparator = this.renderStatic(config.historySeparator());

        final List<String> lines = config.lines();
        final CompiledLine[] compiled = new CompiledLine[lines.size()];
        boolean hasStatistic = false;

        for (int index = 0; index < lines.size(); index++) {
            final CompiledLine line = this.compileLine(lines.get(index));
            compiled[index] = line;

            if (line.usesStatistic) {
                hasStatistic = true;
            }
        }

        this.compiledLines = compiled;
        this.usesStatistic = hasStatistic;

    }

    @Override
    public @NotNull List<Component> generate(
        final @NotNull TrackedPlayer player
    ) {

        final List<HistoryEntry> history = player.history();
        final Statistic statistic = this.usesStatistic ? Statistic.of(history) : Statistic.EMPTY;
        final List<Component> result = new ArrayList<>(this.compiledLines.length);

        for (final CompiledLine line : this.compiledLines) {
            result.add(line.render(statistic, history, this));
        }

        return result;

    }

    private @NotNull CompiledLine compileLine(
        final @NotNull String template
    ) {

        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        final List<Segment> segments = new ArrayList<>();
        boolean hasStatistic = false;
        int position = 0;

        while (matcher.find()) {
            if (matcher.start() > position) {
                segments.add(new StaticSegment(this.renderStatic(template.substring(position, matcher.start()))));
            }

            final Segment segment = this.compilePlaceholder(matcher.group(1), matcher.group());

            if (segment instanceof StatisticSegment) {
                hasStatistic = true;
            }

            segments.add(segment);
            position = matcher.end();
        }

        if (position < template.length()) {
            segments.add(new StaticSegment(this.renderStatic(template.substring(position))));
        }

        return new CompiledLine(segments.toArray(new Segment[0]), hasStatistic);

    }

    private @NotNull Segment compilePlaceholder(
        final @NotNull String placeholder,
        final @NotNull String raw
    ) {

        final int firstColon = placeholder.indexOf(':');

        if (firstColon <= 0 || firstColon == placeholder.length() - 1) {
            return new StaticSegment(Component.text(raw));
        }

        final String category = placeholder.substring(0, firstColon);
        final int secondColon = placeholder.indexOf(':', firstColon + 1);
        final String key = secondColon < 0 ? placeholder.substring(firstColon + 1) : placeholder.substring(firstColon + 1, secondColon);
        final String modifier = secondColon < 0 ? "" : placeholder.substring(secondColon + 1);

        return switch (category) {
            case "statistic" -> this.compileStatistic(key, modifier, raw);
            case "history" -> this.compileHistory(key, raw);
            default -> new StaticSegment(Component.text(raw));
        };

    }

    private @NotNull Segment compileStatistic(
        final @NotNull String key,
        final @NotNull String modifier,
        final @NotNull String raw
    ) {

        final boolean colored = modifier.equalsIgnoreCase("colored");

        return switch (key) {
            case "avg-probability" -> new StatisticSegment(StatisticKind.AVERAGE, colored);
            case "max-probability" -> new StatisticSegment(StatisticKind.MAX, colored);
            case "min-probability" -> new StatisticSegment(StatisticKind.MIN, colored);
            default -> new StaticSegment(Component.text(raw));
        };

    }

    private @NotNull Segment compileHistory(
        final @NotNull String key,
        final @NotNull String raw
    ) {

        if (!key.startsWith("last-")) {
            return new StaticSegment(Component.text(raw));
        }

        final int amount;

        try {
            amount = Integer.parseInt(key.substring("last-".length()));
        } catch (final NumberFormatException exception) {
            return new StaticSegment(Component.text(raw));
        }

        if (amount <= 0) {
            return new StaticSegment(Component.empty());
        }

        return new HistorySegment(amount);

    }

    private @NotNull Component renderStatic(
        final @NotNull String value
    ) {

        return value.isEmpty() ? Component.empty() : this.text.format(value);

    }

    private @NotNull Component renderHistory(
        final int amount,
        final @NotNull List<HistoryEntry> history
    ) {

        if (history.isEmpty()) {
            return Component.empty();
        }

        final ComponentBuilder<?, ?> result = Component.text();
        boolean first = true;

        for (int index = Math.max(0, history.size() - amount); index < history.size(); index++) {
            if (!first) {
                result.append(this.historySeparator);
            }

            first = false;
            result.append(this.probability(history.get(index).probability(), true));
        }

        return result.build();

    }

    private @NotNull Component probability(
        final double value,
        final boolean colored
    ) {

        final Component component = Component.text(this.formatter().format(value));
        return colored ? component.color(probabilityColor(value)) : component;

    }

    private @NotNull DecimalFormat formatter() {

        final Map<String, DecimalFormat> cache = FORMATTERS.get();
        final DecimalFormat existing = cache.get(this.probabilityFormat);

        if (existing != null) {
            return existing;
        }

        final DecimalFormat created = createFormatter(this.probabilityFormat);
        cache.put(this.probabilityFormat, created);
        return created;

    }

    /**
     * Green at 0, yellow at 0.5, red at 1: the scale the plugin uses for its own messages.
     */
    private static @NotNull TextColor probabilityColor(final double probability) {

        final double normalized = Math.min(1.0D, Math.max(probability, 0.0D));

        if (normalized < 0.5D) {
            return TextColor.color((int) (normalized * 2.0D * 255), 255, 0);
        }

        return TextColor.color(255, (int) ((1.0D - (normalized - 0.5D) * 2.0D) * 255), 0);

    }

    private static @NotNull DecimalFormat createFormatter(
        final @NotNull String format
    ) {

        final DecimalFormat decimalFormat = new DecimalFormat(format, DecimalFormatSymbols.getInstance(Locale.US));
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);
        return decimalFormat;

    }

    private enum StatisticKind {

        AVERAGE,
        MAX,
        MIN

    }

    private interface Segment {

        @NotNull Component render(
            @NotNull Statistic statistic,
            @NotNull List<HistoryEntry> history,
            @NotNull ReactHologramTextGenerator generator
        );

    }

    private record StaticSegment(@NotNull Component component) implements Segment {

        @Override
        public @NotNull Component render(
            final @NotNull Statistic statistic,
            final @NotNull List<HistoryEntry> history,
            final @NotNull ReactHologramTextGenerator generator
        ) {

            return this.component;

        }

    }

    private record StatisticSegment(
        @NotNull StatisticKind kind,
        boolean colored
    ) implements Segment {

        @Override
        public @NotNull Component render(
            final @NotNull Statistic statistic,
            final @NotNull List<HistoryEntry> history,
            final @NotNull ReactHologramTextGenerator generator
        ) {

            final double value = switch (this.kind) {
                case AVERAGE -> statistic.averageProbability();
                case MAX -> statistic.maxProbability();
                case MIN -> statistic.minProbability();
            };
            return generator.probability(value, this.colored);

        }

    }

    private record HistorySegment(int amount) implements Segment {

        @Override
        public @NotNull Component render(
            final @NotNull Statistic statistic,
            final @NotNull List<HistoryEntry> history,
            final @NotNull ReactHologramTextGenerator generator
        ) {

            return generator.renderHistory(this.amount, history);

        }

    }

    private static final class CompiledLine {

        private final Segment[] segments;
        private final boolean usesStatistic;

        private CompiledLine(
            final @NotNull Segment[] segments,
            final boolean usesStatistic
        ) {

            this.segments = segments;
            this.usesStatistic = usesStatistic;

        }

        private @NotNull Component render(
            final @NotNull Statistic statistic,
            final @NotNull List<HistoryEntry> history,
            final @NotNull ReactHologramTextGenerator generator
        ) {

            if (this.segments.length == 0) {
                return Component.empty();
            }

            if (this.segments.length == 1) {
                return this.segments[0].render(statistic, history, generator);
            }

            final ComponentBuilder<?, ?> builder = Component.text();

            for (final Segment segment : this.segments) {
                builder.append(segment.render(statistic, history, generator));
            }

            return builder.build();

        }

    }

    private record Statistic(double averageProbability, double maxProbability, double minProbability) {

        private static final Statistic EMPTY = new Statistic(0.0D, 0.0D, 0.0D);

        private static @NotNull Statistic of(
            final @NotNull List<HistoryEntry> history
        ) {

            if (history.isEmpty()) {
                return EMPTY;
            }

            double sum = 0.0D;
            double max = Double.NEGATIVE_INFINITY;
            double min = Double.POSITIVE_INFINITY;

            for (final HistoryEntry entry : history) {
                final double value = entry.probability();
                sum += value;
                max = Math.max(max, value);
                min = Math.min(min, value);
            }

            return new Statistic(sum / history.size(), max, min);

        }

    }

}
