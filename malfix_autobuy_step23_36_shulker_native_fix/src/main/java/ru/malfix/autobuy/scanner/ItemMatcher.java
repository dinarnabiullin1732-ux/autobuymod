package ru.malfix.autobuy.scanner;

import ru.malfix.autobuy.auction.AuctionSlot;

import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import ru.malfix.autobuy.profiler.MalfixProfiler;

/**
 * SpookyBuy-style matcher.
 *
 * Old SpookyBuy did not rely on a single visible name check. Its CollectItem checker
 * first compared the minecraft item, then required configured NBT/tag/tooltip/
 * enchantment/effect/attribute conditions. This matcher keeps Malfix's simple
 * TargetItem config, but applies the same safe order:
 *
 * 1) exact itemId match when itemId is configured;
 * 2) all tagContains conditions must match. tagContains may use:
 *      a && b        = both required
 *      a || b        = either token accepted inside one condition
 *      !bad-token    = token must be absent
 *    The search area includes NBT, tooltip and display name, because old configs
 *    mixed all three kinds of identifiers.
 * 3) when no tag is configured, fall back to a strict visible-name/tooltip match
 *    instead of the old weak "any token" logic.
 *
 * Step 22.75: storage containers (shulker boxes) must not match targets by the
 * NBT/tooltip of items stored inside them. Otherwise a shulker containing silver
 * can be sold as the silver target. Only an explicit shulker/container target may
 * match a shulker box itself.
 */
public final class ItemMatcher {

    public MatchResult match(AuctionSlot slot, List<TargetItem> targets) {
        long profStart = MalfixProfiler.start();
        try {
            return matchInternal(slot, targets);
        } finally {
            MalfixProfiler.recordMatcher(profStart);
        }
    }

    /**
     * Very cheap prefilter used by the scanner before the expensive matcher.
     * It must never ACCEPT a purchase; it only decides whether strict matching
     * is worth running for this slot/target pair. No tooltip/NBT access here.
     */
    public boolean isFastCandidate(AuctionSlot slot, TargetItem target) {
        if (slot == null || slot.isEmpty() || target == null || !target.isEnabled()) {
            return false;
        }

        String id = normalizeId(slot.getItemId());
        String name = normalizeText(slot.getDisplayName());
        String expectedItemId = expectedItemId(target);
        boolean hasExpectedItemId = !expectedItemId.isEmpty();

        if (isStorageContainerItemId(id) && !isExplicitStorageContainerTarget(target, expectedItemId)) {
            return false;
        }

        if (hasExpectedItemId && !id.equals(expectedItemId)) {
            return false;
        }

        if (isSphereTarget(target)) {
            // Spheres are server-custom player heads. Do not require the visible
            // name in the hot prefilter: some server builds serialize Athena as
            // "Сфера Афина", "Сфера Афины", or hide the stable identity only in
            // SkullOwner/PublicBukkitValues. Safety is still preserved by the
            // strict texture/tag check in matchesSphereTarget().
            return "minecraft:player_head".equals(id);
        }

        if (isTalismanTarget(target)) {
            // Talismans are server-custom totems. Only a visible talisman-looking
            // totem is allowed into the strict AttributeModifiers check.
            return "minecraft:totem_of_undying".equals(id) && mayMatchVisibleNameOnly(target, name);
        }

        if (target.requiresTag()) {
            // If itemId is configured, itemId already narrows the heavy NBT/lore check
            // to a small family, e.g. player_head spheres or netherite armor.
            if (hasExpectedItemId) {
                return true;
            }

            // Old configs without itemId are dangerous and expensive. Only inspect
            // tooltip/NBT if the visible name already looks like this target.
            return mayMatchVisibleNameOnly(target, name);
        }

        if (hasExpectedItemId) {
            return true;
        }

        return mayMatchVisibleNameOnly(target, name);
    }


    /**
     * Step 22.68: verbose parser/debug explanation for one slot-target pair.
     * This is intentionally not used by the hot scanner path. It may read tooltip/NBT
     * and should only run from manual parser diagnostics or rare parser failure dumps.
     */
    public String debugMatch(AuctionSlot slot, TargetItem target) {
        if (slot == null) {
            return "match=false reason=slot_null";
        }
        if (slot.isEmpty()) {
            return "match=false reason=empty_slot auctionIndex=" + slot.getAuctionIndex()
                    + " containerSlot=" + slot.getContainerSlotId();
        }
        if (target == null) {
            return "match=false reason=target_null auctionIndex=" + slot.getAuctionIndex()
                    + " containerSlot=" + slot.getContainerSlotId();
        }

        String name = normalizeText(slot.getDisplayName());
        String id = normalizeId(slot.getItemId());
        String expectedItemId = expectedItemId(target);
        boolean idOk = expectedItemId.isEmpty() || id.equals(expectedItemId);
        boolean fastCandidate = isFastCandidate(slot, target);
        boolean visibleLooksOk = mayMatchVisibleNameOnly(target, name);
        boolean talismanTarget = isTalismanTarget(target);
        boolean tagTarget = target.requiresTag();
        boolean protectedCustomTarget = isProtectedCustomTarget(target, !expectedItemId.isEmpty());
        boolean storageContainer = isStorageContainerItemId(id);
        boolean storageContainerAllowed = !storageContainer || isExplicitStorageContainerTarget(target, expectedItemId);

        String tagExplain = "not_required";
        if (tagTarget && idOk) {
            SlotText lazy = new SlotText(slot, name);
            tagExplain = explainTagRule(target.getTagContains(), lazy.searchable(this));
        } else if (tagTarget) {
            tagExplain = "not_checked_id_mismatch";
        }

        MatchResult finalMatch = match(slot, Collections.singletonList(target));

        StringBuilder out = new StringBuilder(256);
        out.append("match=").append(finalMatch.isMatched())
                .append(" reason=").append(finalMatch.getReason())
                .append(" auctionIndex=").append(slot.getAuctionIndex())
                .append(" containerSlot=").append(slot.getContainerSlotId())
                .append(" actualId=").append(id)
                .append(" expectedId=").append(expectedItemId.isEmpty() ? "<none/inferred-empty>" : expectedItemId)
                .append(" idOk=").append(idOk)
                .append(" enabled=").append(target.isEnabled())
                .append(" fastCandidate=").append(fastCandidate)
                .append(" visibleLooksOk=").append(visibleLooksOk)
                .append(" tagRequired=").append(tagTarget)
                .append(" tagExplain=").append(tagExplain)
                .append(" talismanTarget=").append(talismanTarget)
                .append(" protectedCustomTarget=").append(protectedCustomTarget)
                .append(" storageContainer=").append(storageContainer)
                .append(" storageContainerAllowed=").append(storageContainerAllowed);
        return out.toString();
    }

