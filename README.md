# PokeFrontier — Sistema de Asalto a Bases

Mod de Fabric para Cobblemon 1.21.1 que implementa un sistema de asalto secuencial a bases de organizaciones Pokémon (Team Rocket, Team Flare, etc.).

---

## Requisitos

| Dependencia         | Versión mínima    |
|---------------------|-------------------|
| Minecraft           | 1.21.1            |
| Fabric Loader       | 0.16.10+          |
| Fabric API          | 0.116.5+1.21.1    |
| Cobblemon           | 1.7.0+1.21.1      |
| CobblemonNPCs       | 0.1.0+            |
| GooeyLibs2          | (en carpeta libs) |
| Java                | 21+               |

Opcional: CobblemonMegaShowdown (para mega-evolución de NPCs)

---

## Compilación

```bash
# Copia los JARs de CobblemonNPCs y GooeyLibs2 a la carpeta libs/
cp CobblemonNPCs-*.jar libs/
cp GooeyLibs2-*.jar libs/

# Compila
./gradlew build

# El JAR estará en: build/libs/PokeFrontier-1.0.0.jar
```

---

## Instalación

1. Copia `PokeFrontier-1.0.0.jar` a la carpeta `mods/` de tu servidor.
2. Inicia el servidor una vez para generar `config/pokefrontier/assault_config.json`.
3. Configura las bases y las arenas (ver sección de configuración).
4. Copia los YAMLs de entrenadores a `config/pokefrontier/trainers/`.

---

## Configuración rápida de una base

### 1. Definir la base en `assault_config.json`

```json
{
  "bases": [
    {
      "id": "base_rocket",
      "organization": "Team Rocket",
      "displayColor": "§c",
      "sequence": ["rocket_grunt1", "rocket_grunt2", "rocket_grunt3", "rocket_admin_archer", "giovanni"],
      "minibossIndex": 3,
      "bossIndex": 4,
      "minibossRewards": ["give {player} cobblemon:rare_candy 5"],
      "bossRewards": ["give {player} cobblemon:master_ball 1", "give {player} minecraft:diamond 10"],
      "bannedPokemon": ["cobblemon:mewtwo"],
      "banLegendaries": false,
      "arena": {
        "id": "arena_rocket",
        "playerSpawn": { "world": "minecraft:overworld", "x": 100, "y": 64, "z": 200, "yaw": 0, "pitch": 0 },
        "npcSpawn":    { "world": "minecraft:overworld", "x": 100, "y": 64, "z": 203, "yaw": 180, "pitch": 0 }
      }
    }
  ]
}
```

### 2. Configurar spawns con comandos (en lugar de editar JSON)

```
# Pararte en la posición de spawn del jugador:
/assault setarena base_rocket player

# Pararte en la posición del NPC:
/assault setarena base_rocket npc
```

### 3. Crear los entrenadores

**Opción A — Exportar desde tu equipo:**
```
/exporttrainer giovanni
```

**Opción B — GUI completa:**
```
/exporttrainer gui
```

**Opción C — Editar YAML directamente** (ver `ejemplo_bases/team_rocket_trainers.yml`)

### 4. Crear el trigger de entrada

En el mundo, crea un NPC estático, una señal o un bloque de comandos que ejecute:
```
/assault enter base_rocket
```

---

## Comandos del jugador

| Comando                    | Descripción                              |
|----------------------------|------------------------------------------|
| `/assault enter <base_id>` | Entrar a una base (o continuar progreso) |
| `/assault leave`           | Salir de la base (conserva progreso)     |
| `/assault status`          | Ver progreso en todas las bases          |

## Comandos de administrador

| Comando                               | Descripción                         |
|---------------------------------------|-------------------------------------|
| `/assault reset <base_id>`            | Reiniciar tu progreso en una base   |
| `/assault reload`                     | Recargar `assault_config.json`      |
| `/assault setarena <base_id> player`  | Guardar spawn del jugador           |
| `/assault setarena <base_id> npc`     | Guardar spawn del NPC               |
| `/exporttrainer [gui|<trainer_id>]`   | Exportar equipo como entrenador     |
| `/bfset id|name|battle|defeat|skin|cooldown|rewardcmd <valor>` | Editar campos del entrenador en GUI |

---

## Flujo secuencial

```
Jugador usa trigger → /assault enter base_rocket
    ↓
AssaultManager lee progreso guardado (ej: paso 2)
    ↓
Genera NPC del paso 2 (rocket_grunt3)
    ↓ [combate]
Jugador gana → progreso guardado en disco → genera NPC del paso 3 (archer)
    ↓ [combate] → recompensas de mini-jefe
Jugador gana → genera NPC del paso 4 (giovanni)
    ↓ [combate] → recompensas de jefe final
Base completada → sesión finalizada
```

**Si el jugador se desconecta:**
- El NPC desaparece, la sesión RAM se limpia.
- El progreso en disco se conserva.
- Al volver y usar el trigger, genera exactamente el NPC del paso guardado.

---

## Agregar una nueva organización

1. Crea YAMLs de entrenadores en `config/pokefrontier/trainers/`
2. Añade una entrada a `bases` en `assault_config.json`
3. Crea el trigger en el mundo
4. No se requiere ningún cambio en Java.

---

## Estructura de archivos

```
config/pokefrontier/
├── assault_config.json          ← Configuración global + definición de bases
├── data/
│   └── assault_progress.json   ← Progreso por jugador × base (auto-generado)
└── trainers/
    ├── rocket_grunt1.yml
    ├── giovanni.yml
    └── ...
```
