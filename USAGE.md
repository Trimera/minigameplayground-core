# MiniGamePlayground — 使用与实现说明

## 构建

```bash
./gradlew build
```

产物：`build/libs/minigameplayground-2.0.1.jar`

当前项目固定使用：

- Minecraft 1.20.1
- Fabric Loader 0.18.4
- Fabric API 0.92.6+1.20.1
- WorldEdit 7.2.15+（必需）
- Java 17+（已在 Java 21 下测试）

## 安装

把 `minigameplayground-2.0.1.jar` 放入**服务端**的 `mods` 目录即可。客户端不需要安装本模组；
服务端需要 Fabric Loader 0.18.4、Fabric API 和 WorldEdit 7.2.15+。
模组只处理服务端伤害、保护与指令，不添加物品、方块或客户端界面。

WorldEdit 是必需依赖（7.2.15+）：快照使用 WorldEdit 的 `.schem` 格式和粘贴 API。安装后仍保留内置 `StructureTemplate` 快照作为异常兜底。

Create Contraption 防复制由独立附属模组 `minigameplayground-create` 提供；核心模组不强制依赖 Create。

## 指令

主指令：`/mgp`，兼容别名 `/minigameplayground`、`/pvb`、`/pvpbalance`。需要 OP 权限等级 2。

```text
/mgp create <区域ID> <x1> <y1> <z1> <x2> <y2> <z2>
/mgp create <区域ID> dimension <维度ID> <x1> <y1> <z1> <x2> <y2> <z2>
/mgp remove <区域ID>
/mgp list [维度ID]
/mgp damage <区域ID> <伤害基准|disable>
/mgp heal <区域ID> <恢复基准|disable>
/mgp unbreakable <区域ID> <true|false>
/mgp reset <区域ID>
/mgp snapshot <区域ID>
/mgp damage_rate <玩家> [区域ID]
/mgp heal_rate <玩家> [区域ID]
/mgp check
/mgp reload
/mgp help
```

`/mgp create` 不再接收伤害基准参数。新区域默认：

- 伤害基准 `20.0`
- 恢复基准 `20.0`
- 耐久保护开启（不消耗物品耐久）

新区域的维度可以两种方式指定：

```text
# 使用命令执行时所在维度
/mgp create nether_arena ~ ~ ~ ~20 ~10 ~20

# 直接指定维度 ID，适合控制台和模组维度
/mgp create nether_arena dimension minecraft:the_nether ~ ~ ~ ~20 ~10 ~20
/mgp create custom_arena dimension mymod:custom_dimension ~ ~ ~ ~20 ~10 ~20
```

- 控制台默认位于主世界。
- 也可以使用 `/execute in <维度ID> run mgp create ...`。
- 区域 ID 是自定义名称，用于 `reset`/`snapshot` 等操作。
- 维度 ID 是 `minecraft:the_nether`、`mymod:custom_dimension` 这类实际维度标识。
- 同一维度可以有多个区域，所以 `reset`/`snapshot` 仍使用区域 ID，并自动读取区域记录的维度。

如果对已存在的区域再次执行 `/mgp create`，会更新范围、维度并重新生成快照，保留原有基准、耐久和 disable 设置。

不带参数的 `/mgp` 也会显示帮助。`/mgp help` 会列出全部指令、参数格式和用途。

示例：

```text
/mgp create arena_1 ~ ~ ~ ~20 ~10 ~20
/mgp damage arena_1 24.0
/mgp heal arena_1 30.0
/mgp damage arena_2 disable
/mgp heal arena_2 disable
/mgp unbreakable arena_1 true
/mgp damage_rate Steve arena_1
/mgp heal_rate Steve arena_1
/mgp reset arena_1
```

坐标使用原版命令坐标格式，支持 `~` 相对坐标和绝对坐标。AABB 会自动取两点的 min/max。

## 计算规则

所有计算使用 `float`，最终伤害/治疗量保留两位小数。

### 伤害基准

伤害缩放基于**减伤后的基础伤害**，不是 `hurt()` 入口的原始值。

