package com.duperknight.client.utils;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.components.EditBox;

/**
 * Minecraft 26.x removed {@code EditBox#setFilter}. This restores the same behaviour through the
 * value responder: if the new text fails the filter, the previous valid text is put back.
 *
 * <p>Note: this installs the box's responder, so any other {@code setResponder} call on the same box
 * would replace it. Pass your own responder to the three-argument overload instead.
 */
public final class EditBoxUtils {
    private EditBoxUtils() {
    }

    public static void setFilter(EditBox box, Predicate<String> filter) {
        setFilter(box, filter, ignored -> { });
    }

    public static void setFilter(EditBox box, Predicate<String> filter, Consumer<String> responder) {
        String initial = box.getValue();
        String[] lastValid = {filter.test(initial) ? initial : ""};
        box.setResponder(text -> {
            if (!filter.test(text)) {
                box.setValue(lastValid[0]);
                return;
            }
            lastValid[0] = text;
            responder.accept(text);
        });
    }
}
 