# MiniGamePlayground

Minecraft 1.20.1 / Fabric 服务端小游戏场地搭建与玩家对战平衡工具。

MiniGamePlayground 会根据玩家当前的最大生命值和场地基准，动态调整玩家受到的伤害与治疗，并提供区域保护、耐久保护、按存档隔离的地形快照、场地重置、区域创造飞行和鞘翅滑行控制。

每个 playground 可以独立选择伤害模式：

- `pvp`：只平衡同一区域内的玩家对玩家伤害；
- `all`：只要求受击者在区域内，玩家、生物、投射物、无来源、自伤和环境伤害都参与平衡。

- 纯服务端模组，客户端不需要安装。
- 不修改玩家的 `MAX_HEALTH` 属性。
- 不取消伤害后重新施加，避免递归伤害和击退异常。
- 兼容 TaCZ、Spell Engine、Better Combat、Simply Swords 等走原版伤害/治疗流程的模组。
- 对其他模组在 `getDamageAfterMagicAbsorb` 返回点覆盖返回值、导致原版回调被跳过的情况，由可选附属提供最终生命值兜底缩放。
- 阻止原版活塞把区域方块带出区域后复制，并阻止机械钻头/锯等 `Block.getDrops` 路径在区域内刷取掉落物。
- 区域定义和快照保存在当前存档内，不同存档不会互相覆盖。
- 每个区域可以禁止生存/冒险/极限模式玩家创造飞行；重叠区域按“任一允许即允许”处理。
- 每个区域可以允许或禁止鞘翅滑行，默认允许；重叠区域按“任一允许即允许”处理。
- Create Contraption 的防复制作为独立附属模组 `minigameplayground-create` 提供。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| Minecraft | 1.20.1 |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.92.6+1.20.1 |
| Java | 17+ |
| 客户端 | 不需要安装本模组 |
| WorldEdit | 必需，7.2.15+ |
| Create | 可选，安装附属模组时需要 6.0.8+ |

## 构建与安装

构建：

```bash
./gradlew build
```

产物：

```text
build/libs/minigameplayground-2.5.3.jar
```

发布 jar 不会捆绑 WorldEdit 或 Fabric API。

安装：

1. 把 jar 放入服务端的 `mods` 目录。
2. 从旧版升级时，先移除旧的 `pvpbalance-*.jar`；旧全局区域和快照不会自动迁移，需要在新存档重新创建或手动复制。
3. 服务端安装 Fabric Loader 0.18.4、Fabric API 和 WorldEdit 7.2.15+。
4. 启动服务器。
5. 使用 OP 权限等级 2 执行 `/mgp` 指令。

如需 Create Contraption 防复制，再安装同目录下的
`minigameplayground-create` 附属模组和服务端 Create 6.0.8+。

客户端没有安装本模组也可以正常进入服务器。`/mgp reset` 的 Title 提示使用原版数据包。

## 快速开始

```text
# 1. 创建场地，默认伤害基准 20、恢复基准 20、不消耗耐久
/mgp create arena_1 ~ ~ ~ ~20 ~10 ~20

# 2. 按需要调整基准
/mgp damage arena_1 24.0
/mgp heal arena_1 30.0

# 3. 检查自己的位置和场地状态
/mgp check

# 4. 查看某个玩家的倍率
/mgp damage_rate Steve arena_1
/mgp heal_rate Steve arena_1

# 5. 设置伤害模式（默认 pvp）
/mgp damage_mode arena_1 all
```

## 指令

主指令：`/mgp`
兼容别名：`/minigameplayground`
权限：OP 等级 2

