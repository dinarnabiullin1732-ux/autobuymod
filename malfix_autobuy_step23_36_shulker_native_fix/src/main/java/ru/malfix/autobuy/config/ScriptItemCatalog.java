package ru.malfix.autobuy.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Built-in item catalog for Malfix AutoBuy.
 *
 * Prices are intentionally 0 and targets are disabled by default.
 * This prevents accidental buying before the user sets prices in GUI.
 */
public final class ScriptItemCatalog {

    private ScriptItemCatalog() {
    }


    private static final String ARMOR_DEFAULT_ENCHANTS =
            "ench:minecraft:protection=5&&ench:minecraft:blast_protection=5&&ench:minecraft:fire_protection=5&&ench:minecraft:projectile_protection=5&&ench:minecraft:mending=1&&ench:minecraft:unbreaking=5";

    // Chestplate Kryshitel has the normal armor enchant set. Thorns III is optional
    // on this server: valid lots may have it or may not have it, so do not require
    // ench:minecraft:thorns=3 and do not add any !thorns blocker.
    private static final String CHESTPLATE_ENCHANTS = ARMOR_DEFAULT_ENCHANTS;

    private static final String HELMET_ENCHANTS =
            ARMOR_DEFAULT_ENCHANTS + "&&ench:minecraft:aqua_affinity=1&&ench:minecraft:respiration=3";

    private static final String BOOTS_ENCHANTS =
            ARMOR_DEFAULT_ENCHANTS + "&&ench:minecraft:depth_strider=3&&ench:minecraft:feather_falling=4&&ench:minecraft:soul_speed=3";

    private static final String ELYTRA_ENCHANTS =
            "ench:minecraft:mending=1&&ench:minecraft:unbreaking=5";

    private static final String TRIDENT_ENCHANTS =
            "ench:minecraft:channeling=1&&ench:minecraft:riptide=2&&ench:minecraft:impaling=5&&ench:minecraft:loyalty=3&&ench:minecraft:mending=1&&ench:minecraft:sharpness=7&&ench:minecraft:unbreaking=5";

    private static final String SWORD_ENCHANTS =
            "ench:minecraft:bane_of_arthropods=7&&ench:minecraft:sharpness=7&&ench:minecraft:smite=7&&ench:minecraft:fire_aspect=2&&ench:minecraft:looting=5&&ench:minecraft:mending=1&&ench:minecraft:sweeping=3&&ench:minecraft:unbreaking=5";

    private static final String CROSSBOW_ENCHANTS =
            "ench:minecraft:multishot=1&&ench:minecraft:piercing=5&&ench:minecraft:quick_charge=3&&ench:minecraft:mending=1&&ench:minecraft:unbreaking=3";

    private static final String PICKAXE_ENCHANTS =
            "ench:minecraft:efficiency=10&&ench:minecraft:fortune=5&&ench:minecraft:mending=1&&ench:minecraft:unbreaking=5";

    // Exact NeverBuy MACE_CRUSH recognition layer from ru.nedan.neverbuy.item.Items:
    // item=minecraft:mace + 10 enchantments + the same custom tooltip markers.
    private static final String MACE_CRUSH_ENCHANTS =
            "ench:minecraft:sharpness=7&&ench:minecraft:bane_of_arthropods=7&&ench:minecraft:smite=7"
                    + "&&ench:minecraft:density=5&&ench:minecraft:breach=3"
                    + "&&ench:minecraft:fire_aspect=3&&ench:minecraft:knockback=2"
                    + "&&ench:minecraft:looting=5&&ench:minecraft:unbreaking=5&&ench:minecraft:mending=1";

    private static final String MACE_CRUSH_TOOLTIP =
            "опытный iii&&вампиризм ii&&окисление ii&&яд iii&&детекция iii";

    // NeverBuy recognition layer. These proof groups are intentionally based on
    // item type + real enchants/tooltip/tags instead of visible names. Visible
    // names stay only as GUI/search hints and as fallback for plain vanilla items.
    private static final String NEVER_SWORD_TOOLTIP =
            "детекция iii&&окисление ii&&вампиризм ii&&опытный iii&&яд iii";

    private static final String NEVER_PICKAXE_TOOLTIP =
            "паутина&&магнит&&пингер&&опытный iii&&авто-плавка&&бульдозер ii";

