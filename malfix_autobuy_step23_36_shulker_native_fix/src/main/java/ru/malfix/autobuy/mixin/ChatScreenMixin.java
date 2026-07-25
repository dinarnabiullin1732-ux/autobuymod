package ru.malfix.autobuy.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow protected TextFieldWidget chatField;

    private static final String[] ALL_COMMANDS = new String[] {
            ".cloud save",
            ".cloud load latest",
            ".cloud list",
            ".cloud dir",
            ".script reload",
            ".script dir",
            ".tg status",
            ".tg reload",
            ".tg test",
            ".tg balance",
            ".mab help",
            ".mab gui",
            ".mab binds",
            ".mab parser",
            ".mab stop",
            ".mab shulker status",
            ".mab shulker test",
            ".mab shulker take",
            ".mab shulker reset",
            ".mab autorejoin status",
            ".mab autorejoin test",
            ".mab autorejoin stop",
            ".mab runtime status",
            ".mab runtime reload",
            ".mab runtime dir",

            // Common server/client dot commands shown by the same Never-style list.
            // They are not handled by Malfix; they are only offered visually so the
            // dot-suggestion box behaves like the user's reference screenshot.
            ".paytoggle",
            ".payoffer list",
            ".payoffdir",
            ".payoffreload",
            ".prefix",
            ".profile",
            ".pcolon",
            ".phobia",
            ".ping gui",
            ".pionands"
    };

    /**
     * Step 23.30: render dot commands as an in-chat completion list, not as real
     * chat messages. This matches Never's behaviour: the chat history stays clean,
     * the list is anchored above the input line, the first item is highlighted, and
     * Tab inserts the first suggestion.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void malfix_autobuy$renderDotCommandSuggestor(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            if (chatField == null) return;
            String text = chatField.getText();
            if (text == null || !text.startsWith(".")) return;

            String[] lines = suggestions(text);
            if (lines.length == 0) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.textRenderer == null || mc.getWindow() == null) return;

            int x = 4;
            int lineHeight = 12;
            int inputY = mc.getWindow().getScaledHeight() - 14;
            int maxWidth = 0;
            for (String line : lines) {
                int w = mc.textRenderer.getWidth(line) + 22;
                if (w > maxWidth) maxWidth = w;
            }
            maxWidth = Math.max(maxWidth, 130);

            int top = inputY - lines.length * lineHeight - 4;
            int bottom = inputY - 2;
            context.fill(x - 2, top - 2, x + maxWidth + 2, bottom + 2, 0xCC000000);

            for (int i = 0; i < lines.length; i++) {
                int y = top + i * lineHeight;
                if (i == 0) {
                    context.fill(x - 1, y - 1, x + maxWidth + 1, y + lineHeight - 1, 0xAA1B1B1B);
                    context.drawTextWithShadow(mc.textRenderer, "➜", x, y + 1, 0x55FF55);
                    context.drawTextWithShadow(mc.textRenderer, lines[i], x + 14, y + 1, 0xFFFFFF);
                } else {
                    context.drawTextWithShadow(mc.textRenderer, lines[i], x + 14, y + 1, 0xD8D8D8);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void malfix_autobuy$tabCompleteDotCommand(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (keyCode != 258 || chatField == null) { // GLFW_KEY_TAB
                return;
            }
            String text = chatField.getText();
            if (text == null || !text.startsWith(".")) {
                return;
            }
            String[] lines = suggestions(text);
            if (lines.length <= 0) {
                return;
            }
            chatField.setText(lines[0]);
            cir.setReturnValue(Boolean.TRUE);
        } catch (Throwable ignored) {
        }
    }

    private static String[] suggestions(String typed) {
        String lower = typed == null ? "" : typed.toLowerCase(Locale.ROOT).trim();
        List<String> out = new ArrayList<String>();

        for (String command : ALL_COMMANDS) {
            String c = command.toLowerCase(Locale.ROOT);
            if (lower.length() <= 1 || c.startsWith(lower)) {
                out.add(command);
            }
        }

        if (out.isEmpty()) {
            String needle = lower.replace(".", "").trim();
            for (String command : ALL_COMMANDS) {
                String c = command.toLowerCase(Locale.ROOT);
                if (!needle.isEmpty() && c.contains(needle)) {
                    out.add(command);
                }
            }
        }

        int limit = Math.min(12, out.size());
        String[] arr = new String[limit];
        for (int i = 0; i < limit; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }
}
