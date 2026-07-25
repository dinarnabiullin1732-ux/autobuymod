Step 22.46 — безопасный matcher для custom/protected предметов

Изменено:
- Быстрый visible-name prefilter остался только как ранний SKIP, но не может принять предмет к покупке.
- Добавлен protected/custom target режим в ItemMatcher.
- Все tagged targets проверяются только через tag/NBT/lore/searchable правило.
- Все talisman targets проверяются только через AttributeModifiers fingerprint.
- Targets без itemId и без tag больше не принимаются по одному названию, потому что это легко подделать rename'ом.
- Custom/protected серверные предметы с ванильным itemId не принимаются по displayName alone.
- Простые vanilla commodities вроде diamond/gunpowder/obsidian/netherite_ingot остаются через exact itemId + visible name/fallback.

Важно:
- Если какой-то реальный custom предмет перестал покупаться, ему нужно добавить tagContains/fingerprint, а не ослаблять matcher до name-only.
- Это safety-first patch: лучше пропустить сомнительный лот, чем купить фейк.