    private static final String NEVER_KEY_SPHERES_TAG =
            "spookystash:stash_id||stash_id&&spheres";

    private static final String NEVER_FLY_MODIFIER_TAG =
            "spookyitems:spooky-item||spooky-item&&modifier-item-fly-day";

    public static List<TargetConfig> createScriptTargets() {
        List<TargetConfig> list = new ArrayList<TargetConfig>();

        // Selected list from the old SpookyBuy/NeverBuy autobuy.
        add(list, "Зачарованное золотое яблоко", "minecraft:enchanted_golden_apple", 0L, "зачарованное золотое яблоко", "enchanted golden apple", "чарка");
        add(list, "Золотое яблоко", "minecraft:golden_apple", 0L, "золотое яблоко", "golden apple");

        addTagged(list, "Шлем крушителя", "minecraft:netherite_helmet", HELMET_ENCHANTS, 0L, "шлем крушителя", "helmet-kryshitel");
        addTagged(list, "Нагрудник крушителя", "minecraft:netherite_chestplate", CHESTPLATE_ENCHANTS, 0L, "нагрудник крушителя", "chestplate-kryshitel");
        addTagged(list, "Поножи крушителя", "minecraft:netherite_leggings", ARMOR_DEFAULT_ENCHANTS, 0L, "поножи крушителя", "leggings-kryshitel");
        addTagged(list, "Ботинки крушителя", "minecraft:netherite_boots", BOOTS_ENCHANTS, 0L, "ботинки крушителя", "boots-kryshitel");
        addTagged(list, "Трезубец крушителя", "minecraft:trident", "trident-kryshitel&&" + TRIDENT_ENCHANTS, 0L, "трезубец крушителя", "trident-kryshitel");
        addTagged(list, "Меч крушителя", "minecraft:netherite_sword", SWORD_ENCHANTS + "&&" + NEVER_SWORD_TOOLTIP, 0L, "меч крушителя", "sword-kryshitel");
        addTagged(list, "Арбалет крушителя", "minecraft:crossbow", "crossbow-kryshitel&&" + CROSSBOW_ENCHANTS, 0L, "арбалет крушителя", "crossbow-kryshitel");
        addTagged(list, "Кирка крушителя", "minecraft:netherite_pickaxe", PICKAXE_ENCHANTS + "&&" + NEVER_PICKAXE_TOOLTIP, 0L, "кирка крушителя", "pickaxe-kryshitel");

        addTagged(list, "Божья Аура", "minecraft:phantom_membrane", "effect-item-god", 0L, "божья аура", "effect-item-god");
        addTagged(list, "Серебро", "", "spookystash:currency\":\"silver", 0L, "серебро", "silver");
        add(list, "Алмаз", "minecraft:diamond", 0L, "алмаз", "diamond");
        addTagged(list, "Книга починка", "minecraft:enchanted_book", "minecraft:mending", 0L, "книга починка", "починка", "mending");
        addTagged(list, "Элитры Крушителя", "minecraft:elytra", "elytra-kryshitel&&" + ELYTRA_ENCHANTS, 0L, "элитры крушителя", "элитры", "elytra", "elytra-kryshitel&&" + ELYTRA_ENCHANTS);
        addTagged(list, "Божье касание", "minecraft:golden_pickaxe", "spawner-item-spawner-break", 0L, "божье касание", "spawner-item-spawner-break");
        addTagged(list, "Молот Тора", "minecraft:netherite_pickaxe", "radius-item-mega-buldozer", 0L, "молот тора", "radius-item-mega-buldozer");
        addTagged(list, "Трапка", "minecraft:netherite_scrap", "schematic-item-trap", 0L, "трапка", "schematic-item-trap");
        addTagged(list, "Отмычка к сферам", "minecraft:tripwire_hook", NEVER_KEY_SPHERES_TAG, 0L, "отмычка к сферам", "отмычка", "spheres");
        add(list, "Спавнер", "minecraft:spawner", 0L, "спавнер", "spawner");
        add(list, "Череп визер-скелета", "minecraft:wither_skeleton_skull", 0L, "череп визер-скелета", "череп визер скелета", "голова визер-скелета", "голова визер скелета", "wither skeleton skull");
        add(list, "Голова дракона", "minecraft:dragon_head", 0L, "голова дракона", "dragon head");
        add(list, "Голова пиглина", "minecraft:piglin_head", 0L, "голова пиглина", "piglin head");
        addTagged(list, "Булава Крушителя", "minecraft:mace", MACE_CRUSH_ENCHANTS + "&&" + MACE_CRUSH_TOOLTIP, 0L, "булава крушителя", "mace crush", "mace kryshitel");
        add(list, "Обычная булава", "minecraft:mace", 0L, "обычная булава", "булава", "mace");
        add(list, "Навершие булавы", "minecraft:heavy_core", 0L, "навершие булавы", "heavy core");
        addTagged(list, "Таер блэк", "minecraft:tnt", "tnt-item-black", 0L, "таер блэк", "tnt-item-black");
        addTagged(list, "Таер вайт", "minecraft:tnt", "tnt-item-white", 0L, "таер вайт", "tnt-item-white");

        addTagged(list, "Хлопушка", "minecraft:splash_potion", "custompotioncolor:16738740&&хлопушка", 0L, "хлопушка");
        addTagged(list, "Святая вода", "minecraft:splash_potion", "custompotioncolor:16777215&&святая вода", 0L, "святая вода");
        addTagged(list, "Зелье гнева", "minecraft:splash_potion", "custompotioncolor:10040115&&зелье гнева", 0L, "зелье гнева");
        addTagged(list, "Зелье палладина", "minecraft:splash_potion", "custompotioncolor:65535&&зелье палладина||зелье паладина", 0L, "зелье палладина", "зелье паладина");
        addTagged(list, "Зелье ассасина", "minecraft:splash_potion", "custompotioncolor:3355443&&зелье ассасина", 0L, "зелье ассасина");
        addTagged(list, "Зелье радиации", "minecraft:splash_potion", "custompotioncolor:3329330&&зелье радиации", 0L, "зелье радиации");
        addTagged(list, "Снотворное", "minecraft:splash_potion", "custompotioncolor:4737096&&снотворное", 0L, "снотворное");

        // From Malfix_only_potions_effectdata_autoreg.js:
        // PotionItem(false, 0x7CAFC6, SPEED III 180s, STRENGTH III 180s).
        // Match by potion effects, not by exact NBT string, so effect order does not matter.
        addTaggedUnstack(list, "Несоздаваемое зелье", "minecraft:potion",
                "effect:speed:3600:2&&effect:strength:3600:2",
                0L, true, 6,
                "несоздаваемое зелье", "скорость iii", "сила iii", "speed iii", "strength iii");

        addTagged(list, "Сфера Афины", "minecraft:player_head", "93f9eeda3ba23fe1423c4036e7dd0a74461dff96badc5b2f2b9faa7cc16f382f||attribute-item-safina", 0L, "сфера афины", "сфера афина", "attribute-item-safina");
        addTagged(list, "Сфера Хаоса", "minecraft:player_head", "e7a7ae7cdcf616e8b7a4221a621b2435753c60ed6a258ea060dae3002ffe9e28", 0L, "сфера хаоса");
        addTagged(list, "Сфера Сатира", "minecraft:player_head", "771a9a498b4fa5ec49362f9bc88eda4f52b04de49d75aa3ca332a1fea1aa0e57", 0L, "сфера сатира", "сфера сатир");
        addTagged(list, "Сфера Бестии", "minecraft:player_head", "5411ac17381b9fce9bab3c72afdb7f198570daf4732bd811d31c227d80fa39b1", 0L, "сфера бестии", "сфера бестий");
        addTagged(list, "Сфера Ареса", "minecraft:player_head", "c16adc6bafcb57fd707dee7dd6a736fe126711d53a1fd6ce789da41b3be13f2a", 0L, "сфера ареса");
        addTagged(list, "Сфера Гидры", "minecraft:player_head", "3e3c118d696d910e54de02ca4d807543f9b18c008c9838d2ff69377622fb1d32", 0L, "сфера гидры");
        addTagged(list, "Сфера Икара", "minecraft:player_head", "c6803e6d5667a2d610628bc3b32f863cda495c465616de655cb329933b61af77", 0L, "сфера икара", "сфера икар");
        addTagged(list, "Сфера Титана", "minecraft:player_head", "81e9698458b7841c96ae4f24ec84ae01724100641c564e2a7b185f406e8ed23", 0L, "сфера титана");
        addTagged(list, "Сфера Эрида", "minecraft:player_head", "6e4e2f1047f3ec6e9e459184739e33b7c1fc63ad8202bdab9f024508add23e5b", 0L, "сфера эрида", "сфера эриды");

        addTagged(list, "Талисман Крушителя", "minecraft:totem_of_undying", "талисман крушителя", 0L, "талисман крушителя");
        addTagged(list, "Талисман Карателя", "minecraft:totem_of_undying", "талисман карателя", 0L, "талисман карателя");
        addTagged(list, "Талисман Раздора", "minecraft:totem_of_undying", "талисман раздора", 0L, "талисман раздора");
        addTagged(list, "Талисман Тирана", "minecraft:totem_of_undying", "талисман тирана", 0L, "талисман тирана");
        addTagged(list, "Талисман Ярости", "minecraft:totem_of_undying", "талисман ярости", 0L, "талисман ярости");

        // New mist tags from spookybuy-2.5.3-ForkNeverMods.
        addTagged(list, "Обычный мист", "minecraft:campfire", "spookyevents:mythic\":\"MILD", 0L, "обычный мист", "мист", "mild");
        addTagged(list, "Богатый мист", "minecraft:campfire", "spookyevents:mythic\":\"WEAK", 0L, "богатый мист", "weak");
        addTagged(list, "Легендарный мист", "minecraft:soul_campfire", "spookyevents:mythic\":\"MEDIUM", 0L, "легендарный мист", "medium");

        // Existing script/custom targets.
        add(list, "Порох", "minecraft:gunpowder", 0L, "порох", "gunpowder");
        add(list, "Обсидиан", "minecraft:obsidian", 0L, "обсидиан", "obsidian");
        add(list, "Плачущий обсидиан", "minecraft:crying_obsidian", 0L, "плачущий обсидиан", "crying obsidian");
        add(list, "Тотем бессмертия", "minecraft:totem_of_undying", 0L, "тотем бессмертия", "totem of undying");
        add(list, "Звезда незера", "minecraft:nether_star", 0L, "звезда незера", "nether star");
        add(list, "Незеритовый слиток", "minecraft:netherite_ingot", 0L, "незеритовый слиток", "netherite ingot");
        add(list, "Яйцо дракона", "minecraft:dragon_egg", 0L, "яйцо дракона", "dragon egg");
        add(list, "Яйцо нюхача", "minecraft:sniffer_egg", 0L, "яйцо нюхача", "sniffer egg");
        add(list, "Яйцо голема", "minecraft:iron_golem_spawn_egg", 0L, "яйцо голема", "golem spawn egg", "iron golem spawn egg");
        add(list, "Маяк", "minecraft:beacon", 0L, "маяк", "beacon");
        add(list, "Незеритовый лом", "minecraft:netherite_scrap", 0L, "незеритовый лом", "netherite scrap");
        add(list, "Изумрудная руда", "minecraft:emerald_ore", 0L, "изумрудная руда", "emerald ore");
        add(list, "Яйцо шалкера", "minecraft:shulker_spawn_egg", 0L, "яйцо шалкера", "shulker spawn egg");
        add(list, "Яйцо эндермена", "minecraft:enderman_spawn_egg", 0L, "яйцо эндермена", "enderman spawn egg");
        add(list, "Яйцо зимогора", "minecraft:stray_spawn_egg", 0L, "яйцо зимогора", "stray spawn egg");
        add(list, "Яйцо жителя", "minecraft:villager_spawn_egg", 0L, "яйцо жителя", "villager spawn egg");
        add(list, "Яйцо панды", "minecraft:panda_spawn_egg", 0L, "яйцо панды", "panda spawn egg");
        add(list, "Яйцо зомби-жителя", "minecraft:zombie_villager_spawn_egg", 0L, "яйцо зомби-жителя", "zombie villager spawn egg");
        add(list, "Яйцо странствующего торговца", "minecraft:wandering_trader_spawn_egg", 0L, "яйцо странствующего торговца", "wandering trader spawn egg");
        add(list, "Яйцо магмокуба", "minecraft:magma_cube_spawn_egg", 0L, "яйцо магмокуба", "magma cube spawn egg");
        add(list, "Шалкер", "minecraft:shulker_box", 0L, "шалкер", "shulker");

        addTagged(list, "Модификатор полёта", "minecraft:feather", NEVER_FLY_MODIFIER_TAG, 0L, "модификатор полёта", "модификатор полета", "fly", "modifier-item-fly-day");
        addTagged(list, "Модификатор починки", "minecraft:bubble_coral", "spooky-item\":\"modifier-item-fix-day", 0L, "модификатор починки", "починка", "fix", "modifier-item-fix-day");
        addTagged(list, "Крабсбургер", "minecraft:pumpkin_pie", "spookystash:currency\":\"burger", 0L, "крабсбургер", "burger");
        addTagged(list, "Формула крабсбургера", "minecraft:guster_banner_pattern", "spookystash:currency\":\"formula", 0L, "формула крабсбургера", "formula");
        addTagged(list, "Проклятая душа", "minecraft:soul_lantern", "spookystash:currency\":\"soul", 0L, "проклятая душа", "soul");
        addTagged(list, "Опыт 15уровень", "minecraft:experience_bottle", "spookystash:levels\":15", 0L, "опыт 15уровень", "пузырёк опыта [15", "15 ур", "15 уровень");
        addTagged(list, "Дезориентация", "minecraft:ender_eye", "spooky-item\":\"effect-item-diz", 0L, "дезориентация", "effect-item-diz");
        addTagged(list, "Снежок заморозка", "minecraft:snowball", "spooky-item\":\"effect-item-snowball", 0L, "снежок заморозка", "заморозка", "effect-item-snowball");
        addTagged(list, "Явная пыль", "minecraft:sugar", "spooky-item\":\"effect-item-dust", 0L, "явная пыль", "effect-item-dust");
        addTagged(list, "Прогрузчик чанков 1x1", "minecraft:structure_block", "spooky-item\":\"executable-block-chunker-1", 0L, "прогрузчик чанков", "chunker");
        addTagged(list, "Дамагер", "minecraft:jigsaw", "spooky-item\":\"executable-block-damager", 0L, "дамагер", "damager");
        addTagged(list, "Аир-дроп", "minecraft:redstone_torch", "spookyevents:airdrop_summoning\":\"truee", 0L, "аир-дроп", "airdrop");
        addTagged(list, "Пласт", "minecraft:dried_kelp", "schematic-item-plast", 0L, "пласт", "schematic-item-plast");
        add(list, "Пузырёк опыта", "minecraft:experience_bottle", 0L, "пузырёк опыта", "experience bottle");

        return list;
    }