“基础伤害”指经过以下流程之后、**吸收伤害之前**的数值：

1. 受击冷却/无敌帧
2. 盾牌格挡
3. 冻结、头盔等特殊减伤
4. 盔甲、盔甲韧性
5. 抗性提升、保护类附魔

1. 受击者和攻击者必须都是玩家。
2. 两人必须处于**同一个**游戏区域内。重叠区域会取第一个同时包含两人的区域。
3. 读取受击者最终的 `getMaxHealth()`，包含等级、饰品、Puffish Attributes 等模组对生命上限的修改。
4. 如果**减伤后基础伤害** `< 区域伤害基准`：
   * 最终伤害 = `基础伤害 × 受击者最大生命值 / 伤害基准`
   * 结果保留两位小数
5. 如果**减伤后基础伤害** `>= 区域伤害基准`：
   * 最终伤害 = `受击者最大生命值`
   * 超过基准的部分不再参与计算，按致命值封顶，避免浮点溢出
6. 最终伤害不会超过受击者最大生命值。
7. 吸收伤害（黄色爱心）在倍率之后结算，因此即使达到基准，仍可能被吸收挡住。
8. 四舍五入后为 `0.00` 时，实际造成 0 伤害，但**保留原版受击动画和受击音效**，并且仍然消耗原版受击冷却。

### 关于连续攻击与低伤害基准

伤害倍率是 `最大生命值 / 伤害基准`。基准设置得过低会让普通攻击直接封顶：

```text
伤害基准 2.0、最大生命 20 → 1 点伤害变 10 点，2 点以上变 20 点
```

基准 2.0 时目标通常一两下就会死亡，死亡/重生或被打出区域后，后续攻击自然没有
共同区域内的有效命中。测试建议使用 `damage 10` 或 `damage 20`。

此外，伤害倍率只在原版 `LivingEntity.hurt` 实际处理一次受击时生效。同一目标在
约 0.5 秒（20 tick）的受击冷却内继续攻击，后续攻击会被原版忽略；服务器 TPS 较低时
实际等待时间会更长。这不是“每次进入区域只能触发一次”。

### 伤害缩放与减伤顺序

本模组在 `getDamageAfterMagicAbsorb()` 返回时缩放伤害，此时盔甲/抗性/保护附魔已经计算完成。完整顺序是：

1. `hurt()` HEAD：安全区 disable 检查
2. 受击冷却/无敌帧
3. 盾牌、冻结、头盔等
4. `actuallyHurt()` 中的盔甲、盔甲韧性
5. 抗性提升、保护类附魔
6. **本模组应用伤害倍率**
7. 吸收伤害（黄色爱心）
8. 扣除生命值

因此：

- 盔甲、抗性提升、保护附魔都会在倍率计算之前降低基础伤害。
- 达到基准表示**减伤后、吸收前**已经达到致命值，不再表示“无视减伤必杀”。
- 吸收伤害仍然在倍率之后生效。
- `lastHurt` 仍记录原版受击冷却的原始值，不会被缩放；后续冷却比较仍按原版规则。
- 盔甲耐久消耗保持原版行为，按缩放前的伤害计算，本次未修改。

### 恢复基准

1. 只要玩家位于某个游戏场地内，其受到的治疗就会按该场地的恢复基准确认倍率。
2. 如果**原始恢复量** `< 区域恢复基准`：
   * 最终恢复量 = `原始恢复量 × 玩家最大生命值 / 恢复基准`
   * 结果保留两位小数
3. 如果**原始恢复量** `>= 区域恢复基准`：
   * 最终恢复量 = `玩家最大生命值`
   * 超过基准的治疗量不再参与计算
4. 最终恢复量不会超过玩家最大生命值。
5. 四舍五入后为 `0.00` 时，实际治疗 0。

不再使用 `0.1x ~ 3.0x` 的倍率硬限制。

恢复缩放通过拦截 `LivingEntity.heal(float)` 实现，覆盖：