| 指令 | 说明 |
| --- | --- |
| `/mgp create <区域ID> <x1> <y1> <z1> <x2> <y2> <z2>` | 在当前维度创建区域；坐标支持 `~` |
| `/mgp create <区域ID> dimension <维度ID> <x1> <y1> <z1> <x2> <y2> <z2>` | 在指定维度创建区域 |
| `/mgp remove <区域ID>` | 删除区域及其快照 |
| `/mgp list [维度ID\|区域模式]` | 列出全部区域；含冒号的参数按维度过滤，否则按区域模式过滤，支持 `*`、`?` 通配符 |
| `/mgp damage <区域ID> <伤害基准\|disable>` | 设置伤害基准，或禁用区域内伤害 |
| `/mgp heal <区域ID> <恢复基准\|disable>` | 设置恢复基准，或禁用区域内治疗 |
| `/mgp damage_mode <区域ID> <pvp\|all>` | 设置该区域的伤害平衡模式；不填模式时查询当前值 |
| `/mgp flight <区域ID> [true\|false]` | 查看/设置生存、冒险、极限模式玩家的区域创造飞行；默认禁止 |
| `/mgp falldamage <区域ID> [2-1000\|disable]` | 查看/设置摔落受伤高度；默认 3，最低 2，`disable` 表示不受摔落伤害 |
| `/mgp mobdrops <区域ID> [true\|false]` | 查看/设置区域内 Mob 是否生成物品掉落；默认禁止 |
| `/mgp keepinventory <区域ID> [true\|false]` | 查看/设置区域内死亡是否保留物品和经验；`true` = 强制保留，`false` = 跟随世界规则 |
| `/mgp simplyskills <区域ID> [enabled\|disabled]` | 查看/设置区域内是否禁用 Simply Skills；需要安装兼容附属 |
| `/mgp elytra <区域ID> [true\|false]` | 查看/设置区域内是否允许鞘翅滑行；默认允许，重叠区域任意一个允许即允许 |
| `/mgp unbreakable <区域ID> <true\|false>` | 开启/关闭区域内耐久保护 |
| `/mgp reset <区域ID\|区域模式>` | 清除区域实体、恢复方块/方块实体和快照实体；支持通配符批量处理 |
| `/mgp snapshot <区域ID\|区域模式>` | 重新生成当前地形和实体快照；支持通配符批量处理 |
| `/mgp damage_rate <玩家> [区域ID]` | 查看伤害倍率 |
| `/mgp heal_rate <玩家> [区域ID]` | 查看恢复倍率 |
| `/mgp check` | 检查自己是否在场地内 |
| `/mgp reload` | 重新加载区域配置 |
| `/mgp help` | 显示游戏内帮助 |

### 创建区域

```text
/mgp create arena_1 ~ ~ ~ ~20 ~10 ~20
```

- 新区域的维度取执行命令时所在的维度。
- 新区域默认伤害基准 `20.0`。
- 新区域默认恢复基准 `20.0`。
- 新区域默认伤害模式为 `pvp`。
- 新区域默认开启耐久保护，即不消耗物品耐久。
- 新区域默认不强制库存保留，死亡时跟随世界 `keepInventory` 规则。
- 新区域默认启用 Simply Skills；需要安装兼容附属才能在区域内禁用。
- 新区域默认允许鞘翅滑行。
- 再次对同一 ID 执行 `create` 会更新范围、维度和快照，保留原有基准、耐久、disable、伤害模式、库存保留、Simply Skills 和鞘翅设置。
- 单个区域体积上限由 `config/minigameplayground.json` 的 `maxZoneVolume` 控制，默认 `500,000`，最大 `20,000,000`。

控制台默认位于主世界。可以在其他维度创建区域：

```text
# 方式一：切换命令执行维度
/execute in minecraft:the_nether run mgp create nether_arena ~ ~ ~ ~20 ~10 ~20

# 方式二：直接指定维度 ID
/mgp create nether_arena dimension minecraft:the_nether ~ ~ ~ ~20 ~10 ~20
```

支持模组新增维度，例如：

```text
/mgp create custom_arena dimension mymod:custom_dimension ~ ~ ~ ~20 ~10 ~20
/mgp list mymod:custom_dimension
```

区域 ID 和维度 ID 是两个不同的东西：

