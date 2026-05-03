# MySQL调优与ERP项目案例学习笔记

这份笔记整理了两部分内容：

1. MySQL 调优的通用思路、索引原理和常见面试点
2. 结合科瑞 ERP 项目的具体调优案例，方便面试时直接举例

---

## 一、MySQL 调优的整体思路

MySQL 调优不等于“只会加索引”。  
本质上是尽量做到下面几件事：

- 减少不必要的数据扫描
- 减少磁盘 IO
- 减少回表次数
- 减少排序和临时表开销
- 让 SQL 更贴合业务访问路径

一个比较完整的调优流程通常是：

1. 先定位慢 SQL
2. 再看 `EXPLAIN` 执行计划
3. 再决定是：
   - 改 SQL
   - 加索引
   - 改联合索引顺序
   - 改分页方式
   - 加缓存
   - 优化表结构

面试里不要一上来就说“我会给字段加索引”，这样会显得比较浅。

---

## 二、我做 MySQL 调优时通常会关注什么

### 1. 用 `EXPLAIN` 看执行计划

我通常会重点看：

- `type`
- `key`
- `rows`
- `Extra`

#### `type`

代表查询访问类型，常见从好到差大致可以理解为：

- `const`
- `eq_ref`
- `ref`
- `range`
- `index`
- `ALL`

其中：

- `ALL` 往往表示全表扫描

#### `key`

表示实际命中的索引。

#### `rows`

表示 MySQL 预估需要扫描多少行。  
这个值越大，通常说明代价越高。

#### `Extra`

常见的两个坏信号：

- `Using temporary`
- `Using filesort`

通常说明：

- 用了临时表
- 做了额外排序

很多时候意味着 SQL 还有优化空间。

---

## 三、索引底层原理

### 1. InnoDB 的索引底层主要是 B+ 树

不是红黑树，也不是普通二叉树。

数据库之所以选 B+ 树，主要因为它更适合磁盘场景：

- 树更矮，查找层数更少
- 每个节点能存很多 key，磁盘 IO 更少
- 叶子节点有序，范围查询性能好
- 非叶子节点只存索引键，能容纳更多索引项

### 2. 聚簇索引和二级索引

#### 聚簇索引（主键索引）

InnoDB 主键索引的叶子节点存的是：

- 整行数据

所以主键查询通常比较快。

#### 二级索引（普通索引）

普通索引的叶子节点存的是：

- 索引列值
- 对应主键值

如果查询字段不全在二级索引里，查到主键后还要再回主键索引查整行数据，这个过程叫：

- 回表

---

## 四、加索引为什么会快

没有索引时，MySQL 很多时候只能：

- 从第一行扫到最后一行
- 一条条比较

这就是全表扫描。

有索引后，就能通过 B+ 树快速定位目标位置。  
可以把它理解成：

- 没索引：翻整本书找内容
- 有索引：先翻目录再到具体页

所以索引快的原因本质上是：

- 减少全表扫描
- 减少磁盘 IO
- 更快定位数据

---

## 五、联合索引与最左前缀原则

例如有联合索引：

```sql
CREATE INDEX idx_a_b_c ON table_name(a, b, c);
```

它更擅长支持这些查询：

```sql
WHERE a = ?
WHERE a = ? AND b = ?
WHERE a = ? AND b = ? AND c = ?
```

但不擅长单独支持：

```sql
WHERE b = ?
WHERE c = ?
WHERE b = ? AND c = ?
```

因为联合索引遵循：

- 最左前缀原则

意思是：

- 查询最好从最左边的列开始使用索引

所以 `(a, b, c)` 不等于 3 个完全独立的索引。

---

## 六、什么是回表，什么是覆盖索引

### 1. 回表

通过二级索引先找到主键，再去聚簇索引查整行数据，这就叫回表。

### 2. 覆盖索引

如果查询的字段刚好都在索引里，MySQL 就不需要再回主表查数据。

比如索引是：

```sql
(category_id, status, publish_time)
```

查询是：

```sql
SELECT category_id, status, publish_time
FROM news
WHERE category_id = 1 AND status = 1;
```