- 饱食度/饱和度自然回血
- 生命恢复（Regeneration）效果
- 瞬间治疗（Instant Health）效果
- 食物、药水、模组技能等最终调用 `heal()` 的治疗

如果模组直接写入生命值（例如直接调用 `setHealth()`），则不会经过恢复倍率。

模组不会修改 `MAX_HEALTH` 属性，也不会取消后重新施加伤害，因此不会触发递归伤害，
也不会破坏击退、横扫、枪械命中判定等依赖 `hurt()` 返回值的逻辑。

## 安全区与禁用

```text
/mgp damage <区域ID> disable
/mgp heal <区域ID> disable
```

- `/mgp damage <区域ID> disable`：区域内玩家受到的所有伤害变为 0（安全区）。
  * 仍然生效：虚空伤害 `minecraft:out_of_world`、`minecraft:generic_kill`、`/kill`。
  * 其他 PvP、怪物、摔落、火焰、岩浆、窒息等伤害都会被取消。
  * 使用 `/mgp damage <区域ID> <数值>` 可重新启用伤害。
- `/mgp heal <区域ID> disable`：区域内玩家的所有 `heal()` 治疗变为 0。
  * 包括自然回血、生命恢复效果、瞬间治疗和模组治疗。
  * 吸收伤害、Health Boost、直接 `setHealth()` 不受影响。
  * 使用 `/mgp heal <区域ID> <数值>` 可重新启用恢复。

`damage disable` 是真正的安全区，会取消 `hurt()`，因此不会产生受击动画和受击无敌帧；
而倍率计算得到 0 伤害时仍然走原版 `hurt()`，会保留受击动画，同时也会占用原版受击无敌帧。

伤害/恢复被禁用时，`/mgp damage_rate` 和 `/mgp heal_rate` 会显示“已禁用”。

## 耐久保护

`/mgp unbreakable <区域ID> <true|false>` 控制区域内玩家的物品耐久：

- `true`：不消耗耐久（新区域默认）
- `false`：正常消耗耐久

实现会拦截 `ItemStack.hurtAndBreak(...)` 和 `ItemStack.hurt(...)`。因此盔甲受击、
武器攻击、盾牌格挡、弓弩射击、工具使用等路径都会受到控制。为了避免漏掉模组武器和盔甲，
控制范围是区域内玩家的**所有可损坏物品**。

## 场地保护与地形回溯

### 区域内方块无掉落物

当方块位于任意游戏场地内时，以下路径生成的掉落物会被取消：

- `Block.dropResources(...)`：玩家挖掘、爆炸等方块破坏掉落。
- `Block.getDrops(...)`：机械钻头、机械锯、收割器等（包括 Create 的对应设备）直接读取方块掉落列表的路径。
- `Block.popResource(...)` / `popResourceFromFace(...)`：方块直接生成的掉落物。
- `Containers.dropContents(...)`：箱子、木桶等容器被破坏时的内容物。

玩家按 Q 丢弃物品走的是 `Player.drop`，不经过上述方法，因此仍会正常掉落；
通过丢弃物品触发的剑气/技能弹射物也不受影响。

### 防复制：活塞

原版 `PistonStructureResolver` 在 `resolve()` 返回后检查每个待推动方块：

```text
源位置在任意游戏区域内
且
目标位置（源位置沿推动方向移动一格）在全部区域外
```

满足条件时：

1. 将源方块直接设为空气，不调用 `Block.dropResources`；
2. 从 `toPush` 列表移除该方块，避免同一 tick 再次移动；
3. 同一次推动中的其他方块继续移动。

只吞掉越界的那一个源方块；目标位置仍在区域内、或源方块本来就在区域外时不处理。
被吞掉的方块由 `/mgp reset` 恢复。

### 防复制：Create Contraption

Create 的 Contraption 防复制放在独立附属模组 `minigameplayground-create` 中，
详见该模组目录下的 README。核心模组不加载任何 Create Mixin，也不要求服务端安装 Create。

### 快照与 `/mgp reset`