    private MatchResult matchInternal(AuctionSlot slot, List<TargetItem> targets) {
        if (slot == null || slot.isEmpty()) {
            return MatchResult.no("empty_slot");
        }

        if (targets == null || targets.isEmpty()) {
            return MatchResult.no("no_targets");
        }

        String name = normalizeText(slot.getDisplayName());
        String id = normalizeId(slot.getItemId());
        SlotText lazy = new SlotText(slot, name);

        for (TargetItem target : targets) {
            if (target == null || !target.isEnabled()) {
                continue;
            }

            String expectedItemId = expectedItemId(target);
            if (isStorageContainerItemId(id) && !isExplicitStorageContainerTarget(target, expectedItemId)) {
                continue;
            }
            if (!expectedItemId.isEmpty() && !id.equals(expectedItemId)) {
                continue;
            }

            boolean sphereTarget = isSphereTarget(target);
            boolean talismanTarget = isTalismanTarget(target);
            boolean tagTarget = target.requiresTag();
            boolean protectedCustomTarget = isProtectedCustomTarget(target, !expectedItemId.isEmpty());

            // Cheap visible-name prefilter. It is allowed to reject obviously unrelated
            // visible-name-only targets, but it is never allowed to ACCEPT an item.
            if (!tagTarget && expectedItemId.isEmpty() && !mayMatchVisibleNameOnly(target, name)) {
                continue;
            }

            // SpookyBuy-style order: itemId/name prefilter first, then heavy proof only
            // for targets that actually need it. Do not read tooltip/NBT for plain vanilla
            // resources such as netherite ingots, obsidian, gunpowder, etc.
            if (sphereTarget) {
                String tooltip = lazy.tooltip(this);
                String nbt = lazy.nbt(this);
                if (isBlockedByKnownCustomIdentity(target, name, tooltip, nbt)) {
                    continue;
                }
                if (!matchesSphereTarget(target, name, tooltip, nbt, lazy.searchable(this))) {
                    continue;
                }
                return MatchResult.yes(target, "old_spooky_sphere_texture=" + target.getLabel());
            }

            if (talismanTarget) {
                String tooltip = lazy.tooltip(this);
                String nbt = lazy.nbt(this);
                if (isBlockedByKnownCustomIdentity(target, name, tooltip, nbt)) {
                    continue;
                }
                if (!matchesTalismanTarget(target, name, tooltip, nbt, lazy.searchable(this))) {
                    continue;
                }
                return MatchResult.yes(target, "old_spooky_talisman_attributes=" + target.getLabel());
            }

            if (tagTarget) {
                String searchable = lazy.searchable(this);
                if (isBlockedByKnownCustomIdentity(target, name, lazy.tooltip(this), lazy.nbt(this))) {
                    continue;
                }
                if (!matchesTagRule(target.getTagContains(), searchable)) {
                    continue;
                }
                return MatchResult.yes(target, "spooky_style_id_tag_enchants=" + target.getLabel());
            }

            // A protected/custom economy item without tag/attribute proof must not be bought.
            // This is the anti-fake rule: renamed vanilla junk may share the same display name.
            if (protectedCustomTarget) {
                continue;
            }

            // For old configs that forgot itemId but clearly describe a vanilla item, infer
            // the vanilla id from the label/name. This keeps commodities safe: a renamed
            // stick cannot pass as a netherite ingot just because the visible name matches.
            if (!expectedItemId.isEmpty() && !id.equals(expectedItemId)) {
                continue;
            }

            // Exact vanilla itemId is already enough for plain commodities. A visible-name
            // check is still accepted for manually created simple targets, but without any
            // expensive tooltip/NBT read.
            if (matchesStrictVisibleTarget(target, name, "", id)) {
                return MatchResult.yes(target, "spooky_style_fast_vanilla=" + target.getLabel());
            }
        }

        return MatchResult.no("not_matched");
        }


    private boolean mayMatchVisibleNameOnly(TargetItem target, String normalizedName) {
        String compactName = compactSearchText(normalizedName);
        if (compactName.isEmpty() || target == null) {
            return false;
        }

        String label = target.getLabel();
        if (isUsablePhrase(label)) {
            String normalized = normalizeRuleToken(label);
            if (!normalized.isEmpty() && containsToken(compactName, normalized)) {
                return true;
            }
        }

        for (String phrase : target.getNameContains()) {
            if (!isUsablePhrase(phrase)) {
                continue;
            }
            String normalized = normalizeRuleToken(phrase);
            if (!normalized.isEmpty() && containsToken(compactName, normalized)) {
                return true;
            }
        }

        return false;
    }

    String expectedItemId(TargetItem target) {
        if (target == null) {
            return "";
        }

        String configured = normalizeId(target.getItemId());
        if (!configured.isEmpty()) {
            return configured;
        }

        StringBuilder textBuilder = new StringBuilder();
        textBuilder.append(compactSearchText(target.getLabel()));
        for (String token : target.getNameContains()) {
            String compact = compactSearchText(token);
            if (!compact.isEmpty()) {
                if (textBuilder.length() > 0) {
                    textBuilder.append(' ');
                }
                textBuilder.append(compact);
            }
        }
        String text = textBuilder.toString();

        // Compatibility for older/manual configs: infer exact vanilla ids for common
        // commodities and blocks. This is intentionally conservative; custom/server
        // items still need tagContains or a dedicated attribute fingerprint.
        return inferKnownVanillaItemId(text);
    }

    private String inferKnownVanillaItemId(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        if (has(text, "незеритовый слиток", "netherite ingot")) return "minecraft:netherite_ingot";
        if (has(text, "незеритовый лом", "netherite scrap")) return "minecraft:netherite_scrap";
        if (has(text, "обсидиан", "obsidian") && !has(text, "плачущий", "crying")) return "minecraft:obsidian";
        if (has(text, "плачущий обсидиан", "crying obsidian")) return "minecraft:crying_obsidian";
        if (has(text, "алмаз", "diamond") && !has(text, "руда", "ore")) return "minecraft:diamond";
        if (has(text, "изумрудная руда", "emerald ore")) return "minecraft:emerald_ore";
        if (has(text, "порох", "gunpowder")) return "minecraft:gunpowder";
        if (has(text, "золотое яблоко", "golden apple") && !has(text, "зачар", "enchanted")) return "minecraft:golden_apple";
        if (has(text, "зачарованное золотое яблоко", "enchanted golden apple")) return "minecraft:enchanted_golden_apple";
        if (has(text, "тотем бессмертия", "totem of undying")) return "minecraft:totem_of_undying";
        if (has(text, "звезда незера", "nether star")) return "minecraft:nether_star";
        if (has(text, "пузырек опыта", "пузырек опыта", "experience bottle")) return "minecraft:experience_bottle";
        if (has(text, "череп визер скелета", "череп визер-скелета", "голова визер скелета", "голова визер-скелета", "wither skeleton skull")) return "minecraft:wither_skeleton_skull";
        if (has(text, "голова дракона", "dragon head")) return "minecraft:dragon_head";
        if (has(text, "яйцо шалкера", "shulker spawn egg")) return "minecraft:shulker_spawn_egg";
        if (has(text, "яйцо эндермена", "enderman spawn egg")) return "minecraft:enderman_spawn_egg";
        if (has(text, "яйцо зимогора", "stray spawn egg")) return "minecraft:stray_spawn_egg";
        if (has(text, "яйцо жителя", "villager spawn egg") && !has(text, "зомби")) return "minecraft:villager_spawn_egg";
        if (has(text, "яйцо зомби жителя", "яйцо зомби-жителя", "zombie villager spawn egg")) return "minecraft:zombie_villager_spawn_egg";
        if (has(text, "яйцо панды", "panda spawn egg")) return "minecraft:panda_spawn_egg";
        if (has(text, "яйцо странствующего торговца", "wandering trader spawn egg")) return "minecraft:wandering_trader_spawn_egg";
        if (has(text, "яйцо магмокуба", "magma cube spawn egg")) return "minecraft:magma_cube_spawn_egg";

        return "";
    }