那么这时查询字段都在索引里，可以直接从索引返回结果，这就是覆盖索引。

覆盖索引更快，因为：

- 少一次回表
- 少一次磁盘访问

---

## 七、索引能无条件加吗

不能。

索引不是越多越好，主要有几个原因：

### 1. 会占空间

每个索引都要单独存储，表越大，索引占用越大。

### 2. 会影响写性能

插入、更新、删除时，不仅要改数据，还要维护 B+ 树索引结构。

### 3. 索引太多会增加维护成本

索引设计越多越复杂，优化器选择成本也会上升。

### 4. 低区分度字段不适合乱加索引

例如：

- 性别
- 状态
- 是否删除

这种字段取值很少，单独加索引收益通常不高。

---

## 八、索引对数据库性能的影响

### 正面影响

- 提高查询速度
- 降低扫描行数
- 提高排序、分组、关联效率
- 覆盖索引可减少回表

### 负面影响

- 增加磁盘和内存占用
- 降低插入、更新、删除性能
- 增加维护成本
- 对高频更新字段可能带来更大代价

---

## 九、常见 SQL 优化方式

### 1. 给高频查询条件建立合适索引

重点关注：

- `where` 字段
- `join` 字段
- `order by` 字段
- `group by` 字段

### 2. 优先考虑联合索引，而不是只堆单列索引

因为实际业务查询通常是多个条件组合。

### 3. 避免 `select *`

只查真正需要的字段，可以：

- 减少网络传输
- 降低回表开销

### 4. 避免索引失效

常见失效场景：

- 对索引列做函数
- `LIKE '%xxx'`
- 联合索引不满足最左前缀

### 5. 优化深分页

例如：

```sql
SELECT *
FROM news
ORDER BY id
LIMIT 100000, 20;
```

这种 offset 很大的分页会越来越慢。  
可考虑：

- 基于主键游标翻页
- 先查 id 再回表

### 6. 热点数据引入缓存

例如结合 Redis 缓存：

- 热门商品
- 用户信息
- 新闻详情
- 分类数据

减轻 MySQL 压力。

---

## 十、结合科瑞 ERP 项目的具体优化案例

注意：  
在科瑞 ERP 项目里，我的岗位是测试开发实习生，所以更稳妥的表述不是“我主导了数据库底层优化”，而是：

**我在接口测试、性能压测和问题定位过程中，参与了慢 SQL 分析和优化建议验证。**

下面这些案例都适合你在面试时具体展开。

---

## 案例 1：BOM 管理列表查询优化

### 业务场景

ERP 的 BOM 管理页面通常支持：

- 按 BOM 编号查
- 按物料名称查
- 按状态查
- 按创建时间排序
- 分页显示

例如查询可能类似：

```sql
SELECT id, bom_code, material_name, status, create_time
FROM bom_info
WHERE status = 1
  AND material_name LIKE '%电机%'
ORDER BY create_time DESC
LIMIT 0, 20;
```

### 这个 SQL 的问题

#### 问题 1：前导模糊查询

```sql
material_name LIKE '%电机%'
```

前面有 `%`，普通 B+ 树索引通常很难高效利用。

#### 问题 2：排序可能额外开销大

```sql
ORDER BY create_time DESC
```

如果 `EXPLAIN` 里看到：

```text
Using filesort
```

说明数据库进行了额外排序。

### 我在面试里可以怎么说

我会先说：

**在 BOM 管理模块里，我接触过列表页按状态筛选、按物料名称搜索、按创建时间倒序分页的场景。定位时我会先用 `EXPLAIN` 看是否出现全表扫描和 `Using filesort`。如果发现排序和筛选没有命中合适索引，就会建议优先围绕高频条件设计联合索引，比如 `status + create_time`，而不是简单堆很多单列索引。**

### 可以说的优化思路

#### 优化 1：如果业务允许，把前导模糊改成前缀匹配

把：

```sql
LIKE '%电机%'
```

尽量改成：

```sql
LIKE '电机%'
```

这样普通索引更容易生效。

