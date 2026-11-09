package com.g4vrk.hologramAddon.hologram.text.impl;

import com.g4vrk.hologramAddon.hologram.text.HologramTextGenerator;
import com.g4vrk.react.api.player.HistoryEntry;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.text.TextFormatter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders hologram lines from a player's retained inference history.
 *
 * <p>Supports {@code {statistic:avg-probability[:colored]}}, {@code max-probability},
 * {@code min-probability} and {@code {history:last-N}}.</p>
 */
public final class ReactHologramTextGenerator implements HologramTextGenerator<TrackedPlayer> {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^{}]+)}");

    private final ReactHologramTextConfiguration config;
    private final TextFormatter text;
    private final ThreadLocal<DecimalFormat> probabilityFormatter;

    public ReactHologramTextGenerator(
            final @NotNull ReactHologramTextConfiguration config,
            final @NotNull TextFormatter text
    ) {
        this.config = config;
        this.text = text;
        this.probabilityFormatter = ThreadLocal.withInitial(() -> createFormatter(config.probabilityFormat()));
    }

    @Override
    public @NotNull List<Component> generate(final @NotNull TrackedPlayer player) {
        final List<HistoryEntry> history = player.history();
        final Statistic statistic = Statistic.of(history);
        final List<Component> result = new ObjectArrayList<>(this.config.lines().size());

        for (final String template : this.config.lines()) {
            result.add(this.render(template, statistic, history));
        }

        return result;
    }

    private @NotNull Component render(
            final @NotNull String template,
            final @NotNull Statistic statistic,
            final @NotNull List<HistoryEntry> history
    ) {
        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        final ComponentBuilder<?, ?> result = Component.text();
        int position = 0;

        while (matcher.find()) {
            if (matcher.start() > position) {
                result.append(this.renderStatic(template.substring(position, matcher.start())));
            }

            final Component replacement = this.resolvePlaceholder(matcher.group(1), statistic, history);
            final String raw = matcher.group();
            result.append(Objects.requireNonNullElseGet(replacement, () -> Component.text(raw)));
            position = matcher.end();
        }

        if (position < template.length()) {
            result.append(this.renderStatic(template.substring(position)));
        }

        return result.build();
    }

    private @Nullable Component resolvePlaceholder(
            final @NotNull String placeholder,
            final @NotNull Statistic statistic,
            final @NotNull List<HistoryEntry> history
    ) {
        final String[] parts = placeholder.split(":");

        if (parts.length < 2) {
            return null;
        }

        return switch (parts[0]) {
            case "statistic" -> this.resolveStatistic(parts, statistic);
            case "history" -> this.resolveHistory(parts, history);
            default -> null;
        };
    }

    private @Nullable Component resolveStatistic(final @NotNull String[] parts, final @NotNull Statistic statistic) {
        final boolean colored = parts.length >= 3 && parts[2].equalsIgnoreCase("colored");

        return switch (parts[1]) {
            case "avg-probability" -> this.probability(statistic.averageProbability(), colored);
            case "max-probability" -> this.probability(statistic.maxProbability(), colored);
            case "min-probability" -> this.probability(statistic.minProbability(), colored);
            default -> null;
        };
    }

    private @Nullable Component resolveHistory(final @NotNull String[] parts, final @NotNull List<HistoryEntry> history) {
        if (!parts[1].startsWith("last-")) {
            return null;
        }

        final int amount;

        try {
            amount = Integer.parseInt(parts[1].substring("last-".length()));
        } catch (final NumberFormatException exception) {
            return null;
        }

        if (amount <= 0) {
            return Component.empty();
        }

        final ComponentBuilder<?, ?> result = Component.text();
        boolean first = true;

        for (int index = Math.max(0, history.size() - amount); index < history.size(); index++) {
            if (!first) {
                result.append(this.renderStatic(this.config.historySeparator()));
            }

            first = false;
            result.append(this.probability(history.get(index).probability(), true));
        }

        return result.build();
    }

    private @NotNull Component renderStatic(final @NotNull String value) {
        return value.isEmpty() ? Component.empty() : this.text.format(value);
    }

    private @NotNull Component probability(final double value, final boolean colored) {
        final Component component = Component.text(this.probabilityFormatter.get().format(value));
        return colored ? component.color(probabilityColor(value)) : component;
    }

    /** Green at 0, yellow at 0.5, red at 1: the scale the plugin uses for its own messages. */
    private static @NotNull TextColor probabilityColor(final double probability) {
        final double normalized = Math.min(1.0D, Math.max(probability, 0.0D));

        if (normalized < 0.5D) {
            return TextColor.color((int) (normalized * 2.0D * 255), 255, 0);
        }

        return TextColor.color(255, (int) ((1.0D - (normalized - 0.5D) * 2.0D) * 255), 0);
    }

    private static @NotNull DecimalFormat createFormatter(final @NotNull String format) {
        final DecimalFormat decimalFormat = new DecimalFormat(format, DecimalFormatSymbols.getInstance(Locale.US));
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);
        return decimalFormat;
    }

    private record Statistic(double averageProbability, double maxProbability, double minProbability) {

        private static @NotNull Statistic of(final @NotNull List<HistoryEntry> history) {
            if (history.isEmpty()) {
                return new Statistic(0.0D, 0.0D, 0.0D);
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