    public static int mergeInto(AutoBuyConfig config) {
        if (config == null) {
            return 0;
        }

        int changed = removeDeprecatedTargets(config);
        changed += patchExistingTargets(config);

        for (TargetConfig target : createScriptTargets()) {
            if (config.findTarget(target.getLabel()) == null) {
                if (config.addTarget(target)) {
                    changed++;
                }
            }
        }

        changed += reorderKnownTargets(config);
        return changed;
    }

    public static int removeDeprecatedTargets(AutoBuyConfig config) {
        if (config == null) {
            return 0;
        }

        int removed = 0;
        String[] deprecated = new String[] {
                "Пасхальное яйцо",
                "Сигнальный огонь MILD",
                "Сигнальный огонь Богатый",
                "Сигнальный огонь Легендарный",
                "Кузнечный шаблон: Береговая отделка",
                "Кузнечный шаблон: Пустынная отделка",
                "Кузнечный шаблон: Отделка глаза",
                "Кузнечный шаблон: Отделка хозяина",
                "Кузнечный шаблон: Отделка подъемa",
                "Кузнечный шаблон: Реберная отделка",
                "Кузнечный шаблон: Отделка стража",
                "Кузнечный шаблон: Отделка кузнеца",
                "Кузнечный шаблон: Тихая отделка",
                "Кузнечный шаблон: Отделка рыла",
                "Кузнечный шаблон: Отделка шпиля",
                "Кузнечный шаблон: Приливная отделка",
                "Кузнечный шаблон: Отделка vex",
                "Кузнечный шаблон: Отделка палаты",
                "Кузнечный шаблон: Отделка путника",
                "Кузнечный шаблон: Дикая отделка",
                "Кузнечный шаблон: Болтовая отделка",
                "Кузнечный шаблон: Поточная отделка",
                "Отделка Береска",
                "Отделка Окору",
                "Отделка Собиратель",
                "Отделка Берег",
                "Отделка Вождь",
                "Отделка Вредина",
                "Отделка Дебри",
                "Отделка Дюна",
                "Отделка Искатель",
                "Отделка Око",
                "Отделка Ось",
                "Отделка Поток",
                "Отделка Прилив",
                "Отделка Ребро",
                "Отделка Рыло",
                "Отделка Скульптор",
                "Отделка Сборщик",
                "Отделка Страж",
                "Отделка Тишина",
                "Отделка Хранитель",
                "Отделка Шпиль"
        };

        for (String label : deprecated) {
            if (config.removeTarget(label)) {
                removed++;
            }
        }

        return removed;
    }