- 执行 `/mgp create` 时，会在后台为区域生成初始地形快照。
- 单个区域体积上限由 `config/minigameplayground.json` 的 `maxZoneVolume` 控制，默认 **500,000**，最大 **20,000,000**；超过时拒绝创建/重置。
- `/mgp snapshot <区域ID>` 可用当前地形重新生成快照。
- `/mgp reset <区域ID>` 会：
  1. 向场地内玩家发送 Title：`§c场地正在重置中，请勿破坏...`；
  2. 清除区域 AABB 内的全部 `ItemEntity` 掉落物（包括玩家按 Q 丢出的物品）；
  3. 将地形恢复到快照状态（方块、方块实体 NBT 一并恢复）；
  4. 再次清理掉落物，并发送 `§a场地重置完成`。
- 快照元数据会记录创建时的维度，控制台执行重置时也会定位到正确维度。
- 1.1.x 及更早版本创建的区域可能没有快照；升级后请先执行一次 `/mgp snapshot <区域ID>`。

快照使用 WorldEdit 的 `.schem` 格式和粘贴 API，并保存在文件中：

```text
config/minigameplayground-snapshots/<区域ID哈希>.schem
config/minigameplayground-snapshots/<区域ID哈希>.meta.json
```

`/mgp reset` 会从文件读取快照，不依赖上一次命令留下的内存状态。
如果 WorldEdit 操作异常，内置 `StructureTemplate` 快照会作为兜底。

## 兼容性说明

- 伤害缩放在 `LivingEntity.getDamageAfterMagicAbsorb()` 返回时执行：此时受击冷却、盔甲/韧性、抗性提升和保护附魔已经结算，吸收伤害尚未结算。
- 恢复拦截使用 `LivingEntity.heal(float)` 的 Mixin，覆盖自然回血、生命恢复效果和瞬间治疗。
- 耐久保护使用 `ItemStack.hurtAndBreak(...)` / `ItemStack.hurt(...)` 的 Mixin。
- TaCZ 的子弹最终调用 `Entity.hurt(...)`/`LivingEntity.hurt(...)`，因此无需额外硬依赖即可覆盖。
- Spell Engine、Better Combat、Simply Swords、Puffish Attributes 等通过原版伤害/治疗流程的模组同样会被覆盖。
- 如果某个模组完全绕过 `hurt()` 或 `heal()`，直接写入玩家血量（例如直接 `setHealth`），则无法通过对应入口拦截。
- 区域按创建时记录的维度隔离；相同坐标在不同维度互不影响。伤害、治疗、耐久、无掉落物、快照和重置都只作用于该维度。
- 核心模组不依赖 Create；Create Contraption 保护由 `minigameplayground-create` 提供。
- `/mgp reset` 是恢复手段，不是防复制手段；不能依赖 `reset` 阻止已经发生的机械搬运复制。

## 数据持久化

区域数据保存在服务端：

```text
config/minigameplayground.json
config/minigameplayground-zones.json
config/minigameplayground-snapshots/<区域ID哈希>.schem    # WorldEdit 快照
config/minigameplayground-snapshots/<区域ID哈希>.nbt      # 内置兜底快照（异常时）
config/minigameplayground-snapshots/<区域ID哈希>.meta.json
```

如果新路径不存在，会自动读取旧版 `config/pvpbalance.json`、
`config/pvpbalance-zones.json` 和 `config/pvpbalance-snapshots/`，并迁移到新路径。
旧命令 `/pvb`、`/pvpbalance` 保留为兼容别名，新命令推荐 `/mgp`。

通用配置：

```json
{
  "maxZoneVolume": 500000
}
```

- 默认 `500,000`
- 允许最大 `20,000,000`
- 超过硬上限会自动限制并警告
- 超过 `1,000,000` 会记录大体积警告
- 修改后执行 `/mgp reload` 生效
- 作用于 `create`、`snapshot`、`reset`

每个区域配置包含 `dimension`、AABB 坐标、`standardHealth`、`restoreBaseline`、`keepDurability`、`damageDisabled` 和 `healDisabled`。