- 区域 ID 是 `nether_arena` 这种自定义名称，用于后续 `/mgp reset`、`/mgp snapshot` 等操作。
- 维度 ID 是 `minecraft:the_nether`、`mymod:custom_dimension` 这种实际维度标识。
- 同一维度可以有多个区域，所以 `reset`/`snapshot` 仍然使用区域 ID，并自动读取该区域记录的维度。

#### 维度解析规则

`dimension` 是一个独立的关键字，不能省略。区域 ID 后面的第一个 token 会决定走哪条分支：

| 输入 | 解析结果 |
| --- | --- |
| `/mgp create arena_1 ~ ~ ~ ~20 ~10 ~20` | 使用执行命令时所在维度 |
| `/mgp create arena_1 dimension minecraft:the_nether ~ ~ ~ ~20 ~10 ~20` | 使用指定维度 |
| `/mgp create arena_1 minecraft:the_nether ~ ~ ~ ~20 ~10 ~20` | 解析失败：`minecraft:the_nether` 会被当成坐标 |
| `/mgp create arena_1 dimension ~ ~ ~ ~20 ~10 ~20` | 解析失败：`~` 不是维度 ID |
| `/mgp create arena_1 dimension minecraft:the_nether ~ ~ ~` | 命令不完整：缺少第二个坐标点 |
| `/mgp create arena_1` | 命令不完整：缺少坐标 |

两条分支不会自动回退：

- 不写 `dimension` 时，第一条分支要求后面直接是两组坐标。
- 一旦写了 `dimension`，就必须继续写有效的维度 ID 和完整坐标；中途失败不会退回“当前维度”模式。

维度 ID 支持 Tab 补全，补全来源是服务器当前加载的所有维度，包括模组新增维度。

### 设置基准

```text
/mgp damage arena_1 24.0
/mgp heal arena_1 30.0
```

设置数值时会自动重新启用对应的伤害/恢复。

### 禁用伤害或恢复

```text
/mgp damage arena_1 disable
/mgp heal arena_1 disable
```

- `damage disable`：区域内玩家受到的所有普通伤害变为 0，即安全区。
  - 虚空伤害 `minecraft:out_of_world` 仍然生效。
  - `minecraft:generic_kill` 仍然生效。
  - 原版 `/kill` 仍然可以正常击杀。
- `heal disable`：区域内玩家的所有 `heal()` 治疗变为 0。
  - 包括自然回血、生命恢复效果、瞬间治疗和模组治疗。
  - Health Boost、直接 `setHealth()` 不受影响。
- 禁用状态下，`/mgp damage_rate` 和 `/mgp heal_rate` 会显示“已禁用”。

重新设置数值即可恢复：

```text
/mgp damage arena_1 20
/mgp heal arena_1 20
```

### 耐久保护

```text
/mgp unbreakable arena_1 true
/mgp unbreakable arena_1 false
```

- `true`：区域内玩家不消耗物品耐久（默认）。
- `false`：正常消耗耐久。

控制范围是区域内玩家的所有可损坏物品，包括武器、盔甲、盾牌、弓弩和工具。

## 创造飞行

每个区域默认禁止生存/冒险/极限模式玩家使用创造飞行：

```text
/mgp flight arena_1 true
/mgp flight arena_1 false
/mgp flight arena_1
```

规则：

- 创造/旁观模式始终允许飞行，不受区域设置影响；
- 重叠区域采用宽松规则：只要玩家所在的任意一个区域允许，就允许飞行；
- 禁止区域会统一压制 `mayfly` 和 `flying`，包括翅膀、飞行药水、管理员 `/fly` 等所有来源；
- 压制同时发生在玩家 tick 开始和结束：开始阶段先清除飞行状态，避免 `fallDistance` 被飞行状态每 tick 重置，从而恢复正常的摔落伤害；
- 对 PlayerAbilityLib / Winged 等 PAL 飞行来源，使用 PAL 的启用回调阻止来源生效并刷新 tracker，而不是每 tick 强行改写 `mayfly`，避免 PAL 重新授予飞行和刷屏警告；
- 摔落伤害仍然经过区域伤害平衡，例如伤害基准 `90`、玩家最大生命 `20` 时会被缩放到约 `20/90`；
- 不做装置检测；如果地图作者希望升腾台座等装置生效，可以围绕装置创建一个 `flight=true` 的小区域；
- 离开禁止区域后只释放压制，不主动授予飞行。PlayerAbilityLib 来源会在离开时刷新；效果过期或装备已移除时不会恢复；
- 管理员 `/fly` 在禁止区域内也会被压制，离开后需要重新执行。