    public static int applyCatalogPatch(AutoBuyConfig config) {
        return mergeInto(config);
    }

    public static void resetInto(AutoBuyConfig config) {
        if (config == null) {
            return;
        }

        config.clearTargets();
        for (TargetConfig target : createScriptTargets()) {
            config.addTarget(target);
        }
    }

    private static int reorderKnownTargets(AutoBuyConfig config) {
        if (config == null) {
            return 0;
        }

        List<TargetConfig> current = new ArrayList<TargetConfig>(config.getTargets());
        if (current.isEmpty()) {
            return 0;
        }

        List<TargetConfig> reordered = new ArrayList<TargetConfig>();
        for (TargetConfig catalogTarget : createScriptTargets()) {
            TargetConfig existing = findTargetInList(current, catalogTarget.getLabel());
            if (existing != null && !reordered.contains(existing)) {
                reordered.add(existing);
            }
        }

        // Keep user-created/custom targets after the built-in catalog targets, in the
        // same relative order as the user already had them. This moves newly-added
        // built-ins like Сфера Афины into the sphere group instead of leaving them at
        // the very end of the GUI list.
        for (TargetConfig target : current) {
            if (target != null && !reordered.contains(target)) {
                reordered.add(target);
            }
        }

        if (sameOrder(current, reordered)) {
            return 0;
        }

        config.clearTargets();
        for (TargetConfig target : reordered) {
            config.addTarget(target);
        }
        return 1;
    }

