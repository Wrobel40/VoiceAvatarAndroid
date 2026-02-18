package com.aiavatar.app.avatar

enum class AvatarStyle(
    val displayName: String,
    val emoji: String,
    val description: String,
    val bodyColor1: Long,
    val bodyColor2: Long,
    val armorColor1: Long,
    val armorColor2: Long,
    val reactorColor: Long,
    val eyeColor: Long,
    val particleColor: Long,
    val glowColor: Long
) {
    DARK_KNIGHT(
        "Dark Knight", "⚔️", "Czarny neon + miecz",
        0xFF0D0D0D, 0xFF050505,
        0xFF1A1A1A, 0xFF111111,
        0xFFFF2200, 0xFFFF3300,
        0xFFFF4400, 0xFFDD1100
    ),
    CYBER_KNIGHT(
        "Cyber Knight", "🛡️", "Granatowy rycerz",
        0xFF0D1B4B, 0xFF060E2E,
        0xFF1A3A8F, 0xFF0A1F5C,
        0xFF00E5FF, 0xFF00E5FF,
        0xFF00CCFF, 0xFF0A4AD4
    ),
    GHOST(
        "Ghost", "👻", "Widmo - biało-srebrny",
        0xFF1A1A2E, 0xFF0D0D1A,
        0xFF2A2A4A, 0xFF1A1A35,
        0xFFCCDDFF, 0xFFCCEEFF,
        0xFFAABBFF, 0xFF4455AA
    ),
    LAVA(
        "Lava", "🔥", "Ognisty wojownik",
        0xFF2D0A00, 0xFF1A0500,
        0xFF6B1A00, 0xFF3D0F00,
        0xFFFF6600, 0xFFFF4400,
        0xFFFF8800, 0xFFCC3300
    ),
    FOREST(
        "Forest", "🌿", "Leśny strażnik",
        0xFF0A1F0A, 0xFF051005,
        0xFF1A4A1A, 0xFF0F2E0F,
        0xFF44FF88, 0xFF66FF66,
        0xFF44DD66, 0xFF226633
    ),
    VOID(
        "Void", "🌌", "Kosmiczny wojownik",
        0xFF0D001A, 0xFF080010,
        0xFF2A0050, 0xFF1A0035,
        0xFFAA44FF, 0xFFCC88FF,
        0xFF9933FF, 0xFF6600CC
    ),
    GOLD(
        "Gold Elite", "👑", "Złoty elitarny",
        0xFF1A1000, 0xFF0F0A00,
        0xFF4A3200, 0xFF2E1F00,
        0xFFFFCC00, 0xFFFFDD44,
        0xFFFFAA00, 0xFFAA7700
    )
}