## 摔落受伤高度

每个区域可以独立设置摔落受伤高度：

```text
/mgp falldamage arena_1 3
/mgp falldamage arena_1 5
/mgp falldamage arena_1 disable
/mgp falldamage arena_1
```

规则：

- 默认值 `3`，使用原版公式 `ceil((摔落距离 - 高度 - 跳跃提升) * 摔落保护倍率)`；
- 最低可设置 `2`；
- `disable` 表示该区域完全不受摔落伤害；
- 正好等于设定高度时原版公式为 0 点，超过后开始受 1 点，之后每多一格增加 1 点；
- 跳跃提升和摔落保护附魔仍然生效；
- 摔落伤害跟随区域伤害模式：`all` 模式参与伤害平衡，`pvp` 模式不参与；
- 重叠区域采用“更保护玩家”的规则：任意一个区域设置 `disable` 就禁用摔落伤害，否则取最高的受伤高度。

## 伤害模式

每个区域独立保存伤害模式：

```text
/mgp damage_mode <区域ID> pvp|all
```

- `pvp`（默认）：受击者和攻击者必须是玩家，且双方位于同一区域。
- `all`：只要受击者是玩家且位于区域内，玩家、僵尸/骷髅等生物、投射物、无来源伤害、自伤以及摔落/火焰/岩浆等环境伤害都会平衡。
- 两种模式都跳过虚空伤害和 `minecraft:generic_kill`。
- 区域 `damage disable` 仍然优先，会取消普通伤害。

`all` 会放大环境伤害：例如伤害基准 2、最大生命 100 时，1 点摔落伤害会变成 50。请根据场地用途选择。

## 伤害基准

伤害缩放使用**减伤后的基础伤害**。

基础伤害的结算顺序：

```text
受击冷却 / 无敌帧
-> 盾牌、冻结、头盔等
-> 盔甲、盔甲韧性
-> 抗性提升、保护附魔
-> 本模组应用伤害倍率
-> 后续原版伤害流程
```

计算方式：

```text
伤害倍率 = 受击者最大生命值 / 区域伤害基准

如果 减伤后伤害 < 伤害基准：
    最终伤害 = round2(减伤后伤害 × 伤害倍率)
否则：
    最终伤害 = 受击者最大生命值
```

规则：

- 最终伤害不会超过受击者最大生命值。
- 超过伤害基准的部分不再参与计算，避免浮点溢出。
- 结果保留两位小数。
- 四舍五入为 `0.00` 时仍会走原版受击流程，保留受击动画和音效，并且仍然消耗原版受击冷却。
- 默认 `pvp` 模式：只有受击者和攻击者处于同一个游戏区域时，伤害倍率才会生效。
- `all` 模式：只要受击者在区域内，玩家、生物、投射物、无来源、自伤和环境伤害都会平衡；虚空伤害和 `/kill` 仍然跳过。

说明：

- 达到伤害基准表示**减伤后**已经达到最大生命值上限；后续原版流程仍会继续处理。
- 物理/魔法减伤仍然会正常参与，不会被本模组绕过。
- 盔甲耐久消耗按缩放前的伤害计算。

## 恢复基准

恢复缩放使用：

