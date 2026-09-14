package com.g4vrk.hologramAddon.hologram.text.impl;

import com.g4vrk.hologramAddon.hologram.text.HologramTextGenerator;
import com.g4vrk.react.React;
import com.g4vrk.react.color.resolver.impl.ConfidenceColorResolver;
import com.g4vrk.react.color.resolver.impl.ProbabilityColorResolver;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.statistic.InferenceStatistic;
import com.g4vrk.text.Serializers;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReactHologramTextGenerator implements HologramTextGenerator<ReactPlayer> {

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{([^{}]+)}");

    private final ProbabilityColorResolver probabilityColorResolver =
            new ProbabilityColorResolver();

    private final ConfidenceColorResolver confidenceColorResolver =
            new ConfidenceColorResolver();

    private final Serializers serializers = React.INSTANCE.getSerializers();

    private final ReactHologramTextConfiguration config;

    private final ThreadLocal<DecimalFormat> probabilityFormatter;

    private final ThreadLocal<DecimalFormat> confidenceFormatter;

    public ReactHologramTextGenerator(
            @NotNull ReactHologramTextConfiguration config
    ) {
        this.config = config;

        this.probabilityFormatter = ThreadLocal.withInitial(
                () -> createFormatter(
                        config.probabilityFormat()
                )
        );

        this.confidenceFormatter = ThreadLocal.withInitial(
                () -> createFormatter(
                        config.confidenceFormat()
                )
        );
    }

    @Override
    public @NotNull List<Component> generate(
            @NonNull ReactPlayer reactPlayer
    ) {
        final InferenceStatistic.Result statistic =
                reactPlayer.inferenceStatistic.calculate();

        final InferenceHistoryEntry[] history =
                reactPlayer.inferenceHistory.entries();

        final List<Component> result =
                new ObjectArrayList<>(
                        this.config.lines().size()
                );

        for (String template : this.config.lines()) {
            result.add(
                    this.render(
                            template,
                            statistic,
                            history
                    )
            );
        }

        return result;
    }

    private @NotNull Component render(
            @NotNull String template,
            @NotNull InferenceStatistic.Result statistic,
            @NotNull InferenceHistoryEntry[] history
    ) {
        final Matcher matcher =
                PLACEHOLDER_PATTERN.matcher(template);

        final ComponentBuilder<?, ?> result =
                Component.text();

        int position = 0;

        while (matcher.find()) {

            if (matcher.start() > position) {
                result.append(
                        this.renderStatic(
                                template.substring(
                                        position,
                                        matcher.start()
                                )
                        )
                );
            }

            final String placeholder =
                    matcher.group(1);

            final Component replacement =
                    this.resolvePlaceholder(
                            placeholder,
                            statistic,
                            history
                    );

            result.append(Objects.requireNonNullElseGet(replacement, () -> Component.text(
                    matcher.group()
            )));

            position = matcher.end();
        }

        if (position < template.length()) {
            result.append(
                    this.renderStatic(
                            template.substring(position)
                    )
            );
        }

        return result.build();
    }

    private Component resolvePlaceholder(
            @NotNull String placeholder,
            @NotNull InferenceStatistic.Result statistic,
            @NotNull InferenceHistoryEntry[] history
    ) {
        final String[] parts =
                placeholder.split(":");

        if (parts.length < 2) {
            return null;
        }

        return switch (parts[0]) {

            case "statistic" ->
                    this.resolveStatistic(
                            parts,
                            statistic
                    );

            case "history" ->
                    this.resolveHistory(
                            parts,
                            history
                    );

            default -> null;
        };
    }

    private Component resolveStatistic(
            @NotNull String[] parts,
            @NotNull InferenceStatistic.Result statistic
    ) {
        final boolean colored =
                parts.length >= 3
                        && parts[2].equalsIgnoreCase("colored");

        return switch (parts[1]) {

            case "avg-probability" -> {

                final double value =
                        statistic.averageProbability();

                Component component =
                        Component.text(
                                this.formatProbability(value)
                        );

                if (colored) {
                    component = component.color(
                            this.probabilityColorResolver.resolve(
                                    value
                            )
                    );
                }

                yield component;
            }

            case "avg-confidence" -> {

                final double value =
                        statistic.averageConfidence();

                Component component =
                        Component.text(
                                this.formatConfidence(value)
                        );

                if (colored) {
                    component = component.color(
                            this.confidenceColorResolver.resolve(
                                    value
                            )
                    );
                }

                yield component;
            }

            default -> null;
        };
    }

    private Component resolveHistory(
            @NotNull String[] parts,
            @NotNull InferenceHistoryEntry[] history
    ) {
        if (!parts[1].startsWith("last-")) {
            return null;
        }

        final String amountString =
                parts[1].substring(
                        "last-".length()
                );

        final int amount;

        try {
            amount = Integer.parseInt(
                    amountString
            );
        } catch (NumberFormatException exception) {
            return null;
        }

        if (amount <= 0) {
            return Component.empty();
        }

        final int start =
                Math.max(
                        0,
                        history.length - amount
                );

        final ComponentBuilder<?, ?> result =
                Component.text();

        boolean first = true;

        for (int i = start; i < history.length; i++) {

            if (!first) {
                result.append(
                        this.renderStatic(
                                this.config.historySeparator()
                        )
                );
            }

            first = false;

            final double probability =
                    history[i].getProbability();

            result.append(
                    Component.text(
                            this.formatProbability(
                                    probability
                            )
                    ).color(
                            this.probabilityColorResolver.resolve(
                                    probability
                            )
                    )
            );
        }

        return result.build();
    }

    private @NotNull Component renderStatic(
            @NotNull String text
    ) {
        if (text.isEmpty()) {
            return Component.empty();
        }

        return this.serializers.universalSerializer().serialize(text);
    }

    private @NotNull String formatProbability(
            double value
    ) {
        return this.probabilityFormatter
                .get()
                .format(value);
    }

    private @NotNull String formatConfidence(
            double value
    ) {
        return this.confidenceFormatter
                .get()
                .format(value);
    }

    private static @NotNull DecimalFormat createFormatter(
            @NotNull String format
    ) {
        final DecimalFormat decimalFormat =
                new DecimalFormat(
                        format,
                        DecimalFormatSymbols.getInstance(
                                Locale.US
                        )
                );

        decimalFormat.setRoundingMode(
                RoundingMode.HALF_UP
        );

        return decimalFormat;
    }
}