#### 优化 2：给高频筛选 + 排序字段设计联合索引

```sql
CREATE INDEX idx_bom_status_create_time
ON bom_info(status, create_time);
```

作用：

- `status` 用于筛选
- `create_time` 用于排序

如果名称搜索支持前缀匹配，也可以考虑类似：

```sql
CREATE INDEX idx_bom_status_name_create
ON bom_info(status, material_name, create_time);
```

但要强调：

**如果仍然是 `%关键词%` 这种模糊搜索，普通索引收益有限，不能机械地认为“建了索引就一定快”。**

---

## 案例 2：盘点管理明细分页优化

### 业务场景

盘点单详情页通常会展示很多盘点明细，例如：

- 盘点单号
- 仓库
- 物料
- 账面数量
- 实盘数量
- 差异数量

一张盘点单可能有很多条明细记录。

查询可能类似：

```sql
SELECT id, check_order_id, material_code, material_name, book_qty, real_qty, diff_qty
FROM inventory_check_detail
WHERE check_order_id = 10001
ORDER BY id DESC
LIMIT 0, 20;
```

### 这个 SQL 的问题

如果 `check_order_id` 没有合适索引，就可能扫大量明细数据。

### 面试时可说的分析逻辑

**这种主从表场景里，详情页查明细本质上是“按主单 ID 查从表记录”，所以 `check_order_id` 是非常高频的筛选字段。**

### 可以说的优化方案

建立联合索引：

```sql
CREATE INDEX idx_check_order_id_id
ON inventory_check_detail(check_order_id, id);
```

作用：

- `check_order_id` 用于按盘点单定位明细
- `id` 用于排序和分页

### 如果面试官追问为什么不用单列索引

你可以说：

**因为这个查询不是只按 `check_order_id` 过滤，还带了 `ORDER BY id DESC`，所以相比单独给 `check_order_id` 建索引，更适合根据真实 SQL 建 `(check_order_id, id)` 联合索引，让筛选和排序更贴合查询路径。**

### 深分页问题也可以顺手提

如果数据量特别大，还可以补一句：

**这种明细表如果做深分页，`LIMIT offset, size` 会越来越慢，所以也可以考虑基于主键游标翻页。**

例如：

```sql
SELECT id, check_order_id, material_code, material_name
FROM inventory_check_detail
WHERE check_order_id = 10001
  AND id < 5000
ORDER BY id DESC
LIMIT 20;
```

---

## 案例 3：销售订单列表筛选优化

### 业务场景

销售订单列表通常支持：

- 按客户 ID 查
- 按订单状态查
- 按下单时间范围查
- 按创建时间倒序显示

例如：

```sql
SELECT id, order_no, customer_id, order_status, create_time
FROM sales_order
WHERE customer_id = 2001
  AND order_status = 2
  AND create_time >= '2026-04-01 00:00:00'
  AND create_time <  '2026-05-01 00:00:00'
ORDER BY create_time DESC
LIMIT 0, 20;
```

### 这类场景我可以怎么讲

**销售订单列表是典型的多条件筛选 + 时间排序 + 分页场景。对于这种 SQL，我不会只给某个字段单独加索引，而是会根据实际查询组合考虑联合索引。**

例如：

```sql
CREATE INDEX idx_sales_customer_status_time
ON sales_order(customer_id, order_status, create_time);
```

### 为什么这样设计字段顺序

你可以这样说：

**`customer_id` 和 `order_status` 都是高频等值筛选字段，`create_time` 既参与范围筛选也参与排序，所以这种组合更贴近实际业务查询。**

### 还能补的细节

**如果订单列表页长期 `select *`，即使索引命中了，回表开销也可能偏大，所以我会更建议只查页面真正需要展示的字段。**

---

## 案例 4：库存流水 / 仓库记录查询优化

### 业务场景

库存流水查询通常会按：

- 仓库 ID
- 物料 ID
- 时间范围

筛选库存变化记录。

例如：