```text
恢复倍率 = 玩家最大生命值 / 区域恢复基准

如果 原始恢复量 < 恢复基准：
    最终恢复量 = round2(原始恢复量 × 恢复倍率)
否则：
    最终恢复量 = 玩家最大生命值
```

规则：

- 最终恢复量不会超过玩家最大生命值。
- 结果保留两位小数。
- 四舍五入为 `0.00` 时治疗 0。
- 只要玩家在游戏区域内就会生效，不需要正在战斗。

覆盖范围：

- 饱食度/饱和度自然回血
- 生命恢复（Regeneration）效果
- 瞬间治疗（Instant Health）效果
- 食物、药水、模组技能等最终调用 `heal()` 的治疗

如果模组直接写入生命值（例如 `setHealth()`），不会经过恢复倍率。

## 区域库存保留

```text
/mgp keepinventory <区域ID> true
/mgp keepinventory <区域ID> false
```

- `true`：玩家在区域内死亡时保留物品栏、盔甲、副手和经验。
- `false`（默认）：不覆盖世界规则，由 `keepInventory` 游戏规则决定。
- 重叠区域采用宽松规则：任意一个区域开启库存保留，就保留。
- 只影响玩家死亡掉落，不影响玩家主动丢弃物品。

## Simply Skills 兼容

核心模组只提供区域开关：

```text
/mgp simplyskills <区域ID> disabled
```

真正移除 Simply Skills 效果、关闭 Undying/护盾/闪避/DDR、技能被动和属性奖励的逻辑由可选附属
`minigameplayground-simplyskills` 提供。未安装附属时该开关仅记录在区域配置中，
不会改变 Simply Skills 行为。

详细冲突列表和禁用范围见附属模组的 README。

## 鞘翅滑行

```text
/mgp elytra <区域ID> true
/mgp elytra <区域ID> false
```

- 默认 `true`，允许区域内使用鞘翅滑行；
- `false` 时，玩家在区域内无法开始或继续滑行；
- 重叠区域采用宽松规则：任意一个区域允许，就允许滑行；
- 进入区域后在玩家 tick 开始时立即生效。

## 场地保护

### 区域内方块无掉落物

以下路径生成的掉落物会在游戏区域内被取消：

- 方块挖掘、爆炸等 `Block.dropResources(...)`
- `Block.getDrops(...)`，包括 Create 钻头、机械锯、收割器等机械方块破坏路径
- `Block.popResource(...)` / `popResourceFromFace(...)`
- 箱子、木桶等容器的 `Containers.dropContents(...)`

玩家按 Q 丢弃物品，以及通过丢弃物品触发的技能，不受影响。

### 防复制：原版活塞

- 当一个活塞推动的方块源位置在区域内、目标位置在区域外时，只吞掉这一个源方块。
- 同一次推动中的其他方块继续正常移动。
- 被吞掉的方块不产生掉落物；`/mgp reset` 可以恢复。
- 目标位置在区域内，或源方块本来就在区域外时，不拦截。
- 其他模组的方块移动系统暂不覆盖。

Create Contraption 的组装/拆卸、矿车 NBT 和多区域判断放在独立附属模组
`minigameplayground-create` 中，避免核心模组强制依赖 Create。

### 快照与重置

- 执行 `/mgp create` 时自动生成方块和实体快照。
- `/mgp snapshot <区域ID>` 可用当前状态重新生成快照。
- `/mgp reset <区域ID>` 会：
  1. 向场地内玩家发送重置提示；
  2. 先让骑乘被清除载具的玩家下坐骑；
  3. 清除区域内所有非玩家实体；
  4. 将方块、方块实体和快照实体恢复到快照状态。
- 快照会记录创建时的维度，控制台执行重置时会定位到正确维度。
- 旧版本创建、没有快照的区域，请先执行一次 `/mgp snapshot <区域ID>`。
- 玩家不会进入实体快照，不会被保存、复制、删除或传送。载具即使有玩家乘客也会保存为“空载具”，玩家在重置前会被安全下坐骑。
- 旧快照没有实体文件时仍按旧行为恢复：只恢复方块/方块实体并清理掉落物。
- 普通实体、载具、盔甲架、掉落物等由核心模组保存；Create 实体（含 Contraption）由 `minigameplayground-create` 附属通过核心的 `ZoneSnapshotExtension` API 单独保存。未安装附属时核心不会接管 Create 实体。