启动服务器时加载区域到内存，执行创建、删除、修改伤害/恢复基准、disable 状态或耐久设置时立即原子写回磁盘。
伤害/恢复计算路径只做内存 AABB 判断，不读取磁盘。快照仅在创建、`/mgp snapshot` 和 `/mgp reset` 时读写。

旧版配置缺少 `dimension`、`restoreBaseline`、`keepDurability`、`damageDisabled` 或 `healDisabled` 时，会分别自动回退为 `minecraft:overworld`、`20.0`、`true`、`false`、`false`。

## 已验证场景

使用 Fabric Loader 0.18.4 + Fabric API 0.92.6 + WorldEdit 7.2.15 的专用服务端进行了实际测试（部分玩家流程测试使用 Carpet 假人）：

- `/mgp create` 不带基准参数时，新区域正确生成伤害基准 20.0、恢复基准 20.0、耐久不消耗。
- 在主世界和 `minecraft:the_nether` 相同坐标创建两个区域后，主世界玩家只识别主世界区域，传送到下界后只识别下界区域。
- `/mgp create nether2 dimension minecraft:the_nether ...` 可以在控制台直接创建下界区域。
- `/mgp list minecraft:the_nether` 和 `/mgp list the_nether` 都能正确过滤下界区域。
- 不存在的维度 ID 会被拒绝，不会创建区域。
- 最大生命 40、伤害基准 20、无护甲：2 点伤害经过减伤后仍为 2，倍率 2 倍，实际扣除 4 点生命。
- 最大生命 40、伤害基准 20、钻石胸甲：2 点伤害先被盔甲减到约 1.425，再乘 2 倍，实际扣除约 2.85 点。
- 最大生命 40、伤害基准 20、抗性提升 II：2 点伤害先被减到约 1.2，再乘 2 倍，实际扣除约 2.4 点。
- 受击冷却：第一次 2 点伤害扣 4 点生命；冷却内第二次 2 点被忽略；随后 3 点只结算 1 点基础伤害，再乘 2 倍扣 2 点生命。
- 最大生命 40、恢复基准 20：4 点瞬间治疗实际恢复 8 点生命。
- 0.001 点伤害缩放后四舍五入为 0.00：生命不变，但 `HurtTime` 变为 10，受击动画保留，并且仍然消耗受击冷却。
- `/mgp damage arena disable` 后，玩家攻击不造成伤害；虚空伤害和 `generic_kill` 仍然生效；`/kill` 正常击杀。
- `/mgp heal arena disable` 后，瞬间治疗不恢复生命。
- 用数值重新执行 `/mgp damage` / `/mgp heal` 后，伤害和恢复恢复正常。
- 默认 `unbreakable true` 下，盔甲受击后 `Damage` 保持 0；切换为 `false` 后正常增加。
- 两人不在同一区域：不应用伤害倍率。
- 区域内使用 `/fill ... air destroy` 破坏方块不产生掉落物；区域外正常产生掉落物。
- 区域内箱子被破坏后内容物不掉落。
- `/mgp reset` 能恢复被破坏的方块、箱子内容和方块实体 NBT，并清理区域内掉落物，区域外掉落物不受影响。
- `/mgp snapshot` 更新快照后，`/mgp reset` 能恢复到新快照状态。
- 默认 500,000 体积上限生效；修改 `config/minigameplayground.json` 并执行 `/mgp reload` 后，新的上限立即生效。
- 配置为 `999999999` 时会自动限制为 `20,000,000` 并打印警告。
- WorldEdit 快照写入 `.schem` 文件，`/mgp reset` 能从文件读取并恢复。
- 旧版配置缺少新字段时，正确回退为恢复基准 20.0、耐久不消耗、伤害/恢复启用。
- 重启服务器后区域配置和快照文件正确保留。
- 原版活塞从区域内向区域外推动单个方块时，源方块被吞掉且不产生掉落物；目标位置保持空气。
- 原版活塞在区域内推动、不跨越区域边界时，方块正常移动。
- `Block.getDrops` 在区域内返回空列表，在区域外正常返回掉落物。
- 区域内带物品的木桶被直接移除时，内容物不掉落。