    private static TargetConfig findTargetInList(List<TargetConfig> list, String label) {
        if (list == null || label == null) {
            return null;
        }
        for (TargetConfig target : list) {
            if (target != null && target.matchesLabel(label)) {
                return target;
            }
        }
        return null;
    }

    private static boolean sameOrder(List<TargetConfig> a, List<TargetConfig> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static int patchExistingTargets(AutoBuyConfig config) {
        int changed = 0;
        changed += patchTarget(config, "Обычный мист", "minecraft:campfire", "spookyevents:mythic\":\"MILD");
        changed += patchTarget(config, "Богатый мист", "minecraft:campfire", "spookyevents:mythic\":\"WEAK");
        changed += patchTarget(config, "Легендарный мист", "minecraft:soul_campfire", "spookyevents:mythic\":\"MEDIUM");
        changed += patchTarget(config, "Модификатор полёта", "minecraft:feather", NEVER_FLY_MODIFIER_TAG);
        changed += patchTarget(config, "Модификатор починки", "minecraft:bubble_coral", "spooky-item\":\"modifier-item-fix-day");

        // Step 23.35: SpongeBob currency NBT was updated from real 1.21.4 dumps.
        // These items are not player heads on this server: burger is pumpkin_pie,
        // formula is guster_banner_pattern. Keep the stable PublicBukkitValues keys.
        changed += patchTarget(config, "Крабсбургер", "minecraft:pumpkin_pie", "spookystash:currency\":\"burger");
        changed += patchTarget(config, "Формула крабсбургера", "minecraft:guster_banner_pattern", "spookystash:currency\":\"formula");

        // Step 22.78: restore real vanilla item ids for custom/script items so GUI icons
        // are not rendered as paper and matcher can prefilter them safely.
        changed += patchTarget(config, "Божья Аура", "minecraft:phantom_membrane", "effect-item-god");
        changed += patchTarget(config, "Божье касание", "minecraft:golden_pickaxe", "spawner-item-spawner-break");
        changed += patchTarget(config, "Молот Тора", "minecraft:netherite_pickaxe", "radius-item-mega-buldozer");
        changed += patchTarget(config, "Трапка", "minecraft:netherite_scrap", "schematic-item-trap");
        changed += patchTarget(config, "Отмычка к сферам", "minecraft:tripwire_hook", NEVER_KEY_SPHERES_TAG);
        changed += patchTarget(config, "Спавнер", "minecraft:spawner", "");

        // Use stable custom item keys plus required enchants. Do not include Damage here:
        // damage/durability changes on armor, so a Damage-based tag makes valid lots fail.
        // Thorns III is optional for chestplate-kryshitel and must not be required.
        changed += patchTarget(config, "Нагрудник крушителя", "minecraft:netherite_chestplate", CHESTPLATE_ENCHANTS);
        changed += patchTarget(config, "Ботинки крушителя", "minecraft:netherite_boots", BOOTS_ENCHANTS);
        changed += patchTarget(config, "Элитры Крушителя", "minecraft:elytra", "elytra-kryshitel&&" + ELYTRA_ENCHANTS);
        changed += patchTarget(config, "Зачарованное золотое яблоко", "minecraft:enchanted_golden_apple", "");
        changed += patchTarget(config, "Череп визер-скелета", "minecraft:wither_skeleton_skull", "");
        changed += patchTarget(config, "Голова визер-скелета", "minecraft:wither_skeleton_skull", "");
        changed += patchTarget(config, "Маяк", "minecraft:beacon", "");
        changed += patchTarget(config, "Шалкер", "minecraft:shulker_box", "");

        changed += patchTarget(config, "Шлем крушителя", "minecraft:netherite_helmet", HELMET_ENCHANTS);
        changed += patchTarget(config, "Поножи крушителя", "minecraft:netherite_leggings", ARMOR_DEFAULT_ENCHANTS);
        changed += patchTarget(config, "Трезубец крушителя", "minecraft:trident", "trident-kryshitel&&" + TRIDENT_ENCHANTS);
        changed += patchTarget(config, "Меч крушителя", "minecraft:netherite_sword", SWORD_ENCHANTS + "&&" + NEVER_SWORD_TOOLTIP);
        changed += patchTarget(config, "Арбалет крушителя", "minecraft:crossbow", "crossbow-kryshitel&&" + CROSSBOW_ENCHANTS);
        changed += patchTarget(config, "Кирка крушителя", "minecraft:netherite_pickaxe", PICKAXE_ENCHANTS + "&&" + NEVER_PICKAXE_TOOLTIP);
        changed += patchTarget(config, "Книга починка", "minecraft:enchanted_book", "minecraft:mending");
        changed += patchTarget(config, "Хлопушка", "minecraft:splash_potion", "custompotioncolor:16738740&&хлопушка");
        changed += patchTarget(config, "Святая вода", "minecraft:splash_potion", "custompotioncolor:16777215&&святая вода");
        changed += patchTarget(config, "Зелье гнева", "minecraft:splash_potion", "custompotioncolor:10040115&&зелье гнева");
        changed += patchTarget(config, "Зелье палладина", "minecraft:splash_potion", "custompotioncolor:65535&&зелье палладина||зелье паладина");
        changed += patchTarget(config, "Зелье ассасина", "minecraft:splash_potion", "custompotioncolor:3355443&&зелье ассасина");
        changed += patchTarget(config, "Зелье радиации", "minecraft:splash_potion", "custompotioncolor:3329330&&зелье радиации");
        changed += patchTarget(config, "Снотворное", "minecraft:splash_potion", "custompotioncolor:4737096&&снотворное");
        changed += patchPotionTarget(config);
        changed += patchTarget(config, "Сфера Хаоса", "minecraft:player_head", "e7a7ae7cdcf616e8b7a4221a621b2435753c60ed6a258ea060dae3002ffe9e28");
        changed += patchTarget(config, "Сфера Сатира", "minecraft:player_head", "771a9a498b4fa5ec49362f9bc88eda4f52b04de49d75aa3ca332a1fea1aa0e57");
        changed += patchTarget(config, "Сфера Бестии", "minecraft:player_head", "5411ac17381b9fce9bab3c72afdb7f198570daf4732bd811d31c227d80fa39b1");
        changed += patchTarget(config, "Сфера Ареса", "minecraft:player_head", "c16adc6bafcb57fd707dee7dd6a736fe126711d53a1fd6ce789da41b3be13f2a");
        changed += patchTarget(config, "Сфера Гидры", "minecraft:player_head", "3e3c118d696d910e54de02ca4d807543f9b18c008c9838d2ff69377622fb1d32");
        changed += patchTarget(config, "Сфера Икара", "minecraft:player_head", "c6803e6d5667a2d610628bc3b32f863cda495c465616de655cb329933b61af77");
        changed += patchTarget(config, "Сфера Титана", "minecraft:player_head", "81e9698458b7841c96ae4f24ec84ae01724100641c564e2a7b185f406e8ed23");
        changed += patchTarget(config, "Сфера Эрида", "minecraft:player_head", "6e4e2f1047f3ec6e9e459184739e33b7c1fc63ad8202bdab9f024508add23e5b");
        changed += patchTarget(config, "Сфера Афины", "minecraft:player_head", "93f9eeda3ba23fe1423c4036e7dd0a74461dff96badc5b2f2b9faa7cc16f382f||attribute-item-safina");
        changed += patchTarget(config, "Талисман Крушителя", "minecraft:totem_of_undying", "талисман крушителя");
        changed += patchTarget(config, "Талисман Карателя", "minecraft:totem_of_undying", "талисман карателя");
        changed += patchTarget(config, "Талисман Раздора", "minecraft:totem_of_undying", "талисман раздора");
        changed += patchTarget(config, "Талисман Тирана", "minecraft:totem_of_undying", "талисман тирана");
        changed += patchTarget(config, "Талисман Ярости", "minecraft:totem_of_undying", "талисман ярости");
        changed += patchTarget(config, "Булава Крушителя", "minecraft:mace", MACE_CRUSH_ENCHANTS + "&&" + MACE_CRUSH_TOOLTIP);
        changed += patchTarget(config, "Обычная булава", "minecraft:mace", "");
        changed += patchTarget(config, "Навершие булавы", "minecraft:heavy_core", "");
        changed += patchTarget(config, "Голова пиглина", "minecraft:piglin_head", "");
        changed += patchTarget(config, "Яйцо дракона", "minecraft:dragon_egg", "");
        changed += patchTarget(config, "Яйцо нюхача", "minecraft:sniffer_egg", "");
        changed += patchTarget(config, "Яйцо голема", "minecraft:iron_golem_spawn_egg", "");
        return changed;
    }

    private static int patchPotionTarget(AutoBuyConfig config) {
        TargetConfig target = config.findTarget("Несоздаваемое зелье");
        if (target == null) {
            return 0;
        }

        int changed = patchTarget(config, "Несоздаваемое зелье", "minecraft:potion", "effect:speed:3600:2&&effect:strength:3600:2");

        // Step 22.57: for this potion only, unstackAmount is used as the amount of
        // inventory slots for LMB drag distribution. Old configs from Step 22.56
        // have value 1, which means "use the new default 6 slots".
        if (target.isUnstack() && target.getUnstackAmount() <= 1) {
            target.setUnstackAmount(6);
            changed++;
        }

        return changed > 0 ? 1 : 0;
    }

    private static int patchTarget(AutoBuyConfig config, String label, String itemId, String tagContains) {
        TargetConfig target = config.findTarget(label);
        if (target == null) {
            return 0;
        }

        boolean changed = false;
        String safeItemId = itemId == null ? "" : itemId;
        String safeTag = tagContains == null ? "" : tagContains;

        if (!safeItemId.equals(target.getItemId())) {
            target.setItemId(safeItemId);
            changed = true;
        }

        if (!safeTag.equals(target.getTagContains())) {
            target.setTagContains(safeTag);
            changed = true;
        }

        return changed ? 1 : 0;
    }

    private static void add(List<TargetConfig> list, String label, String itemId, long maxUnitPrice, String... contains) {
        TargetConfig target = new TargetConfig(label, Arrays.asList(contains), itemId, "", maxUnitPrice, 0L, false, false, 1, false);
        list.add(target);
    }

    private static void addTagged(List<TargetConfig> list, String label, String itemId, String tagContains, long maxUnitPrice, String... contains) {
        TargetConfig target = new TargetConfig(label, Arrays.asList(contains), itemId, tagContains, maxUnitPrice, 0L, false, false, 1, false);
        list.add(target);
    }

    private static void addTaggedUnstack(List<TargetConfig> list, String label, String itemId, String tagContains, long maxUnitPrice, boolean unstack, int unstackAmount, String... contains) {
        TargetConfig target = new TargetConfig(label, Arrays.asList(contains), itemId, tagContains, maxUnitPrice, 0L, false, unstack, unstackAmount, false);
        list.add(target);
    }
}