### 通配符批量操作

`list`、`reset`、`snapshot` 支持简单通配符：

| 通配符 | 含义 |
| --- | --- |
| `*` | 匹配任意长度的任意字符 |
| `?` | 匹配单个字符 |

```text
/mgp list arena_*
/mgp reset arena_*
/mgp snapshot arena_door?
```

规则：

- 精确区域 ID 仍然可用，例如 `/mgp reset arena`；
- 匹配和通配符区分大小写，不支持正则表达式；
- 匹配结果按区域创建/加载顺序执行；
- 单次最多处理 `32` 个区域，超过上限会直接拒绝，不执行任何操作；
- 没有匹配项会报错，不执行任何操作；
- 批量执行时逐区域报告成功或失败，最后输出成功/失败数量；
- 批量 `reset` 按展开顺序依次恢复，嵌套区域仍然是“后执行的覆盖先执行的”，需要固定叠加顺序时请控制匹配范围；
- `/mgp list` 的参数含 `:` 时按维度过滤（例如 `minecraft:the_nether`），否则按区域模式过滤。

### 嵌套区域

两个区域互相独立，没有父子关系，也没有自动级联：

- `A` 是外层区域，`B` 完全包含在 `A` 内；
- `/mgp reset A` 会用 A 的快照恢复整个 A 范围，包括 B 覆盖的方块和实体；
- `/mgp reset B` 只恢复 B 范围，A 中 B 以外的部分不动；
- B 的区域配置和 B 的快照文件不会被 A 的 reset 修改；
- 重叠部分遵循“最后一次 reset 覆盖前一次”。

推荐用法：

1. 先把 A 和 B 的最终结构做好；
2. 对 A 执行 `/mgp snapshot A`，让 A 的快照包含 B；
3. 不要单独 reset B，避免 B 的旧快照和 A 的最新快照产生接缝。

如果 A 的快照是在 B 建好之前生成的，`reset A` 会删除 B 的结构；之后只能靠 B 自己的快照恢复 B。

快照实现：

- WorldEdit 是硬依赖，使用 `.schem` 格式和 WorldEdit 粘贴 API。
- 快照写入当前存档的 `minigameplayground/snapshots/` 目录（例如 `world/minigameplayground/snapshots/`），不会跨存档共享。
- 实体快照保存在同目录的 `.entities.nbt` 和附属扩展文件（例如 `.entities-create.nbt`）中。
- `/mgp reset` 从文件读取快照后恢复，不依赖上一次命令留下的内存状态。
- 仍保留内置 `StructureTemplate` 快照作为 WorldEdit 操作异常时的兜底。

## 配置文件

区域配置和快照保存在当前存档内：

```text
<存档目录>/minigameplayground/zones.json
<存档目录>/minigameplayground/snapshots/
```

例如默认主世界：

```text
world/minigameplayground/zones.json
world/minigameplayground/snapshots/
```

不同存档使用各自独立的区域和快照，不能跨存档恢复。

旧版的全局区域和快照不再读取，也不会在启动时扫描或迁移：

```text
config/minigameplayground-zones.json
config/minigameplayground-snapshots/
```

升级后请直接在目标存档内重新创建区域并执行 `/mgp snapshot <区域ID>`。如果确实需要旧数据，请手动把对应文件复制到该存档的 `minigameplayground/` 目录。新命令使用 `/mgp` 或 `/minigameplayground`；旧的 `/pvb`、`/pvpbalance` 已移除。

通用配置：

```text
config/minigameplayground.json
```

```json
{
  "maxZoneVolume": 500000
}
```