    private boolean has(String text, String... variants) {
        if (text == null || variants == null) {
            return false;
        }
        for (String variant : variants) {
            if (variant != null && !variant.isEmpty() && text.contains(compactSearchText(variant))) {
                return true;
            }
        }
        return false;
    }

    private boolean isStorageContainerItemId(String normalizedItemId) {
        if (normalizedItemId == null || normalizedItemId.isEmpty()) {
            return false;
        }
        String id = normalizeId(normalizedItemId);
        return id.contains("shulker_box") && !id.contains("spawn_egg");
    }

    private boolean isExplicitStorageContainerTarget(TargetItem target, String expectedItemId) {
        if (target == null) {
            return false;
        }

        String expected = normalizeId(expectedItemId);
        if (!expected.isEmpty() && isStorageContainerItemId(expected)) {
            return true;
        }

        String label = compactSearchText(target.getLabel());
        if (label.contains("шалкер") || label.contains("shulkerbox") || label.contains("shulker box")) {
            return true;
        }

        for (String token : target.getNameContains()) {
            String compact = compactSearchText(token);
            if (compact.contains("шалкер") || compact.contains("shulkerbox") || compact.contains("shulker box")) {
                return true;
            }
        }

        return false;
    }

    private boolean isTalismanTarget(TargetItem target) {
        if (target == null) {
            return false;
        }
        String label = compactSearchText(target.getLabel());
        if (label.contains("талисман")) {
            return true;
        }
        for (String token : target.getNameContains()) {
            if (compactSearchText(token).contains("талисман")) {
                return true;
            }
        }
        return false;
    }


    private boolean isSphereTarget(TargetItem target) {
        if (target == null) {
            return false;
        }
        String identity = compactTargetIdentity(target);
        return identity.contains("сфера") || identity.contains("sphere") || identity.contains("attribute-item-") || identity.contains("sphere-item");
    }

    private boolean isProtectedCustomTarget(TargetItem target) {
        return isProtectedCustomTarget(target, target != null && target.requiresItemId());
    }

