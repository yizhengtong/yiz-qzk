# YizMod QZK (yiz-qzk)

A Minecraft 1.21.1 NeoForge library mod providing:

- **Effect Framework** — 6-dimension effect system (perception, unlock, activation, parent type, rarity, execution)
- **Damage System** — 14 damage types with 3 enforcement tags (TRUE_DAMAGE, ARMOR_PIERCING, PIERCE_INVULNERABILITY)
- **Health Modification System** — Priority-based modifier pipeline for healing, damage, regen, lifesteal
- **Attribute Modification Helper** — Simplified entity attribute modification with temporary/permanent support
- **Weapon System** — Abstract base weapon and melee weapon with multi-zone modifier calculation
- **Talent System** — Abstract talent framework with unlock management
- **UI System** — Custom item info overlay, talent panel, tooltip rendering
- **Mixin Integration** — Attack interception for forced damage execution

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.228+

## Getting Started

```bash
./gradlew build
```

The built JAR will be in `build/libs/`.