- 默认上限 `500,000`。
- 允许设置到 `20,000,000`。
- 超过 `20,000,000` 会自动限制到 `20,000,000` 并打印警告。
- 超过 `1,000,000` 时会记录大体积警告。
- 修改后执行 `/mgp reload` 生效。
- 该上限作用于 `/mgp create`、`/mgp snapshot`、`/mgp reset`。

### 怪物掉落物

每个区域独立设置，默认禁止：

```text
/mgp mobdrops arena true
/mgp mobdrops arena false
/mgp mobdrops arena
```

- 影响所有 `Mob`，包括僵尸、牛、鸡等；
- 覆盖死亡战利品、装备掉落和 Mob 主动丢出的物品；
- 不拦截玩家按 Q 丢弃的物品；
- 不拦截经验球；
- 不拦截方块掉落，方块掉落由方块保护逻辑单独处理；
- 离开游戏区域后恢复正常掉落；
- 重叠区域采用宽松规则：所在区域中任意一个允许，就允许掉落。

区域字段：

| 字段 | 说明 |
| --- | --- |
| `dimension` | 区域所在维度 |
| `standardHealth` | 伤害基准 |
| `restoreBaseline` | 恢复基准 |
| `keepDurability` | `true` = 不消耗耐久 |
| `damageDisabled` | `true` = 禁用区域伤害 |
| `healDisabled` | `true` = 禁用区域治疗 |
| `damageMode` | `"pvp"` = 仅玩家对玩家；`"all"` = 所有伤害来源 |
| `allowCreativeFlight` | `false`（默认）= 禁止生存/冒险/极限模式创造飞行；`true` = 允许 |
| `fallDamageThreshold` | `3`（默认）= 摔落受伤高度；`-1` = 禁用摔落伤害；最小有效值 `2` |
| `allowMobDrops` | `false`（默认）= 禁止区域内 Mob 物品掉落；`true` = 允许 |
| `keepInventory` | `false`（默认）= 跟随世界 `keepInventory` 游戏规则；`true` = 区域内死亡不掉落物品和经验 |
| `disableSimplySkills` | `false`（默认）= 保留 Simply Skills；`true` = 在区域内整体禁用 Simply Skills 的技能、效果和属性奖励 |
| `allowElytra` | `true`（默认）= 允许鞘翅滑行；`false` = 禁止 |

旧配置缺少新字段时使用默认值：

```text
dimension = "minecraft:overworld"
restoreBaseline = 20.0
keepDurability = true
damageDisabled = false
healDisabled = false
damageMode = "pvp"
allowCreativeFlight = false
allowMobDrops = false
keepInventory = false
disableSimplySkills = false
allowElytra = true
```

伤害和恢复计算只读取内存，不会在战斗过程中读取磁盘。

## 兼容性

- 伤害缩放基于 `LivingEntity.getDamageAfterMagicAbsorb()` 的返回值，位于盔甲、抗性、保护附魔之后。
- 恢复缩放拦截 `LivingEntity.heal(float)`。
- 耐久保护拦截 `ItemStack.hurtAndBreak(...)` 和 `ItemStack.hurt(...)`。
- TaCZ 子弹最终走 `Entity.hurt(...)` / `LivingEntity.hurt(...)`，因此可以覆盖。
- Spell Engine、Better Combat、Simply Swords、Puffish Attributes 等走原版流程的模组可以覆盖。
- SimplySkills 在 `getDamageAfterMagicAbsorb` 返回时改写返回值并取消后续回调；对应的伤害兜底代码已移到可选附属 `minigameplayground-simplyskills` 中，并恢复为覆盖所有游戏区域。
- 未安装 Simply Skills 附属时，核心模组不包含 Simply Skills 专用兜底；Simply Skills 仍可能取消核心的伤害平衡回调。
- 如需在区域内关闭 Simply Skills 的 Undying、护盾、闪避、DDR、技能被动等，安装 `minigameplayground-simplyskills` 并使用 `/mgp simplyskills <区域> disabled`。
- `/mgp elytra <区域> false` 会禁止区域内的鞘翅滑行；`true`（默认）允许。重叠区域任意一个 `true` 即可滑行。
- 完全绕过 `hurt()` / `heal()`、直接写生命值的模组无法拦截。
- `all` 模式还会处理无来源伤害和非玩家来源伤害；`pvp` 模式不会。
- MmmMmmMmmMmm 训练假人是 `Mob`，不是 `ServerPlayer`，不会触发玩家伤害/恢复倍率，可以安全用作测试目标。
- 区域按创建/更新时记录的维度隔离；相同坐标在不同维度互不影响。
- 其他模组自己的方块搬运、采掘或存储系统不会被自动拦截；如需覆盖，需要单独适配。
- Create Contraption 保护由独立模组 `minigameplayground-create` 提供；不安装它时核心模组不会加载 Create 相关 Mixin。

