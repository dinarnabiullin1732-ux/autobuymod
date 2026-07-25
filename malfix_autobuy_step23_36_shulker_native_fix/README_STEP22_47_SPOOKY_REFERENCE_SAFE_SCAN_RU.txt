STEP 22.47 — SpookyBuy reference matcher / safe fast scan

Что взято из spookybuy-2.5.3-ForkNeverMods.jar как опора:
- порядок проверки как у CollectItem.CHECKER: сначала базовый minecraft item, потом tag/tooltip/enchant/effect/attributes;
- название предмета не считается доказательством для custom/server предметов;
- талисманы проверяются через AttributeModifiers, а не через display name;
- для обычных vanilla-ресурсов используется быстрый exact itemId path без лишнего tooltip/NBT чтения.

Что изменено:
- ItemMatcher больше не читает tooltip/NBT перед обычным vanilla match;
- custom/protected targets без tag/attribute proof не покупаются;
- для старых/ручных конфигов добавлено безопасное infer itemId для популярных vanilla предметов:
  незеритовый слиток/лом, обсидиан, алмаз, порох, яблоки, тотем, головы/яйца и т.д.;
- renamed fake item не должен пройти как vanilla commodity, если фактический itemId другой;
- renamed totem не должен пройти как талисман: нужен attribute fingerprint.

Важно:
- если серверный/custom предмет добавляется вручную, лучше задавать itemId + tagContains;
- если itemId/tagContains пустые, matcher намеренно становится строгим и может пропустить предмет ради защиты от фейков;
- этот шаг не меняет buy click, seller, restack, refresh timing.
