package dev.spectrum;

import dev.spectrum.Messages.Kind;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    private static Function<String, Object> of(Map<String, Object> map) {
        return map::get;
    }

    @Test
    void kindKeyWinsOverSharedKey() {
        var bundled = of(Map.of("chat-equipped", "chat text", "equipped", "shared text"));
        assertEquals("chat text", Messages.pick(Kind.CHAT, "equipped", of(Map.of()), bundled));
        assertEquals("shared text", Messages.pick(Kind.GENERAL, "equipped", of(Map.of()), bundled));
    }

    @Test
    void oldSharedKeyInAdminFileBeatsBundledKindKey() {
        var user = of(Map.of("equipped", "old text"));
        var bundled = of(Map.of("name-equipped", "new text"));
        assertEquals("old text", Messages.pick(Kind.NAME, "equipped", user, bundled));
    }

    @Test
    void missingKeyFallsBackToBundledAndEmptyTurnsOff() {
        var bundled = of(Map.of("name-equipped", "new text"));
        assertEquals("new text", Messages.pick(Kind.NAME, "equipped", of(Map.of()), bundled));
        assertEquals("", Messages.pick(Kind.NAME, "equipped", of(Map.of("name-equipped", "")), bundled));
        assertNull(Messages.pick(Kind.NAME, "nope", of(Map.of()), bundled));
    }

    @Test
    void listsAreAccepted() {
        var bundled = of(Map.of("usage-chat", List.of("a", "b")));
        assertEquals(List.of("a", "b"), Messages.pick(Kind.CHAT, "usage-chat", of(Map.of()), bundled));
    }

    @Test
    void bundledFileHasMatchingChatAndNameMessagesAndPrefixes() {
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                MessagesTest.class.getResourceAsStream("/messages.yml"), StandardCharsets.UTF_8));
        for (String prefix : List.of("prefix", "prefix-chat", "prefix-name")) assertTrue(yaml.isString(prefix), prefix);
        var chat = yaml.getKeys(false).stream().filter(k -> k.startsWith("chat-")).map(k -> k.substring(5))
                .collect(Collectors.toSet());
        var name = yaml.getKeys(false).stream().filter(k -> k.startsWith("name-")).map(k -> k.substring(5))
                .collect(Collectors.toSet());
        assertEquals(chat, name);
        assertTrue(chat.contains("equipped"));
    }
}
