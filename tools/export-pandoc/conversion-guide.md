# Word (DOCX) 转 Agent 可读文档: 流程, 规则与输入规范

本文档说明 `tools/export-pandoc/convert.py` 的转换流程, 硬编码规则, 以及业务人员怎样输出 Word 文档才能让转换尽量全自动.

## 1. 目标

输入一份 docx, 输出一个 agent 可以直接读取的目录:

```text
out/
  document.md               # 全文 GFM
  INDEX.md                  # 章节入口, 附件/图片索引, 取舍说明
  manifest.json             # 机器索引
  sections/section-XX.md    # 按 H3 分节
  assets/media/             # 图片
  assets/attachments/       # 原始附件 + 附件 Markdown
```

核心原则:

- 用 pandoc 解析 Word, 不手写 DOCX 正文解析器
- 用 panflute 在 AST 层做结构修正
- 用 olefile / openpyxl 处理内嵌 Excel
- 输出可校验, 可重复生成

## 2. 整体流程

```text
docx
  |
  | 1. pandoc: docx -> JSON AST + 抽图片
  v
Pandoc AST
  |
  | 2. panflute: 改 AST
  |    - 删 TOC
  |    - 标题编号
  |    - 表格拍平/分组
  |    - 图片图注
  |    - 附件链接
  |    - 清空段落/内部锚点
  v
修改后的 AST
  |
  | 3. pandoc: AST -> GFM
  v
document.md
  |
  | 4. 后处理
  |    - 表格空格压缩
  |    - 按 H3 分节
  |    - INDEX / manifest
  |    - 校验
  v
out/
```

附件是旁路:

```text
docx
  -> document.xml 找 OLEObject
  -> olefile 取 package 流
  -> 修 workbook visibility
  -> openpyxl 转 Markdown
```

## 3. 每个元素做了什么

| 元素 | 处理 |
|---|---|
| 标题 | `Header` 节点; 按 1-6 级顺序编号; 清掉加粗和多余空格 |
| 目录 | 找到文本为"目录"的段落, 删到第一个标题之前; 由 `INDEX.md` 替代 |
| 正文 | 原样保留 |
| 列表 | 保留 pandoc 的列表结构 |
| 内部链接 | 去掉 `#anchor`, 只保留文字, 避免断链 |
| 表格 | colspan 展开; 单元格内多段用 `<br>` 连接; 空行丢弃 |
| 整行合并 | `colspan == 列数` 的行转成 `**分组标题**`, 表格拆成多张 |
| 表头 | 如果 Word 没标表头, 3 列及以上的表把第一行提升为表头 |
| 2 列表 | 当成 key-value, 输出成 `**键** 值` |
| 图片 | 转成 Markdown 图片; 去掉 EMF; 路径改成相对路径 |
| 图注 | 图片下一段匹配 `2.1.3-1 ...` 就写进 alt, 并删掉那段 |
| 页眉页脚 | pandoc 默认不输出 |
| 水印 | 外链不抓取; 页眉水印图不输出 |
| OLE 附件 | 抽 `package` 流成 xlsx; 修隐藏窗口; 再转一份 Markdown |
| 附件位置 | 按 OLE 最近的标题, 把链接插到对应章节下 |
| 空段落 | 删除 |
| 字体/颜色/字号 | 丢弃 |
| 目录缓存 | 删除 |
| 图片去重 | 只保留被引用的 media 文件 |

## 4. 硬编码和假设

### 4.1 真正的常量

| 常量 | 含义 |
|---|---|
| `1..6` | 只处理 6 级标题 |
| `80` | pandoc `--columns=80` |
| `^\d+(?:\.\d+)+-` | 图注格式 |
| `★[^★]{1,6}★` | Excel 里的密级列 |
| `package` | OLE 里的 xlsx 流名 |
| `.emf` | 一律当预览图丢弃 |

### 4.2 启发式规则

| 规则 | 风险 |
|---|---|
| `ncols == 2` -> key-value | 普通 2 列数据表会被误判 |
| `ncols >= 3` -> 第一行当表头 | 第一行不是表头时会错 |
| `colspan >= ncols` -> 分组行 | 1 列表会误判, 应该加 `ncols > 1` |
| "目录"两字 -> 删到第一个标题 | 正文里真有"目录"两字会误伤 |
| 图注在图片下一段 | 图注在图片上方时配不上 |
| OLE 归属最近的标题 | 附件不在对应标题下时会挂错 |
| 标题顺序编号 | 文档里有未编号标题时, 后面编号会偏 |