```sql
SELECT id, warehouse_id, material_id, change_type, change_qty, create_time
FROM stock_record
WHERE warehouse_id = 3
  AND material_id = 10086
  AND create_time >= '2026-04-01 00:00:00'
  AND create_time < '2026-04-30 23:59:59'
ORDER BY create_time DESC;
```

### 可以说的优化思路

建立联合索引：

```sql
CREATE INDEX idx_stock_wh_material_time
ON stock_record(warehouse_id, material_id, create_time);
```

### 为什么这个索引合适

因为这条 SQL 很符合联合索引的最左前缀：

- 先按 `warehouse_id`
- 再按 `material_id`
- 再按 `create_time`

### 还可以顺带提一个索引失效避坑点

不建议写成：

```sql
WHERE DATE(create_time) = '2026-04-01'
```

因为这会对索引列做函数，可能导致索引失效。

更好的写法是：

```sql
WHERE create_time >= '2026-04-01 00:00:00'
  AND create_time <  '2026-04-02 00:00:00'
```

---

## 十一、如果面试官问：这些优化你怎么验证有效

可以从两个角度答：

### 1. 执行计划变化

看：

- `type` 有没有从 `ALL` 变成 `ref` / `range`
- `key` 有没有命中预期索引
- `rows` 预估扫描行数有没有下降
- `Extra` 里是否去掉了 `Using filesort` 或 `Using temporary`

### 2. 接口响应变化

结合测试开发背景，可以说：

- 用 JMeter 压测前后对比
- 观察接口响应时间 RT
- 看吞吐量变化
- 看大数据量场景下的稳定性

这个说法非常贴合你的实习经历。

---

## 十二、面试可直接使用的一段完整回答

如果结合我在科瑞 ERP 项目的实际经历，我接触到的 MySQL 优化更多是在接口测试、压测和问题定位过程中，协助开发分析慢 SQL。比如在 BOM 管理模块里，列表页会按状态、物料名称和创建时间做筛选排序，我会先用 `EXPLAIN` 看是否出现全表扫描和 `Using filesort`，如果发现排序和筛选没有很好命中索引，就会建议优先围绕高频条件设计联合索引，比如 `status + create_time`，而不是简单堆很多单列索引。对于物料名称这种 `%关键词%` 模糊搜索，我也知道普通索引效果有限，不会机械地说建索引就一定能快。

在盘点管理模块里，我更关注的是明细表分页查询。因为盘点单详情往往是一张主表带很多明细，如果 `check_order_id` 没有合适索引，查明细时就容易扫描大量数据。所以这种场景我会优先考虑 `check_order_id + id` 这样的联合索引，让它既支持按盘点单定位明细，也支持按 `id` 排序分页。对于数据量特别大的明细表，我也知道深分页 `limit offset,size` 会越来越慢，可以考虑基于主键游标分页。

另外像销售订单和库存流水这类场景，本质上都是多条件筛选加时间排序。我在分析时会更关注联合索引是否贴合实际 SQL，比如订单列表可能会按客户、状态和时间筛选，就更适合设计 `customer_id + order_status + create_time`；库存流水则适合按 `warehouse_id + material_id + create_time` 这样的顺序建联合索引。同时我也会注意避免索引失效，比如不在时间字段上直接做函数处理，不乱用前导 `%` 模糊查询，并尽量避免列表页直接 `select *`，减少回表开销。

---

## 十三、最短背诵版

面试里最少可以记住这几句：

- MySQL 调优先定位慢 SQL，再看 `EXPLAIN`
- 我主要关注 `type`、`key`、`rows`、`Extra`
- 索引底层是 B+ 树
- 索引不是越多越好，会影响写性能
- ERP 项目里我能举 4 个场景：
  - BOM 列表查询
  - 盘点明细分页
  - 销售订单列表
  - 库存流水查询

---

## 十四、复习建议

建议你优先熟练讲清下面这些词：

- `EXPLAIN`
- `type`
- `Using filesort`
- 联合索引
- 最左前缀
- 回表
- 覆盖索引
- 深分页
- B+ 树

如果这些词你都能结合上面 4 个 ERP 场景说出来，这一题就会很像“真正做过”。