    private boolean isProtectedCustomTarget(TargetItem target, boolean hasEffectiveItemId) {
        if (target == null) {
            return true;
        }

        if (target.requiresTag() || isTalismanTarget(target)) {
            return true;
        }

        // Without configured or inferred exact itemId, visible-name matching is too easy to fake.
        if (!hasEffectiveItemId) {
            return true;
        }

        String label = compactSearchText(target.getLabel());
        String allNames = label;
        for (String token : target.getNameContains()) {
            allNames += " " + compactSearchText(token);
        }

        // Server/custom economy items that share vanilla itemIds must not be accepted
        // by display name alone. Add keywords here instead of weakening the matcher.
        return containsAny(allNames,
                "крушител", "талисман", "сфера", "мист", "модификатор",
                "бож", "отмыч", "трапк", "молот тора", "таер", "хлопуш",
                "святая вода", "зелье", "крабсбургер", "формула", "проклятая душа",
                "дезориентац", "замороз", "явная пыль", "прогрузчик", "дамагер",
                "аир дроп", "airdrop", "пласт", "spooky", "spookystash",
                "spookyevents", "schematic", "modifier", "chunker", "damager");
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null || text.isEmpty() || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && text.contains(compactSearchText(needle))) {
                return true;
            }
        }
        return false;
    }


    private boolean matchesSphereTarget(TargetItem target, String name, String tooltip, String nbt, String searchable) {
        if (target == null) {
            return false;
        }

        String haystack = searchable == null ? "" : searchable;
        if (haystack.isEmpty()) {
            haystack = compactSearchText((name == null ? "" : name) + " " + (tooltip == null ? "" : tooltip) + " " + (nbt == null ? "" : nbt));
        }
        if (haystack.isEmpty()) {
            return false;
        }

        String strongRule = sphereStrongIdentityRule(target);
        if (!strongRule.isEmpty()) {
            return matchesStrongSphereRule(strongRule, haystack);
        }

        String known = knownSphereIdentity(target);
        return !known.isEmpty() && sphereIdentityTokenMatches(known, haystack);
    }

    private String sphereStrongIdentityRule(TargetItem target) {
        if (target == null) {
            return "";
        }
        String rawRule = normalizeRule(target.getTagContains());
        if (rawRule.isEmpty()) {
            return knownSphereIdentity(target);
        }

        StringBuilder out = new StringBuilder();
        String[] groups = rawRule.split("&&");
        for (String groupRaw : groups) {
            String group = groupRaw == null ? "" : groupRaw.trim();
            if (group.isEmpty() || group.startsWith("!")) {
                continue;
            }

            StringBuilder strongGroup = new StringBuilder();
            String[] alternatives = group.split("\\|\\|");
            for (String alternativeRaw : alternatives) {
                String alternative = alternativeRaw == null ? "" : alternativeRaw.trim();
                if (alternative.isEmpty() || !isStrongSphereIdentityToken(alternative)) {
                    continue;
                }
                if (strongGroup.length() > 0) {
                    strongGroup.append("||");
                }
                strongGroup.append(alternative);
            }

            if (strongGroup.length() > 0) {
                if (out.length() > 0) {
                    out.append("&&");
                }
                out.append(strongGroup);
            }
        }

        if (out.length() > 0) {
            return out.toString();
        }
        return knownSphereIdentity(target);
    }

    private boolean matchesStrongSphereRule(String rawRule, String searchable) {
        String rule = normalizeRule(rawRule);
        if (rule.isEmpty()) {
            return false;
        }

        String[] groups = rule.split("&&");
        for (String groupRaw : groups) {
            String group = groupRaw == null ? "" : groupRaw.trim();
            if (group.isEmpty()) {
                continue;
            }

            boolean matched = false;
            String[] alternatives = group.split("\\|\\|");
            for (String alternativeRaw : alternatives) {
                String alternative = alternativeRaw == null ? "" : alternativeRaw.trim();
                if (alternative.isEmpty()) {
                    continue;
                }
                if (sphereIdentityTokenMatches(alternative, searchable)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private boolean isStrongSphereIdentityToken(String rawToken) {
        String token = compactSearchText(rawToken);
        if (token.isEmpty()) {
            return false;
        }

        // Visible names like "сфера титана" are not proof. They are intentionally
        // excluded here; only SkullOwner texture, long texture hash/base64, or a
        // server-side custom id may prove that the head is original.
        if (token.contains("сфера") || token.contains("sphere ")) {
            return token.contains("attribute-item-") || token.contains("sphere-item") || token.contains("skullowner") || token.contains("textures") || token.contains("texture/");
        }
        if (token.contains("attribute-item-") || token.contains("sphere-item") || token.contains("skullowner") || token.contains("textures") || token.contains("texture/")) {
            return true;
        }
        if (looksLikeTextureHash(token) || looksLikeBase64Texture(token)) {
            return true;
        }
        return false;
    }

    private boolean sphereIdentityTokenMatches(String rawToken, String searchable) {
        String token = compactSearchText(rawToken);
        if (token.isEmpty() || searchable == null || searchable.isEmpty()) {
            return false;
        }
        if (containsToken(searchable, token)) {
            return true;
        }
        if (looksLikeTextureHash(token)) {
            String encodedHttp = encodedTextureValue(token, false);
            String encodedHttps = encodedTextureValue(token, true);
            return (!encodedHttp.isEmpty() && searchable.contains(encodedHttp))
                    || (!encodedHttps.isEmpty() && searchable.contains(encodedHttps));
        }
        return false;
    }

    private String encodedTextureValue(String textureHash, boolean https) {
        if (!looksLikeTextureHash(textureHash)) {
            return "";
        }
        try {
            String protocol = https ? "https" : "http";
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + protocol + "://textures.minecraft.net/texture/" + textureHash + "\"}}}";
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)).toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private boolean looksLikeTextureHash(String token) {
        if (token == null || token.length() < 48 || token.length() > 96) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private boolean looksLikeBase64Texture(String token) {
        if (token == null || token.length() < 80) {
            return false;
        }
        return token.indexOf(' ') < 0 && (token.contains("eyj0zxh0dxjlcy") || token.contains("dgv4dhvyzw"));
    }

    private String knownSphereIdentity(TargetItem target) {
        String identity = compactTargetIdentity(target);
        if (identity.contains("хаос") || identity.contains("chaos")) return "e7a7ae7cdcf616e8b7a4221a621b2435753c60ed6a258ea060dae3002ffe9e28";
        if (identity.contains("сатира") || identity.contains("сатир") || identity.contains("статира") || identity.contains("satyr") || identity.contains("satir")) return "771a9a498b4fa5ec49362f9bc88eda4f52b04de49d75aa3ca332a1fea1aa0e57";
        if (identity.contains("бестии") || identity.contains("бестий") || identity.contains("beast")) return "5411ac17381b9fce9bab3c72afdb7f198570daf4732bd811d31c227d80fa39b1";
        if (identity.contains("ареса") || identity.contains("ares")) return "c16adc6bafcb57fd707dee7dd6a736fe126711d53a1fd6ce789da41b3be13f2a";
        if (identity.contains("гидры") || identity.contains("hydra")) return "3e3c118d696d910e54de02ca4d807543f9b18c008c9838d2ff69377622fb1d32";
        if (identity.contains("икара") || identity.contains("икар") || identity.contains("icarus") || identity.contains("ikar")) return "c6803e6d5667a2d610628bc3b32f863cda495c465616de655cb329933b61af77";
        if (identity.contains("титана") || identity.contains("titan")) return "81e9698458b7841c96ae4f24ec84ae01724100641c564e2a7b185f406e8ed23";
        if (identity.contains("эрида") || identity.contains("эриды") || identity.contains("erida") || identity.contains("eris")) return "6e4e2f1047f3ec6e9e459184739e33b7c1fc63ad8202bdab9f024508add23e5b";
        if (identity.contains("афины") || identity.contains("афина") || identity.contains("safina") || identity.contains("athena")) return "93f9eeda3ba23fe1423c4036e7dd0a74461dff96badc5b2f2b9faa7cc16f382f";
        return "";
    }

    private String compactTargetIdentity(TargetItem target) {
        if (target == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(target.getLabel()).append(' ');
        builder.append(target.getTagContains()).append(' ');
        for (String token : target.getNameContains()) {
            builder.append(token).append(' ');
        }
        return compactSearchText(builder.toString());
    }

    private boolean matchesTalismanTarget(TargetItem target, String name, String tooltip, String nbt, String searchable) {
        String identity = compactTargetIdentity(target);
        if (identity.isEmpty()) {
            return false;
        }

        String proofText = compactSearchText((nbt == null ? "" : nbt) + " " + (tooltip == null ? "" : tooltip));
        boolean hasAttributes = hasAnyAttributeData(proofText);
        if (hasAttributes) {
            return matchesKnownTalismanSignature(identity, proofText);
        }

        // NeverBuy accepts server-custom talismans when their stable identity is
        // present in item data/lore. On some 1.21.4 servers the attribute component
        // is stripped from the auction preview, but the custom name/lore still contains
        // the exact talisman identity. Require proof outside the visible slot name.
        String targetPhrase = talismanPhrase(identity);
        if (!targetPhrase.isEmpty() && proofText.contains(compactSearchText(targetPhrase))) {
            return true;
        }
        if (target.requiresTag() && matchesTagRule(target.getTagContains(), proofText)) {
            return true;
        }
        return false;
    }

    private String talismanPhrase(String identity) {
        if (identity == null) return "";
        if (identity.contains("крушител")) return "талисман крушителя";
        if (identity.contains("карател")) return "талисман карателя";
        if (identity.contains("раздор")) return "талисман раздора";
        if (identity.contains("тиран")) return "талисман тирана";
        if (identity.contains("ярост")) return "талисман ярости";
        if (identity.contains("скорост")) return "талисман скорости";
        if (identity.contains("дедал")) return "талисман дедала";
        return "";
    }

    private boolean hasAnyAttributeData(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("attributemodifiers")
                || text.contains("attribute_name")
                || text.contains("attributename")
                || text.contains("generic.max_health")
                || text.contains("generic.attack_damage")
                || text.contains("generic.movement_speed")
                || text.contains("generic.attack_speed")
                || text.contains("generic.armor")
                || text.contains("generic.armor_toughness")
                || text.contains("minecraft:generic.")
                || (text.contains("minecraft:") && text.contains("attribute"));
    }

    private boolean matchesKnownTalismanSignature(String identity, String text) {
        // Signatures copied from the donor SpookyBuy TalismanItem definitions.
        // Operation is not required here because servers may serialize it with
        // slightly different SNBT names, but attribute + amount must match.
        if (identity.contains("крушител")) {
            return attr(text, "generic.max_health", 4.0)
                    && attr(text, "generic.attack_damage", 3.0)
                    && attr(text, "generic.armor", 2.0)
                    && attr(text, "generic.armor_toughness", 2.0);
        }
        if (identity.contains("карател")) {
            return attr(text, "generic.max_health", -4.0)
                    && attr(text, "generic.movement_speed", 0.1)
                    && attr(text, "generic.attack_damage", 7.0);
        }
        if (identity.contains("раздор")) {
            return attr(text, "generic.max_health", 2.0)
                    && attr(text, "generic.movement_speed", 0.1)
                    && attr(text, "generic.attack_speed", 0.1)
                    && attr(text, "generic.attack_damage", 4.0)
                    && attr(text, "generic.armor", -3.0);
        }
        if (identity.contains("тиран")) {
            return attr(text, "generic.max_health", -4.0)
                    && attr(text, "generic.attack_damage", 2.0)
                    && attr(text, "generic.armor", 2.0);
        }
        if (identity.contains("ярост")) {
            return attr(text, "generic.max_health", -4.0)
                    && attr(text, "generic.attack_damage", 5.0);
        }
        if (identity.contains("скорост")) {
            return attr(text, "generic.max_health", 2.0)
                    && attr(text, "generic.attack_speed", 0.15)
                    && attr(text, "generic.movement_speed", 0.15);
        }
        if (identity.contains("дедал")) {
            return attr(text, "generic.max_health", 1.5)
                    && attr(text, "generic.armor", 1.5);
        }

        // Unknown talisman target: do not trust name-only matching when attributes exist.
        return false;
    }

    private boolean attr(String text, String attributeName, double amount) {
        if (text == null || text.isEmpty() || attributeName == null || attributeName.isEmpty()) {
            return false;
        }

        String compact = text.replace(" ", "");
        String attr = attributeName.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from < compact.length()) {
            int pos = compact.indexOf(attr, from);
            if (pos < 0) {
                return false;
            }

            int start = Math.max(0, pos - 160);
            int end = Math.min(compact.length(), pos + 220);
            String window = compact.substring(start, end);
            if (windowContainsAmount(window, amount)) {
                return true;
            }

            from = pos + attr.length();
        }
        return false;
    }

    private boolean windowContainsAmount(String window, double amount) {
        if (window == null || window.isEmpty()) {
            return false;
        }

        String value = formatAmount(amount);
        String integerValue = value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;

        return containsAmountNeedle(window, value)
                || (!integerValue.equals(value) && containsAmountNeedle(window, integerValue));
    }

    private boolean containsAmountNeedle(String window, String value) {
        return window.contains("amount:" + value)
                || window.contains("amount=" + value)
                || window.contains("amount:" + value + "d")
                || window.contains("amount=" + value + "d")
                || window.contains("amount:" + value + "f")
                || window.contains("amount=" + value + "f")
                || window.contains("amount:" + value + ",")
                || window.contains("amount=" + value + ",")
                || window.contains("amount:" + value + "}")
                || window.contains("amount=" + value + "}")
                || window.contains("amount:" + value + "]")
                || window.contains("amount=" + value + "]")
                || window.contains("amount:" + value + "s")
                || window.contains("amount=" + value + "s");
    }

    private String formatAmount(double amount) {
        if (amount == (long) amount) {
            return String.valueOf((long) amount) + ".0";
        }
        String value = String.valueOf(amount);
        if (value.endsWith("0") && value.indexOf('.') >= 0) {
            while (value.endsWith("0")) {
                value = value.substring(0, value.length() - 1);
            }
            if (value.endsWith(".")) {
                value += "0";
            }
        }
        return value;
    }

    private boolean matchesTagRule(String rawRule, String searchable) {
        String rule = normalizeRule(rawRule);
        if (rule.isEmpty()) {
            return true;
        }

        String[] requiredGroups = rule.split("&&");
        for (String groupRaw : requiredGroups) {
            String group = groupRaw == null ? "" : groupRaw.trim();
            if (group.isEmpty()) {
                continue;
            }

            boolean mustBeAbsent = group.startsWith("!");
            if (mustBeAbsent) {
                group = group.substring(1).trim();
            }

            boolean groupMatched = false;
            String[] alternatives = group.split("\\|\\|");
            for (String alternativeRaw : alternatives) {
                String rawAlternative = alternativeRaw == null ? "" : alternativeRaw.trim();
                if (rawAlternative.isEmpty()) {
                    continue;
                }

                if (isEnchantRule(rawAlternative)) {
                    if (matchesExactEnchant(searchable, rawAlternative)) {
                        groupMatched = true;
                        break;
                    }
                    continue;
                }

                if (isPotionEffectRule(rawAlternative)) {
                    if (matchesPotionEffect(searchable, rawAlternative)) {
                        groupMatched = true;
                        break;
                    }
                    continue;
                }

                if (isAttributeRule(rawAlternative)) {
                    if (matchesAttributeRule(searchable, rawAlternative)) {
                        groupMatched = true;
                        break;
                    }
                    continue;
                }

                String alternative = normalizeRuleToken(rawAlternative);
                if (alternative.isEmpty()) {
                    continue;
                }

                if (containsToken(searchable, alternative)) {
                    groupMatched = true;
                    break;
                }
            }

            if (mustBeAbsent) {
                if (groupMatched) {
                    return false;
                }
            } else if (!groupMatched) {
                return false;
            }
        }

        return true;
    }


    private String explainTagRule(String rawRule, String searchable) {
        String rule = normalizeRule(rawRule);
        if (rule.isEmpty()) {
            return "ok(empty_rule)";
        }
        if (searchable == null || searchable.isEmpty()) {
            return "fail(empty_searchable, rule=" + rule + ")";
        }

        String[] requiredGroups = rule.split("&&");
        for (String groupRaw : requiredGroups) {
            String group = groupRaw == null ? "" : groupRaw.trim();
            if (group.isEmpty()) {
                continue;
            }

            boolean mustBeAbsent = group.startsWith("!");
            if (mustBeAbsent) {
                group = group.substring(1).trim();
            }

            boolean groupMatched = false;
            String lastAltReason = "no_alternatives";
            String[] alternatives = group.split("\\|\\|");
            for (String alternativeRaw : alternatives) {
                String rawAlternative = alternativeRaw == null ? "" : alternativeRaw.trim();
                if (rawAlternative.isEmpty()) {
                    continue;
                }

                if (isEnchantRule(rawAlternative)) {
                    boolean ok = matchesExactEnchant(searchable, rawAlternative);
                    lastAltReason = rawAlternative + "=" + ok;
                    if (ok) {
                        groupMatched = true;
                        break;
                    }
                    continue;
                }

                if (isPotionEffectRule(rawAlternative)) {
                    boolean ok = matchesPotionEffect(searchable, rawAlternative);
                    lastAltReason = rawAlternative + "=" + ok;
                    if (ok) {
                        groupMatched = true;
                        break;
                    }
                    continue;
                }

                if (isAttributeRule(rawAlternative)) {
                    boolean ok = matchesAttributeRule(searchable, rawAlternative);
                    lastAltReason = rawAlternative + "=" + ok;
                    if (ok) {
                        groupMatched = true;
                        break;
                    }
                    continue;
                }

                String alternative = normalizeRuleToken(rawAlternative);
                if (alternative.isEmpty()) {
                    lastAltReason = rawAlternative + "=empty_after_normalize";
                    continue;
                }

                boolean ok = containsToken(searchable, alternative);
                lastAltReason = rawAlternative + "=" + ok;
                if (ok) {
                    groupMatched = true;
                    break;
                }
            }

            if (mustBeAbsent) {
                if (groupMatched) {
                    return "fail(forbidden_present: " + group + ")";
                }
            } else if (!groupMatched) {
                return "fail(missing_group: " + group + ", last=" + lastAltReason + ")";
            }
        }

        return "ok";
    }

    private boolean isEnchantRule(String value) {
        if (value == null) {
            return false;
        }
        String s = value.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("ench:") || s.startsWith("enchant:");
    }

    private boolean isPotionEffectRule(String value) {
        if (value == null) {
            return false;
        }
        String s = value.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("effect:") || s.startsWith("potion_effect:");
    }

    private boolean isAttributeRule(String value) {
        if (value == null) {
            return false;
        }
        String s = value.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("attr:") || s.startsWith("attribute:");
    }

    /**
     * Generic attribute rule used by the NeverBuy-style catalog layer.
     * Supported formats:
     *   attr:generic.max_health=4.0
     *   attr:minecraft:generic.attack_damage:7.0
     *   attribute:generic.movement_speed=0.15
     */
    private boolean matchesAttributeRule(String searchable, String rule) {
        if (searchable == null || searchable.isEmpty() || rule == null) {
            return false;
        }

        String raw = rule.trim().toLowerCase(Locale.ROOT);
        if (raw.startsWith("attribute:")) {
            raw = raw.substring("attribute:".length());
        } else if (raw.startsWith("attr:")) {
            raw = raw.substring("attr:".length());
        }

        String attributeName;
        String amountText;
        int eq = raw.lastIndexOf('=');
        if (eq >= 0) {
            attributeName = raw.substring(0, eq);
            amountText = raw.substring(eq + 1);
        } else {
            int sep = raw.lastIndexOf(':');
            if (sep < 0) {
                return false;
            }
            // Keep minecraft:generic.foo intact by splitting at the last colon only.
            attributeName = raw.substring(0, sep);
            amountText = raw.substring(sep + 1);
        }

        double amount;
        try {
            amount = Double.parseDouble(amountText.trim().replace("d", "").replace("f", ""));
        } catch (NumberFormatException ignored) {
            return false;
        }

        String normalizedAttribute = normalizeAttributeRuleName(attributeName);
        return !normalizedAttribute.isEmpty() && attr(compactSearchText(searchable), normalizedAttribute, amount);
    }

    private String normalizeAttributeRuleName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String name = rawName.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        if (name.startsWith("generic.")) {
            return name;
        }
        if ("max_health".equals(name) || "health".equals(name)) return "generic.max_health";
        if ("attack_damage".equals(name) || "damage".equals(name)) return "generic.attack_damage";
        if ("attack_speed".equals(name)) return "generic.attack_speed";
        if ("movement_speed".equals(name) || "speed".equals(name)) return "generic.movement_speed";
        if ("armor".equals(name)) return "generic.armor";
        if ("armor_toughness".equals(name) || "toughness".equals(name)) return "generic.armor_toughness";
        if ("luck".equals(name)) return "generic.luck";
        if ("knockback_resistance".equals(name)) return "generic.knockback_resistance";
        return name;
    }

    /**
     * Effect rule compatible with the user's old PotionItem + EffectData script.
     * Rule format used by the built-in catalog:
     *   effect:speed:3600:2
     *   effect:strength:3600:2
     *
     * Meaning: potion contains this effect with duration >= minDuration ticks and
     * amplifier >= minAmplifier. This is intentionally order-independent, unlike
     * a raw SNBT substring check.
     */
    private boolean matchesPotionEffect(String searchable, String rule) {
        if (searchable == null || searchable.isEmpty() || rule == null) {
            return false;
        }

        String raw = rule.trim().toLowerCase(Locale.ROOT);
        if (raw.startsWith("potion_effect:")) {
            raw = raw.substring("potion_effect:".length());
        } else if (raw.startsWith("effect:")) {
            raw = raw.substring("effect:".length());
        }

        String[] parts = raw.split(":");
        if (parts.length == 0) {
            return false;
        }

        String effectName;
        int offset;
        if (parts.length >= 2 && "minecraft".equals(parts[0])) {
            effectName = parts[1];
            offset = 2;
        } else {
            effectName = parts[0];
            offset = 1;
        }

        int minDuration = parts.length > offset ? parsePositiveInt(parts[offset]) : 1;
        int minAmplifier = parts.length > offset + 1 ? parsePositiveInt(parts[offset + 1]) : 0;
        if (minDuration <= 0) {
            minDuration = 1;
        }
        if (minAmplifier < 0) {
            minAmplifier = 0;
        }

        int effectId = potionEffectId(effectName);
        if (effectId < 0) {
            return false;
        }

        return matchesPotionEffectByNbt(searchable, effectName, effectId, minDuration, minAmplifier)
                || matchesPotionEffectByTooltip(searchable, effectName, minDuration, minAmplifier);
    }

    private int potionEffectId(String effectName) {
        if (effectName == null) {
            return -1;
        }
        String effect = effectName.trim().toLowerCase(Locale.ROOT);
        if (effect.startsWith("minecraft:")) {
            effect = effect.substring("minecraft:".length());
        }
        if ("speed".equals(effect) || "swiftness".equals(effect) || "скорость".equals(effect)) {
            return 1;
        }
        if ("strength".equals(effect) || "сила".equals(effect)) {
            return 5;
        }
        return -1;
    }

    private boolean matchesPotionEffectByNbt(String searchable, String effectName, int effectId, int minDuration, int minAmplifier) {
        String compact = searchable.replace(" ", "");
        if (compact.isEmpty()) {
            return false;
        }

        String[] needles = new String[] {
                "id:" + effectId,
                "id:" + effectId + "b",
                "id:" + effectId + "s",
                "id:\"minecraft:" + effectName + "\"",
                "minecraft:" + effectName
        };

        for (String needle : needles) {
            int from = 0;
            while (from < compact.length()) {
                int pos = compact.indexOf(needle, from);
                if (pos < 0) {
                    break;
                }

                int start = compact.lastIndexOf('{', pos);
                int end = compact.indexOf('}', pos);
                if (start < 0 || end < 0 || end <= start || end - start > 260) {
                    start = Math.max(0, pos - 80);
                    end = Math.min(compact.length(), pos + 220);
                } else {
                    end++;
                }
                String window = compact.substring(start, end);

                if (containsNumericAtLeast(window, "duration", minDuration)
                        && containsNumericAtLeast(window, "amplifier", minAmplifier)) {
                    return true;
                }

                from = pos + needle.length();
            }
        }

        return false;
    }

    private boolean matchesPotionEffectByTooltip(String searchable, String effectName, int minDuration, int minAmplifier) {
        String text = compactSearchText(searchable);
        if (text.isEmpty()) {
            return false;
        }

        boolean nameMatches;
        if ("speed".equals(effectName) || "swiftness".equals(effectName)) {
            nameMatches = text.contains("скорость") || text.contains("speed") || text.contains("swiftness");
        } else if ("strength".equals(effectName)) {
            nameMatches = text.contains("сила") || text.contains("strength");
        } else {
            nameMatches = text.contains(effectName);
        }

        if (!nameMatches) {
            return false;
        }

        if (minAmplifier >= 2) {
            boolean hasLevelThree = text.contains(" iii")
                    || text.contains("iii ")
                    || text.contains(" 3")
                    || text.contains("уровень 3")
                    || text.contains("ур. 3");
            if (!hasLevelThree) {
                return false;
            }
        }

        // Tooltip duration is localized and may be absent in some auction render paths.
        // If SNBT is unavailable but the tooltip clearly says level III, accept it.
        // The strict duration check is still performed for NBT/SNBT above.
        return true;
    }

    private boolean containsNumericAtLeast(String text, String key, int expectedMin) {
        if (text == null || text.isEmpty() || key == null || key.isEmpty()) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        String lowerKey = key.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from < lower.length()) {
            int pos = lower.indexOf(lowerKey, from);
            if (pos < 0) {
                return false;
            }

            int i = pos + lowerKey.length();
            while (i < lower.length()) {
                char c = lower.charAt(i);
                if (c == ':' || c == '=' || c == '"' || c == '\'' || Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                break;
            }

            StringBuilder digits = new StringBuilder();
            while (i < lower.length()) {
                char c = lower.charAt(i);
                if (c >= '0' && c <= '9') {
                    digits.append(c);
                    i++;
                    continue;
                }
                break;
            }

            if (digits.length() > 0) {
                try {
                    if (Integer.parseInt(digits.toString()) >= expectedMin) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            from = pos + lowerKey.length();
        }

        return false;
    }

    /**
     * Strict old-Spooky style enchant check for armor/weapons.
     * Rule examples:
     *   ench:minecraft:protection=5
     *   enchant:protection:5
     * It requires the same enchant id and the same level inside one enchant compound.
     */
    private boolean matchesExactEnchant(String searchable, String rule) {
        if (searchable == null || searchable.isEmpty() || rule == null) {
            return false;
        }

        String value = rule.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("enchant:")) {
            value = value.substring("enchant:".length()).trim();
        } else if (value.startsWith("ench:")) {
            value = value.substring("ench:".length()).trim();
        }

        if (value.isEmpty()) {
            return false;
        }

        String enchantId;
        int level;

        int eq = value.indexOf('=');
        if (eq >= 0) {
            enchantId = value.substring(0, eq).trim();
            level = parsePositiveInt(value.substring(eq + 1));
        } else {
            int colon = value.lastIndexOf(':');
            if (colon <= 0) {
                return false;
            }
            enchantId = value.substring(0, colon).trim();
            level = parsePositiveInt(value.substring(colon + 1));
        }

        if (level <= 0 || enchantId.isEmpty()) {
            return false;
        }
        if (!enchantId.startsWith("minecraft:")) {
            enchantId = "minecraft:" + enchantId;
        }

        String idNeedle = "id:\"" + enchantId + "\"";
        String lvlNeedle = "lvl:" + level;

        int from = 0;
        while (from < searchable.length()) {
            int idPos = searchable.indexOf(idNeedle, from);
            if (idPos < 0) {
                break;
            }

            int compoundStart = searchable.lastIndexOf('{', idPos);
            int compoundEnd = searchable.indexOf('}', idPos);
            if (compoundStart < 0) {
                compoundStart = Math.max(0, idPos - 80);
            }
            if (compoundEnd < 0) {
                compoundEnd = Math.min(searchable.length(), idPos + 120);
            }

            String compound = searchable.substring(compoundStart, compoundEnd + 1);
            // Accept the required enchant level or higher. Some server items may have
            // the same identity with one optional/extra enchant, or a higher level after
            // balance changes. Extra enchantments such as Thorns III are intentionally
            // ignored by this required-enchant check.
            if (containsNumericAtLeast(compound, "lvl", level)) {
                return true;
            }

            from = idPos + idNeedle.length();
        }

        // Minecraft 1.20.5+ data components stringify enchantments differently from
        // old SNBT: for example the component can contain `minecraft:sharpness=7`
        // or `minecraft:sharpness=>7` rather than `{id:"minecraft:sharpness",lvl:7}`.
        // Keep the old strict check above, then accept the component form only when
        // the required enchant id and level are close to each other.
        return matchesComponentEnchant(searchable, enchantId, level);
    }

    private boolean matchesComponentEnchant(String searchable, String enchantId, int level) {
        if (searchable == null || searchable.isEmpty() || enchantId == null || enchantId.isEmpty()) {
            return false;
        }

        String lower = searchable.toLowerCase(Locale.ROOT);
        String id = enchantId.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from < lower.length()) {
            int pos = lower.indexOf(id, from);
            if (pos < 0) {
                return false;
            }

            int start = Math.max(0, pos - 40);
            int end = Math.min(lower.length(), pos + id.length() + 80);
            String window = lower.substring(start, end);
            if (containsNumericAtLeast(window, "lvl", level)
                    || containsNumericAtLeast(window, "level", level)
                    || containsNumberAtLeastAfter(window, id, level)) {
                return true;
            }

            from = pos + id.length();
        }
        return false;
    }

    private boolean containsNumberAtLeastAfter(String text, String marker, int expectedMin) {
        if (text == null || marker == null || marker.isEmpty()) {
            return false;
        }
        int pos = text.indexOf(marker);
        if (pos < 0) {
            return false;
        }
        int i = pos + marker.length();
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ':' || c == '=' || c == '>' || c == '-' || c == '"' || c == '\''
                    || Character.isWhitespace(c) || c == ',' || c == ']' || c == ')' || c == '}') {
                i++;
                continue;
            }
            break;
        }

        StringBuilder digits = new StringBuilder();
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
                i++;
                continue;
            }
            break;
        }
        if (digits.length() == 0) {
            return false;
        }
        try {
            return Integer.parseInt(digits.toString()) >= expectedMin;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private int parsePositiveInt(String value) {
        if (value == null) {
            return -1;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (digits.length() > 0) {
                break;
            }
        }
        if (digits.length() == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private boolean matchesStrictVisibleTarget(TargetItem target, String name, String tooltip, String id) {
        String normalizedName = compactSearchText(name);
        String visible = compactSearchText(name + " " + tooltip);
        String effectiveItemId = expectedItemId(target);

        // Step 22.73: server talismans are also minecraft:totem_of_undying.
        // A generic "Тотем бессмертия" target must never accept them through the
        // old itemId-only fallback, otherwise seller can list a 3m talisman for
        // the cheap totem price. Real vanilla totems still pass by visible name.
        boolean genericTotemTarget = "minecraft:totem_of_undying".equals(effectiveItemId) && !isTalismanTarget(target);
        if (genericTotemTarget && looksLikeTalismanText(visible)) {
            return false;
        }

        if (matchesUsablePhrase(target, target.getLabel(), normalizedName, visible)) {
            return true;
        }

        for (String phrase : target.getNameContains()) {
            if (matchesUsablePhrase(target, phrase, normalizedName, visible)) {
                return true;
            }
        }

        // Fallback for configs where only itemId is meaningful: only allow simple
        // vanilla items without custom NBT/name requirements. This keeps apples,
        // diamonds, gunpowder, etc. working while avoiding "same itemId, any name"
        // purchases for custom items.
        if (genericTotemTarget) {
            return false;
        }

        if (!effectiveItemId.isEmpty() && !isProtectedCustomTarget(target, true)) {
            return true;
        }

        return false;
    }

    private boolean looksLikeTalismanText(String compactText) {
        if (compactText == null || compactText.isEmpty()) {
            return false;
        }
        return compactText.contains("талисман") || compactText.contains("talisman");
    }


    private boolean isBlockedByKnownCustomIdentity(TargetItem target, String name, String tooltip, String nbt) {
        String identityText = compactSearchText(name + " " + tooltip + " " + nbt);
        if (identityText.isEmpty()) {
            return false;
        }

        String label = compactSearchText(target.getLabel());

        // Prevent generic targets from catching custom server items through tooltip/NBT words.
        // Example: "Божье касание" contains spawner/spawner-break in its tooltip/NBT,
        // so the generic "Спавнер" target must not match it unless the user configured
        // the actual "Божье касание" target and gave it a buy price.
        return hasForeignIdentity(identityText, label, "божье касание")
                || hasForeignIdentity(identityText, label, "божья аура")
                || hasForeignIdentity(identityText, label, "молот тора")
                || hasForeignIdentity(identityText, label, "отмычка к сферам")
                || hasForeignIdentity(identityText, label, "трапка")
                || hasForeignIdentity(identityText, label, "аир дроп")
                || hasForeignIdentity(identityText, label, "дезориентация")
                || hasForeignIdentity(identityText, label, "снежок заморозка")
                || hasForeignIdentity(identityText, label, "явная пыль")
                || hasForeignIdentity(identityText, label, "проклятая душа")
                || hasForeignIdentity(identityText, label, "талисман крушителя")
                || hasForeignIdentity(identityText, label, "талисман карателя")
                || hasForeignIdentity(identityText, label, "талисман раздора")
                || hasForeignIdentity(identityText, label, "талисман тирана")
                || hasForeignIdentity(identityText, label, "талисман ярости")
                || hasForeignIdentity(identityText, label, "талисман скорости")
                || hasForeignIdentity(identityText, label, "талисман дедала");
    }

    private boolean hasForeignIdentity(String identityText, String targetLabel, String knownIdentity) {
        String normalizedIdentity = compactSearchText(knownIdentity);
        return !normalizedIdentity.isEmpty()
                && identityText.contains(normalizedIdentity)
                && !targetLabel.equals(normalizedIdentity);
    }

    private boolean isGenericOneWordPhrase(String normalizedPhrase) {
        if (normalizedPhrase == null || normalizedPhrase.isEmpty() || normalizedPhrase.indexOf(' ') >= 0) {
            return false;
        }

        return normalizedPhrase.length() <= 8
                || "spawner".equals(normalizedPhrase)
                || "silver".equals(normalizedPhrase)
                || "soul".equals(normalizedPhrase)
                || "formula".equals(normalizedPhrase)
                || "chunker".equals(normalizedPhrase)
                || "damager".equals(normalizedPhrase)
                || "airdrop".equals(normalizedPhrase);
    }

    private boolean isUsablePhrase(String phrase) {
        if (phrase == null) {
            return false;
        }
        String trimmed = phrase.trim();
        if (trimmed.length() < 3) {
            return false;
        }
        // Keep phrases as strict alternatives. Single generic English words are
        // too weak for custom server items, so use only multi-word or Cyrillic tokens.
        return trimmed.indexOf(' ') >= 0 || containsCyrillic(trimmed);
    }

    private boolean matchesUsablePhrase(TargetItem target, String phrase, String normalizedName, String visible) {
        if (!isUsablePhrase(phrase)) {
            return false;
        }

        String normalized = normalizeRuleToken(phrase);
        if (normalized.isEmpty()) {
            return false;
        }

        if (containsToken(normalizedName, normalized)) {
            return true;
        }

        if (target.requiresItemId() || target.requiresTag() || !isGenericOneWordPhrase(normalized)) {
            return containsToken(visible, normalized);
        }

        return false;
    }

    private boolean containsToken(String searchable, String token) {
        if (token.isEmpty()) {
            return true;
        }
        if (searchable.contains(token)) {
            return true;
        }
        // Support the common NBT JSON/SNBT quote mismatch:
        // spooky-item":"x vs spooky-item:"x vs spooky-item":"x
        String relaxedToken = token.replace("\\\"", "\"").replace("'", "\"");
        if (!relaxedToken.equals(token) && searchable.contains(relaxedToken)) {
            return true;
        }
        String compactToken = compactSearchText(relaxedToken);
        return !compactToken.isEmpty() && searchable.contains(compactToken);
    }

    private String normalizeRule(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String normalizeRuleToken(String value) {
        return compactSearchText(normalizeText(value));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        String s = value.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(s.length());
        boolean skipNextFormattingCode = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (skipNextFormattingCode) {
                skipNextFormattingCode = false;
                continue;
            }
            if (c == '\u00a7') {
                skipNextFormattingCode = true;
                continue;
            }
            if (Character.isISOControl(c)) {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private String compactSearchText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace("\\\"", "\"")
                .replace("'", "\"");

        StringBuilder builder = new StringBuilder(normalized.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!lastWasSpace) {
                    builder.append(' ');
                    lastWasSpace = true;
                }
            } else {
                builder.append(c);
                lastWasSpace = false;
            }
        }
        return builder.toString().trim();
    }

    private String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        String id = value.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return "";
        }
        // Registry ids inside the mod are usually "minecraft:item", but user dumps,
        // older configs and some helper scripts often print "item". Treat plain ids as
        // vanilla Minecraft ids so skull/apple/chestplate matching does not fail on the
        // namespace alone.
        if (id.indexOf(':') < 0) {
            return "minecraft:" + id;
        }
        return id;
    }

    private boolean containsCyrillic(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u0400' && c <= '\u04FF') {
                return true;
            }
        }
        return false;
    }

    private String joinTooltip(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (line != null) {
                builder.append(line).append(' ');
            }
        }
        return builder.toString();
    }
    private static final class SlotText {
        private final AuctionSlot slot;
        private final String name;
        private String tooltip;
        private String nbt;
        private String searchable;

        private SlotText(AuctionSlot slot, String name) {
            this.slot = slot;
            this.name = name == null ? "" : name;
        }

        private String tooltip(ItemMatcher owner) {
            if (tooltip == null) {
                tooltip = owner.normalizeText(owner.joinTooltip(slot.getTooltipLines()));
            }
            return tooltip;
        }

        private String nbt(ItemMatcher owner) {
            if (nbt == null) {
                nbt = owner.normalizeText(slot.getNbtString());
            }
            return nbt;
        }

        private String searchable(ItemMatcher owner) {
            if (searchable == null) {
                searchable = owner.compactSearchText(name + " " + tooltip(owner) + " " + nbt(owner));
            }
            return searchable;
        }
    }

}