## 常见问题

**客户端需要安装吗？**

不需要。只有服务端需要本模组、Fabric Loader 和 Fabric API。

**为什么达到伤害基准没有直接击杀？**

伤害倍率基于减伤后的伤害；护甲、抗性、保护附魔已经先参与计算并正常生效。达到基准表示减伤后已经达到最大生命值上限，不代表无视后续所有原版机制。

**为什么 0 伤害也有受击动画？**

这是有意保留的行为。0 伤害仍然走原版受击流程，因此有动画和音效，也会消耗原版受击冷却。

**为什么连续攻击只有第一次触发伤害平衡？**

先检查场地的伤害基准。伤害基准不是“越低越安全”：

```text
倍率 = 玩家最大生命值 / 伤害基准
基准 2.0、最大生命 20 → 倍率 10
1 点徒手伤害会变成 10 点，2 点以上会直接封顶到最大生命值 20
```

因此基准 2.0 时，目标通常一两下就会死亡；死亡/重生或被打出区域后，后续攻击自然没有“共同区域内的有效命中”。测试请使用 `damage 10` 或 `damage 20`。

另外，原版受击冷却约 0.5 秒（20 tick）；服务器 TPS 较低时，实际等待时间会更长。同一目标冷却内的后续攻击会被原版忽略。等待有效冷却后，每次有效命中都会正常应用倍率。

- `pvp` 模式还要求双方仍在同一区域；
- `all` 模式只要求受击者仍在区域内。

**`damage disable` 后为什么还能被虚空或 `/kill` 杀死？**

这是安全区特例。虚空伤害、`generic_kill` 和 `/kill` 被保留，避免管理员无法处理卡在场地里的玩家。

**区域会跨维度生效吗？**

不会。区域在创建/更新时记录维度，伤害、治疗、耐久、无掉落物、快照和重置都只作用于该维度。

**模组新增维度怎么创建区域？**

使用显式维度 ID：

```text
/mgp create arena_1 dimension mymod:custom_dimension ~ ~ ~ ~20 ~10 ~20
```

也可以用 `/execute in mymod:custom_dimension run mgp create ...`。

查看模组维度中的区域：

```text
/mgp list mymod:custom_dimension
```

`/mgp list` 只有参数包含 `:` 时才按维度过滤，因此请写完整维度 ID，例如
`/mgp list minecraft:the_nether`。不含 `:` 的参数按区域模式处理，例如
`/mgp list nether_*`。

**WorldEdit 必须安装吗？**

必须。服务端需要安装 WorldEdit 7.2.15 或更高版本，否则模组不会加载。

**Create 必须安装吗？**

核心模组不强制。需要 Create Contraption 防复制时，额外安装
`minigameplayground-create` 附属模组和服务端 Create 6.0.8+。

**区域体积上限怎么改？**

编辑 `config/minigameplayground.json`：

```json
{
  "maxZoneVolume": 2000000
}
```

然后执行 `/mgp reload`。不要设置超过 `20,000,000`，超过会被自动限制。

## License

MIT. See [LICENSE](LICENSE).
