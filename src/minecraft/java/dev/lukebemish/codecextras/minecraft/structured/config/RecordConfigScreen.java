package dev.lukebemish.codecextras.minecraft.structured.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;

class RecordConfigScreen<T> extends EntryListScreen {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final List<RecordEntry<?>> entries;
    private final JsonObject jsonValue;
    private final Consumer<JsonElement> update;
    private final DynamicOps<JsonElement> ops;

    public RecordConfigScreen(Screen screen, EntryCreationInfo<T> creationInfo, List<RecordEntry<?>> entries, DynamicOps<JsonElement> ops, JsonElement jsonValue, Consumer<JsonElement> update) {
        super(screen, creationInfo.componentInfo().title());
        this.entries = entries;
        if (jsonValue.isJsonObject()) {
            this.jsonValue = jsonValue.getAsJsonObject();
        } else {
            if (!jsonValue.isJsonNull()) {
                LOGGER.warn("Value {} was not a JSON object", jsonValue);
            }
            this.jsonValue = new JsonObject();
        }
        this.update = update;
        this.ops = ops;
    }

    @Override
    protected void onExit() {
        this.update.accept(jsonValue);
    }

    @Override
    protected void addEntries() {
        for (var entry: this.entries) {
            JsonElement specificValue = this.jsonValue.has(entry.key()) ? this.jsonValue.get(entry.key()) : JsonNull.INSTANCE;
            var label = new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, entry.entry().entryCreationInfo().componentInfo().title(), font).alignLeft();
            var contents = createEntryWidget(entry, specificValue);
            entry.entry().entryCreationInfo().componentInfo().maybeDescription().ifPresent(description -> {
                var tooltip = Tooltip.create(description);
                label.setTooltip(tooltip);
            });
            this.list.addPair(label, contents);
        }
    }

    private <A> LayoutElement createEntryWidget(RecordEntry<A> entry, JsonElement specificValue) {
        // If this is missing, missing values are just not allowed
        var defaultValue = entry.missingBehavior().map(behavior -> {
            var value = behavior.missing().get();
            var encoded = entry.codec().encodeStart(ops, value);
            if (encoded.error().isPresent()) {
                // The default value is unencodeable, so we have to handle missing values in the widget
                return JsonNull.INSTANCE;
            }
            return encoded.result().orElseThrow();
        });
        JsonElement specificValueWithDefault = specificValue.isJsonNull() && defaultValue.isPresent() ? defaultValue.get() : specificValue;
        return entry.entry().widget().create(this, Button.DEFAULT_WIDTH, ops, specificValueWithDefault, newValue -> {
            if (shouldUpdate(newValue, entry)) {
                this.jsonValue.add(entry.key(), newValue);
            } else {
                this.jsonValue.remove(entry.key());
            }
        }, entry.entry().entryCreationInfo(), defaultValue.isPresent() && defaultValue.get().isJsonNull());
    }

    <F> boolean shouldUpdate(JsonElement newValue, RecordEntry<F> entry) {
        if (newValue.isJsonNull()) {
            return false;
        }
        if (entry.missingBehavior().isPresent()) {
            var decoded = entry.codec().parse(this.ops, newValue);
            if (decoded.isError()) {
                LOGGER.warn("Could not encode new value {}", newValue);
                return false;
            }
            return entry.missingBehavior().get().predicate().test(decoded.result().orElseThrow());
        }
        return true;
    }
}