### 4.3 文档家族假设

这些是"当前需求文档"这个家族的假设, 不是通用规则:

- 标题全都有编号
- 分组行是整行合并
- 封面是 2 列键值表
- 附件在 4.1/4.2/4.3 这种 H2 下
- Excel 附件是 OLE 嵌入
- 标题可以从 DLP 元数据里取
- 页眉只有水印和版权

因此当前 `convert.py` 本质上是一个 **profile**, 不是完全通用的核心. 处理其他家族时, 应通过参数/配置文件关闭不适用的规则.

## 5. 业务人员怎么输出, 转换最顺

### 5.1 最值得做的

1. **用真正的标题样式**
   - `Heading 1 / 2 / 3`
   - 不要用"加粗 + 大字号"假装标题
   - 这是影响最大的一个

2. **编号统一**
   - 用 Word 自动编号, 或者手写编号
   - 不要一部分自动, 一部分手写

3. **表格用真表格**
   - 不要用 Tab/空格排版
   - 第一行做表头
   - 数据区不要合并单元格
   - 需要分组时, 用整行合并做小标题, 这个反而很好转

4. **图片用嵌入图片**
   - 不要用文本框、形状、SmartArt 承载内容
   - 图注放在图片下一段, 格式统一, 例如:
     ```text
     图 2.1.3-1 考勤查询页面
     ```

5. **关键信息不要只放在图里**
   - 当前流程不做 OCR
   - 截图里的字段、规则、提示语, 最好在正文或表格里也写一遍

6. **附件用真附件**
   - 需要抽 Excel 就嵌入 Excel
   - 不要只放一个截图或图标
   - 文件名写清楚

7. **目录用 Word 自动目录**
   - 不要手写
   - 流程会删掉缓存目录, 重新生成索引

8. **定稿前接受修订, 删除批注**
   - 当前流程不处理 tracked changes / comments

### 5.2 尽量避免

- 文本框、形状、WordArt、SmartArt 承载正文
- 嵌套表格
- 多行表头
- 数据区大量合并单元格
- 多栏排版
- 公式图片化
- 把重要内容放在页眉页脚
- 用白色/隐藏文字藏内容

### 5.3 可以放心丢的

这些不会影响转换, 可以直接不输出:

- 页眉页脚
- 水印
- 重复目录
- 空页/空段落
- 装饰性图片
- 字体、字号、颜色
- 封面装饰

## 6. 目前不处理的元素

- 扫描件/纯图片 docx (需要 OCR)
- Word 公式 (OMML) 的精细转换
- 批注和修订
- 非 Excel 的 OLE 附件 (Word/PDF)
- 图表对象 (chart) 的语义提取
- 文本框里的正文
- 脚注/尾注的 agent 友好重排
- 多栏排版
- 阴影/艺术字等装饰

## 7. 最理想的输入

```text
真标题样式
+ 真表格 (单行表头, 分组用整行合并)
+ 嵌入图片 + 下一段文字图注
+ Excel 作为 OLE 附件
+ Word 自动目录
+ 定稿后无批注/无修订
```

满足这些, 转换基本全自动.

最容易出问题的输入:

```text
加粗大字假标题
+ 空格/Tab 排版
+ 数据区大量合并
+ 文本框承载正文
+ 关键信息只在截图里
```

遇到这些, 不是 pandoc 不行, 是结构信息在 Word 里本来就不存在, 只能靠人工规则或人工复核.

## 8. 运行方式

依赖:

```text
pandoc 3.14
panflute
olefile
openpyxl
```

安装:

```bash
winget install JohnMacFarlane.Pandoc
python -m pip install panflute olefile openpyxl
```

运行:

```bash
python tools/export-pandoc/convert.py
python tools/export-pandoc/convert.py other.docx
python tools/export-pandoc/convert.py other.docx --out tools/export-pandoc/out-other
python tools/export-pandoc/convert.py other.docx --title "文档标题"
```

`out/` 和 `.build/` 是生成物, 已被 git 忽略.

## 9. 校验指标

每次运行会检查:

```text
text missing    源文档非 TOC 文本是否全部出现在输出中
toc match       重算标题编号与文档自带目录是否一致
broken links    输出中的本地链接是否都存在
counts          标题/表格/图片/附件数量
```

目标:

```text
text missing    = 0
broken links    = 0
toc match       = 全部匹配 (原目录可能过期, 允许少量新增)
```
