# API 清单

## 范围说明

- 本文档当前整理了五类已确认功能: 登录、课表、成绩查询、空教室查询、选课。
- 所有结论以当前抓包内容和 Python 实测结果为准。

## 更新记录

- 2026-03-11
  - `queryYxkc` 已确认可返回教师字段 `dgjsmc`，并且 `kcxx` 中可能包含教师 HTML 链接。
  - 空教室接口 `DJ6` 时间段已确认：20:45-22:30。
  - 空教室 `DJ1..DJ6` 目前按样本判断：`0` 表示空闲，非 `0` 表示占用。

## 总体结论

- 登录链路是后端直连，不是明显的 CAS 跳转式认证链路。
- 登录前请求使用 Authorization: Basic aW5jb246MTIzNDU=。
- 登录成功后业务请求使用 Authorization: bearer <access_token>。
- route 是初始化阶段下发的路由 cookie。
- JSESSIONID 是登录成功后下发的应用会话 cookie，并且在课表接口中会再次刷新。

## 通用请求特征

### 登录前

- Authorization: Basic aW5jb246MTIzNDU=
- _lang: cn
- rolecode: 01 或 06
- Content-Type: application/x-www-form-urlencoded

### 登录后

- Authorization: bearer <access_token>
- _lang: cn
- 常见 rolecode: 06
- Cookie 一般包含:
- route=<route id>
- JSESSIONID=<session id>
- org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE=zh-CN

## 1. 登录

## 1.1 初始化接口

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/component/queryApplicationSetting/rsa
- 鉴权: Basic
- Content-Type: application/x-www-form-urlencoded
- 请求体: 空

### 已确认响应

```json
{"code":0,"msg":null,"msg_en":null,"content":"0"}
```

### 响应头关键点

```text
Set-Cookie: route=<route id>; Path=/
```

### 字段分析

- code
- 当前样本中为 0
- 更像初始化成功标记，不像业务数据
- content
- 当前值为字符串 "0"
- 暂时看不出明确业务含义，像一个开关位或占位值

### 如何使用

- 这是登录前第一步。
- 这个接口最重要的作用不是返回体，而是让服务端先下发 route cookie。
- 后续请求应该复用同一个 Session，保留这个 cookie。

## 1.2 获取 RSA 参数

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/c_raskey
- 鉴权: Basic
- Content-Type: application/x-www-form-urlencoded
- 请求体: 空

### 已确认响应

```json
{"CLIENT_RSA_EXPONENT":"MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDCgPLoJLt5v+gzsl5fsahkKQC87kogMR2999RdtYYfd4wpsLViwQg2lLQUWOOXPYQMypOo74wz0fY6mxRutuBcUVEUE8RifnGtkkEikrgR4q+fgqvNQCnuWCI/SOMG4aJl6W7Qu1OxHGwjHfJ1svQ7JiMhldBxSTFQqEFBeTmkCQIDAQAB","CLIENT_RSA_MODULUS":"10001"}
```

### 字段分析

- CLIENT_RSA_MODULUS
- 当前值为 10001
- 从 RSA 常识看，这更像公钥指数 65537 的十六进制表示
- CLIENT_RSA_EXPONENT
- 当前值是一大串 Base64 数据
- 从形态看更像公钥主体，而不像 exponent

### 当前判断

- 这个接口确实返回了 RSA 参数。
- 但字段名和字段值的语义疑似反了。
- 按现有抓包，登录请求体仍然是明文密码，因此暂时不能断言密码一定要先 RSA 加密。

### 如何使用

- 当前链路里建议保留这一步。
- 如果后续出现某些环境或版本必须加密密码，这个接口就是密钥来源。
- 但按你现在抓到的样本，先按明文 form 登录实现即可。

## 1.3 LDAP 登录

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/authentication/ldap
- 鉴权: Basic
- Content-Type: application/x-www-form-urlencoded

### 请求体格式

```text
username=<学号>&password=<密码>
```

### 成功响应

```json
{
  "access_token":"<token>",
  "refresh_token":"<refresh_token>",
  "data":{
    "pylx":"1",
    "lxdh":"***",
    "yhlx":"1",
    "yhxm":"***",
    "sfjxyz":false,
    "bmmc":"信息学部"
  },
  "scope":"all",
  "token_type":"bearer",
  "expires_in":7327462,
  "info":{
    "yhdm":"<学号>",
    "xm":"***",
    "roles":["01"]
  }
}
```

### 成功响应头关键点

```text
Set-Cookie: JSESSIONID=<session id>; Path=/incoSpringBoot; HttpOnly
```

### 失败响应

```json
{"code":500,"msg":"用户名或密码错误！！","msg_en":"用户名或密码错误！！","content":"用户名或密码错误！！"}
```

### 失败响应头特征

```text
token: inco
```

### 字段分析

- access_token
- 登录后的主鉴权令牌
- 后续业务请求放在 Authorization: bearer <access_token>
- refresh_token
- 说明系统有刷新机制
- 但刷新接口路径暂未确认
- token_type
- 当前样本为 bearer
- expires_in
- token 有效期秒数
- 当前值较大，说明有效期较长或服务端策略较宽松
- data
- 用户附加资料
- pylx: 培养类型
- lxdh: 联系电话
- yhlx: 用户类型
- yhxm: 用户姓名
- sfjxyz: 当前样本为 false
- bmmc: 部门名称
- info
- 登录主体信息
- yhdm: 用户代码，当前可视为学号
- xm: 姓名
- roles: 角色数组

### 如何使用

- 这是当前唯一已确认的登录接口。
- 当前样本里，用户名密码是明文 form 直接提交。
- 登录成功后应至少保存两类状态:
- access_token
- Cookie 中的 route 与 JSESSIONID
- 最稳妥的实现方式是复用同一个 HTTP Session，不要手动拼 Cookie。

### 最小登录步骤

1. 请求 /component/queryApplicationSetting/rsa
2. 请求 /c_raskey
3. 请求 /authentication/ldap
4. 保存 access_token、refresh_token 和 Session cookie

## 2. 课表

## 2.1 按天查询课表

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/app/kbrcbyapp/querykbrcbyday
- 鉴权: bearer
- Content-Type: application/x-www-form-urlencoded

### 请求体格式

```text
nyr=2026-03-09
```

### 响应示例

```json
{
  "code":200,
  "msg":"操作成功",
  "msg_en":"操作成功_en",
  "content":[
    {"JSJC":2,"XJ":"1 2 ","KSJC":1,"JTSJ":" - ","DJ":1},
    {"JSJC":4,"XJ":"3 4 ","KSJC":3,"JTSJ":" - ","DJ":2},
    {"JSJC":6,"XJ":"5 6 ","KSJC":5,"JTSJ":" - ","DJ":3,"kbrc":[{"JSJC":6,"KSJC":5,"JTSJ":" - ","DJ":3,"CDMC":"A309","XB":2,"KCMC":"机器学习","KCMC_EN":"Machine Learning"}]}
  ]
}
```

### 响应头关键点

```text
Set-Cookie: JSESSIONID=<new session id>; Path=/incoSpringBoot; HttpOnly
```

### 字段分析

- 顶层字段
- code: 业务状态码，成功为 200
- msg: 中文提示
- msg_en: 英文提示
- content: 当天课表节次数组
- content[] 节次字段
- KSJC: 开始节次
- JSJC: 结束节次
- DJ: 第几大节
- XJ: 小节展示文本，例如 1 2
- JTSJ: 具体时间文本，当前样本里为空白占位，说明客户端可能另有时间表映射
- kbrc[] 课程字段
- CDMC: 教室或场地名称
- XB: 节内排序或显示序号
- KCMC: 中文课程名
- KCMC_EN: 英文课程名

### 如何使用

- 适合做今日日程或指定日期课表。
- 请求体只需要一个日期参数 nyr。
- 调用成功后建议继续沿用同一个 Session，因为这个接口会刷新 JSESSIONID。

## 2.2 课表总览

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/app/kbrcbyapp/querykbrczong
- 鉴权: bearer
- Content-Type: application/x-www-form-urlencoded

### 请求体格式

```text
nyr=2026-03-09
```

### 响应结构

- 顶层结构仍然是 code、msg、msg_en、content
- content[] 是一段日期范围内按天展开的课表列表

### 字段分析

- XN: 学年，例如 2025-2026
- XQ: 学期编号，例如 2
- XQJ: 星期几，字符串形式，1 到 7
- RQ: 日期，格式为 YYYY-MM-DD
- kbrc[]
- 只有当天有课时才出现
- 当前已确认字段:
- XB
- KCMC
- KCMC_EN
- RQ

### 如何使用

- 适合做未来几周总览、列表视图或按天聚合课表。
- 如果你只想拿某一天的课表，用 querykbrcbyday 更直接。

## 2.3 学年学期列表

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/app/commapp/queryxnxqlist
- 鉴权: bearer
- Content-Type: application/x-www-form-urlencoded
- 请求体: 空

### 响应示例

```json
{
  "code":200,
  "msg":"操作成功",
  "content":[
    {
      "SFDQXQ":"1",
      "XN":"2025-2026",
      "XNXQ":"2025-20262",
      "XQ":"2",
      "XNXQMC":"2026春季",
      "XNXQMC_EN":"2026Spring"
    }
  ]
}
```

### 字段分析

- SFDQXQ
- 是否当前学期
- 1 表示当前学期
- XN
- 学年，例如 2025-2026
- XQ
- 学期编号，例如 1、2、3
- XNXQ
- 学年学期组合值
- XNXQMC
- 中文学期名称
- XNXQMC_EN
- 英文学期名称

### 如何使用

- 如果客户端支持切换学期，这个接口应作为课表模块初始化的第一条业务接口。
- 当前学期可通过 SFDQXQ=1 直接找出。

## 2.4 周次列表

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/app/commapp/queryzclistbyxnxq
- 鉴权: bearer
- Content-Type: application/json

### 请求体格式

```json
{"xn":"2025-2026","xq":"2"}
```

### 已确认响应

```json
{
  "code":200,
  "msg":"操作成功",
  "msg_en":"操作成功_en",
  "content":[
    {"ZCMC":"第1周","ZC":1},
    {"ZCMC":"第2周","ZC":2},
    {"ZCMC":"第3周","ZC":3}
  ]
}
```

### 响应头关键点

```text
Set-Cookie: JSESSIONID=<new session id>; Path=/incoSpringBoot; HttpOnly
```

### 字段分析

- `ZCMC`
  - 周次中文名称，例如 `第1周`
- `ZC`
  - 周次数字值，例如 `1`

### 如何使用

- 这是“按周查看课表”前的标准前置接口。
- 先通过它拿到当前学期有哪些周次，再选择具体 `zc` 去查询周课表。
- 当前样本中返回了 `1` 到 `18` 周。

## 2.5 按周课表矩阵

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/app/Kbcx/query
- 鉴权: bearer
- Content-Type: application/json

### 请求体格式

```json
{"xn":"2025-2026","xq":"2","zc":"1","type":"json"}
```

### 已确认请求参数

- `xn`
  - 学年，例如 `2025-2026`
- `xq`
  - 学期编号，例如 `2`
- `zc`
  - 周次，例如 `1`
- `type`
  - 当前样本固定为 `json`

### 已确认响应结构

```json
{
  "code":200,
  "msg":"操作成功",
  "msg_en":"操作成功_en",
  "content":[
    {"rqList":[{"XQMC":"星期一","XQMC_EN":"Mon","XQDM":1,"RQ":"2026-03-09"}]},
    {"jcList":[{"JSJC":2,"KSJC":1,"DJ":1,"SJ":"08:30—10:15"}]},
    {"kcxxList":[{"XQJ":1,"KCDM":"COMP2054","KBXX":"机器学习\n[A309]","KBXX_EN":"Machine Le...\n[A309]","DJ":3,"XB":2,"SYLB":null}]}
  ]
}
```

### 字段分析

- `rqList`
  - 一周日期信息列表
  - `XQMC`: 中文星期名
  - `XQMC_EN`: 英文星期名
  - `XQDM`: 星期数字，`1` 到 `7`
  - `RQ`: 日期
- `jcList`
  - 节次时间表
  - `KSJC`: 开始节次
  - `JSJC`: 结束节次
  - `DJ`: 第几大节
  - `SJ`: 该大节对应时间段
- `kcxxList`
  - 这一周的课程布点信息
  - `XQJ`: 星期几
  - `KCDM`: 课程代码
  - `KBXX`: 课程展示文本，当前样本里已包含课程名和教室
  - `KBXX_EN`: 英文展示文本
  - `DJ`: 第几大节
  - `XB`: 显示顺序值
  - `SYLB`: 当前样本为 `null`

### 如何使用

- 这是目前最完整的“周课表矩阵”接口。
- 一般先调用 `/app/commapp/queryxnxqlist` 拿学期，再调用 `/app/commapp/queryzclistbyxnxq` 拿周次，然后用这里的 `xn`、`xq`、`zc` 拉取整周课表。
- 这个接口特别适合直接渲染课表表格视图，因为日期、时间段、课程格子都一次性返回了。

## 3. 成绩查询

## 3.1 成绩列表查询

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/app/cjgl/xscjList?_lang=zh_CN
- 鉴权: bearer
- Content-Type: application/json

### 请求体格式

```json
{"xn":"2025-2026","xq":"1","qzqmFlag":"qm","type":"json"}
```

### 已确认请求参数

- `xn`
  - 学年，例如 `2025-2026`
- `xq`
  - 学期编号
  - 当前已见 `1`、`2`、`3`
- `qzqmFlag`
  - 成绩类型标记
  - `qm` 表示期末
  - `qz` 表示期中
- `type`
  - 当前样本固定为 `json`
  - 暂时不要省略

### 已确认请求示例

#### 2025 秋季期末

```json
{"xn":"2025-2026","xq":"1","qzqmFlag":"qm","type":"json"}
```

#### 2025 秋季期中

```json
{"xn":"2025-2026","xq":"1","qzqmFlag":"qz","type":"json"}
```

#### 2025 夏季期末

```json
{"xn":"2024-2025","xq":"3","qzqmFlag":"qm","type":"json"}
```

#### 2025 春季期末

```json
{"xn":"2024-2025","xq":"2","qzqmFlag":"qm","type":"json"}
```

### 当前规则判断

- 学校当前没有冬季学期成绩。
- 成绩查询至少按两个维度切换:
  - 学年学期
  - 期中或期末

### 响应头关键点

```text
Set-Cookie: JSESSIONID=<new session id>; Path=/incoSpringBoot; HttpOnly
Set-Cookie: org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE=zh-CN; Path=/
```

### 响应结构

```json
{
  "code":200,
  "msg":null,
  "msg_en":null,
  "content":[
    {
      "id":"469E74611A172209E0630C18F80A34BE",
      "xnxq":"2025秋季",
      "xnxqdm":"2025-20261",
      "kcdm":"22MX44001",
      "kcmc":"劳动教育概论",
      "kcmc_en":"Introduction to Labor Education",
      "xf":0,
      "zf":"99",
      "kcxz":"必修",
      "rwh":"2025-2026-1-22MX44001-001",
      "fxcj":[],
      "globalXmList":["-2147483648"],
      "globalXmAll":"0",
      "sfpjcxcj":"0"
    }
  ]
}
```

### 字段分析

- 顶层字段
  - `code`
    - 业务状态码，当前成功为 `200`
  - `msg`
    - 当前成功样本为 `null`
  - `msg_en`
    - 当前成功样本为 `null`
  - `content`
    - 成绩记录数组
- 成绩记录核心字段
  - `id`
    - 该条成绩记录的唯一标识
  - `xnxq`
    - 学期中文名，例如 `2025秋季`
  - `xnxqdm`
    - 学期代码，例如 `2025-20261`
  - `kcdm`
    - 课程代码
  - `kcmc`
    - 中文课程名
  - `kcmc_en`
    - 英文课程名
  - `xf`
    - 学分
  - `zf`
    - 总分
    - 既可能是数值分数，例如 `85`、`92`
    - 也可能是等级制，例如 `A`、`A-`
  - `khfs`
    - 考核方式
    - 当前已见 `考试`、`考查`、`null`
  - `kcxz`
    - 课程性质，例如 `必修`
  - `kclb`
    - 课程类别
    - 当前已见 `其他`、`体育`、`美育类`、`跨专业发展课程`、`四史类课`
  - `rwh`
    - 任务号或教学任务标识
  - `fxcj`
    - 分项成绩数组，当前样本为空数组
- `fxcj[]` 分项成绩字段
  - `id`
    - 分项成绩记录 id
  - `rwh`
    - 对应教学任务号
  - `xscjb_ls_id`
    - 关联到外层成绩记录 id
  - `fxlx`
    - 分项类型代码，当前样本固定为 `0`
  - `fxdm`
    - 分项代码，例如 `01`、`02`、`03`
  - `fxmc`
    - 分项中文名，例如 `平时`、`期中`、`期末`、`作业成绩`、`实验`
  - `fxmc_en`
    - 分项英文名或拼音化英文名
  - `df`
    - 该分项得分
  - `bz`
    - 该分项比重
  - `mf`
    - 该分项满分
  - `sfqmks`
    - 当前样本为 `null`
  - `pylx`
    - 当前样本为 `null`
- 当前样本里已出现但含义未完全坐实的字段
  - `globalXmList`
  - `globalXmAll`
  - `gljb`
  - `pageNum`
  - `pageSize`
  - `ordertext`
  - `qzqmFlag`
  - `pylx`
  - `xn`
  - `xq`
  - `yhdm`
  - `yhid`
  - `khfs`
  - `kclb`
  - `ksxnxq`
  - `jsxnxq`
  - `sfpjcxcj`
  - `cxrbmdm`
  - `sfqrhccj`
  - `djcjxssql`
  - `schoolType`

### 当前已观察到的分项成绩模式

- 通识/任选课可能按 `平时 + 期中 + 期末` 组成
- 理工课程常见 `作业 + 期末考试`
- 部分课程会出现 `考试 + 作业 + 实验`
- 体育课会出现 `课堂实操 + 技术成绩 + 身体素质 + 随堂测试`
- 有些课程 `fxcj` 为空，说明接口并不保证每门课都有分项成绩

### 对 `bz` 和 `mf` 的当前判断

### 对 `bz`、`mf`、`df` 的已确认解释

- `bz`
  - 分项比重
  - 例如 `平时 20`、`期中 40`、`期末 40`
  - 又如 `作业 30`、`期末考试 70`
- `mf`
  - 该分项满分
  - 例如 `mf=100`
  - 也可能是 `mf=20`、`mf=40` 这种按分项单独记分的情况
- `df`
  - 该分项实际得分

### 如何使用

- 这是当前已确认的成绩查询主接口。
- 请求时必须带 bearer token，并建议继续复用同一个 Session。
- 当前样本显示它也会刷新 `JSESSIONID`，所以不要手工固定旧 cookie。
- 如果只做“最近一个学期成绩”，直接传对应 `xn`、`xq` 和 `qzqmFlag` 即可。
- 如果要做完整成绩页，建议遍历:
  - 学年
  - 学期
  - `qzqmFlag` in `qm`, `qz`

### 最小使用指南

1. 先完成登录，拿到 `access_token`
2. 维持登录 Session 中的 `route` 和 `JSESSIONID`
3. 调用 `/app/cjgl/xscjList?_lang=zh_CN`
4. 根据 `xn`、`xq`、`qzqmFlag` 切换不同学期和期中期末

## 3.2 学分绩与专业排名

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/app/cjgl/xfj
- 鉴权: bearer
- Content-Type: application/json

### 请求体格式

```json
{"type":"json","ksxnxq":"-1-1","jsxnxq":"-1-1","pylx":"1"}
```

### 已确认请求参数

- `type`
  - 当前样本固定为 `json`
- `ksxnxq`
  - 起始学年学期
  - 当前样本为 `-1-1`
  - 从命名看像“开始学年学期”过滤条件
- `jsxnxq`
  - 结束学年学期
  - 当前样本为 `-1-1`
  - 从命名看像“结束学年学期”过滤条件
- `pylx`
  - 培养类型
  - 当前样本为 `1`

### 已确认响应

```json
{
  "code":200,
  "msg":null,
  "msg_en":null,
  "content":{
    "xfj":{
      "ZYZRS":616,
      "RANK":"61",
      "XFJ":90.165
    }
  }
}
```

### 字段分析

- 顶层字段
  - `code`
    - 成功为 `200`
  - `content`
    - 结果对象
- `content.xfj`
  - 学分绩相关结果对象
- `XFJ`
  - 学分绩
  - 当前样本值为 `90.165`
- `RANK`
  - 当前排名
  - 当前样本值为字符串 `61`
- `ZYZRS`
  - 专业总人数或排名参与总人数
  - 当前样本值为 `616`

### 如何使用

- 这个接口用于查询学分绩和专业排名，不是课程明细。
- 当前样本中使用 `ksxnxq=-1-1`、`jsxnxq=-1-1`，看起来表示“不过滤学期范围，直接统计全部”。
- 如果后续你补到不同参数组合的返回，我可以继续判断它是否支持按学期区间统计。

## 4. 空教室查询

## 4.1 教学楼列表

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/app/commapp/queryjxllist
- 鉴权: bearer
- Content-Type: application/x-www-form-urlencoded
- 请求体: 空

### 已确认响应

```json
{
  "code":200,
  "msg":"操作成功",
  "msg_en":"操作成功_en",
  "content":[
    {"MC":"教学楼II","DM":"14","MC_EN":"Teaching Building II"},
    {"MC":"教学楼III","DM":"15","MC_EN":"Teaching Building III"}
  ]
}
```

### 字段分析

- `MC`
  - 教学楼中文名
- `DM`
  - 教学楼代码
- `MC_EN`
  - 教学楼英文名，部分楼栋为空

### 如何使用

- 这是空教室查询的第一步，用来获取可选楼栋列表。
- 后续查询具体楼栋时，需要把这里返回的 `DM` 作为 `jxl` 参数传下去。

## 4.2 按日期和楼栋查询空教室占用

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/app/kbrcbyapp/querycdzyxx
- 鉴权: bearer
- Content-Type: application/x-www-form-urlencoded

### 请求体格式

```text
nyr=2026-03-09&jxl=14
```

### 已确认请求参数

- `nyr`
  - 查询日期，格式为 `YYYY-MM-DD`
- `jxl`
  - 教学楼代码，例如 `14` 表示“教学楼II`

### 已确认响应结构

```json
{
  "code":200,
  "msg":"操作成功",
  "msg_en":"操作成功_en",
  "content":[
    {"CDMC":"T2102","CDMC_EN":"T2102","DJ1":"0","DJ2":"0","DJ3":"0","DJ4":"0","DJ5":"0","DJ6":"0"}
  ]
}
```

### 字段分析

- `CDMC`
  - 教室名称
- `CDMC_EN`
  - 教室英文名，当前样本与中文一致
- `DJ1` 到 `DJ6`
  - 分别表示 6 个大节时段的占用状态
  - 当前样本值为字符串 `0`
  - 从上下文看，大概率 `0` 表示空闲

### 大节时间映射

- `DJ1`: 第 1-2 节，08:30-10:15
- `DJ2`: 第 3-4 节，10:30-12:15
- `DJ3`: 第 5-6 节，14:00-15:45
- `DJ4`: 第 7-8 节，16:00-17:45
- `DJ5`: 第 9-10 节，18:45-20:30
- `DJ6`: 第 11-12 节，20:45-22:30

### 如何使用

- 先调用 `/app/commapp/queryjxllist` 拿到楼栋代码。
- 再调用 `/app/kbrcbyapp/querycdzyxx`，按日期和楼栋查询该楼各教室在 6 个大节上的占用情况。
- 当前样本判断：`0` 为空闲，非 `0` 表示占用。

## 5. 选课

## 5.1 查看已选课程

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/app/Xsxk/queryYxkc?_lang=zh_CN
- 鉴权: bearer
- Content-Type: application/json

### 请求体格式

```json
{"RoleCode":"01","p_pylx":"1","p_xn":"2025-2026","p_xq":"2","p_xnxq":"2025-20262","p_gjz":"","p_kc_gjz":"","p_xkfsdm":"yixuan"}
```

### 已确认请求参数

- `RoleCode`
  - 当前样本固定为 `01`
- `p_pylx`
  - 培养类型，当前样本为 `1`
- `p_xn`
  - 学年
- `p_xq`
  - 学期编号
- `p_xnxq`
  - 学年学期组合值，例如 `2025-20262`
- `p_gjz`
  - 通用关键词，当前样本为空
- `p_kc_gjz`
  - 课程关键词，当前样本为空
- `p_xkfsdm`
  - 选课方式代码
  - 当前这里固定为 `yixuan`，表示查看已选

### 已确认响应结构

- 这是一个超长响应。
- 当前已确认它不只是“已选课程列表”，还会同时返回选课模块的初始化配置。

### 已确认返回内容组成

- `yxkcList`
  - 已选课程列表
- `xkgwcList`
  - 当前样本中存在该字段
  - 从命名看像选课购物车列表
- `kbjclist`
  - 课表节次时间列表
- `skyyList`
  - 授课语言列表，当前样本已见 `中文`、`英文`、`双语`
- 多个 `xkgl_*` 配置项
  - 例如是否允许忽略冲突课程
  - 是否允许根据空闲时间查询
  - 缴费方式配置等

### 已确认的课程字段

- `rwh`
  - 任务号
- `kxh`
  - 课序号
- `kcdm`
  - 课程代码
- `kcmc`
  - 中文课程名
- `kcmc_en`
  - 英文课程名
- `dgjsmc`
  - 授课教师名单字符串
  - 常见为多个教师，分隔符可能是全角或半角逗号
- `xf`
  - 学分
- `xs`
  - 学时
- `xksj`
  - 选课时间
- `xkbj`
  - 选课标记，当前样本值为 `0`
- `xm`
  - 学生姓名
- `id`
  - 记录 id

### 如何使用

- 这个接口现在可以视为“已选课程页 + 选课模块初始化配置”的组合接口。
- 如果只是想拉已选课程，它能用。
- 如果你要逆向选课模块配置项，它也是当前最重要的入口之一。
- 如果 `yxkcList` 为空，也可能仍然返回 `content.kbjclist` 等配置数据。
- `kcxx` 字段可能包含 HTML，且包含 `queryJsxx(...)` 形式的教师信息入口。

## 5.2 查看可选课程任务

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/app/Xsxk/queryKxrw?_lang=zh_CN
- 鉴权: bearer
- Content-Type: application/json

### 请求体格式

```json
{"RoleCode":"01","p_pylx":"1","p_xn":"2025-2026","p_xq":"2","p_xnxq":"2025-20262","p_gjz":"","p_kc_gjz":"","p_xkfsdm":"bx-b-b","pageNum":1,"pageSize":10}
```

### 已确认请求参数

- 选课学期参数与 `queryYxkc` 基本一致
- 额外参数
  - `pageNum`
  - `pageSize`
  - `p_xkfsdm`

### 当前已见的 `p_xkfsdm`

- `bx-b-b`
- `xx-b-b`
- `ty-b-b`
- 此外你已补充但尚未逐个抓到完整返回的分类代码:
  - `xzygt`
  - `cxcytx`
  - `shsj`
  - `jsrw`
  - `cxyx`
  - `cxsy`
  - `xmz`
  - `sx`
  - `tsk`
  - `mooc`

### 已确认返回特征

- 返回也是超长结构。
- 当前样本表明它返回课程任务列表，并伴随大量查询条件回显字段。
- 目前已确认两条超长返回都来自这个接口:
  - `p_xkfsdm="bx-b-b"` 对应一条课程任务长返回
  - `p_xkfsdm="xx-b-b"` 对应另一条课程任务长返回
- 当前已在长响应中见到的课程字段包括:
  - `rwh`
  - `kxh`
  - `kcdm`
  - `kcmc`
  - `kcmc_en`
  - `xf`
  - `xs`
  - `xkxs`
  - `xiaoqu`
  - `skfs`
  - `sfxyjf`
  - `sfxysh`
  - `id`

### 已确认课程详情字段

- 基础标识
  - `rwh`: 任务号
  - `rwid`: 任务内部 id
  - `id`: 记录 id
  - `kcid`: 课程 id
  - `kxh`: 课序号
  - `kcdm`: 课程代码
  - `kcmc`: 中文课程名
  - `kcmc_en`: 英文课程名
  - `rwmc`: 任务名称，通常是 `课程名 + 课序号`
  - `rwmc_en`: 英文任务名
- 课程属性
  - `kcxz`: 课程性质代码
  - `kcxzmc`: 课程性质中文名，例如 `任选`、`选修`
  - `kcxzmc_en`: 课程性质英文名
  - `kclb`: 课程类别代码
  - `kclbmc`: 课程类别中文名，例如 `美育类`、`跨专业发展课程`
  - `kclbmc_en`: 课程类别英文名
  - `rwlxmc`: 任务类型中文名，例如 `MOOC`、`跨专业发展课程`
  - `xkfsmc`: 选课方式中文名
  - `xkfsmc_en`: 选课方式英文名
- 开课与校区信息
  - `kkyx`: 开课院系代码
  - `kkyxmc`: 开课院系中文名
  - `kkyxmc_en`: 开课院系英文名
  - `xiaoqu`: 校区代码
  - `xiaoqumc`: 校区中文名
  - `xiaoqumc_en`: 校区英文名
  - `skyydm`: 授课语言代码
  - `skyymc`: 授课语言中文名
  - `skyymc_en`: 授课语言英文名
- 学生维度信息
  - `xh`: 学号
  - `xm`: 学生姓名
  - `njmc`: 年级，例如 `2024`
  - `yxmc`: 院系中文名
  - `yxmc_en`: 院系英文名
  - `zymc`: 专业中文名
  - `zymc_en`: 专业英文名
  - `bjmc`: 班级中文名
  - `bjmc_en`: 班级英文名或班级代码
- 学分学时
  - `xf`: 学分
  - `xs`: 学时
- 容量与人数
  - `zrl`: 总容量
  - `bksrl`: 本科生容量
  - `yjsrl`: 研究生容量
  - `dnrl`: 电脑容量相关字段
  - `dwrl`: 学位容量相关字段
  - `yxzrs`: 已选总人数
  - `bksyxrs`: 本科已选人数
  - `yjsyxrs`: 研究生已选人数
  - `nansyxrs`: 男生已选人数
  - `nvsyxrs`: 女生已选人数
  - `yxzrlrs`: 已选容量人数统计字段
  - `sfxzrl`: 是否限制容量，当前样本已见 `1`
  - `rlxszd`: 容量限制使用字段，当前样本已见 `zrl`
- 开放时间与状态
  - `sfkt`: 是否可投，当前样本已见 `1`
  - `zksfkt`: 总控是否开放，当前样本已见 `0`
  - `cqxzsfkt`: 重修限制是否开放，当前样本已见 `0` 或 `1`
  - `ktxkkssj`: 开始选课时间
  - `ktxkjssj`: 结束选课时间
  - `sfzktsjn`: 是否在可退时间内相关标记
  - `sfzjfsjn`: 是否在缴费时间内相关标记
  - `tksfjw`: 退课是否教务相关标记，当前样本已见 `0`
- 费用与审核相关
  - `sfxyjf`: 是否需要缴费，当前样本已见 `0`
  - `sfxyjfsh`: 是否需要缴费审核，当前样本已见 `0`
  - `sfxysh`: 是否需要审核，当前样本已见 `0`
  - `djfje`: 待缴费金额
  - `yjfje`: 已缴费金额
- 展示与排课相关
  - `kcxx`: 富文本课程展示信息，当前样本中可直接看到教师和上课信息 HTML
  - `pkjgmx`: 富文本排课结果展示块
  - `pkjgmc`: 排课结果中文名
  - `pkjgmc_en`: 排课结果英文名
  - `sksj`: 上课时间，当前有些样本为空
  - `skdd`: 上课地点，当前有些样本为空

### 已确认的返回规律

- `yxkcList` 这个字段名虽然看起来像“已选课程列表”，但在 `queryKxrw` 的长返回里也出现了。
- 当前更稳妥的判断是：这个接口返回的是“当前课程任务数据列表”，字段名沿用了旧命名。
- `kcxx` 很关键，里面往往包含教师、周次、星期、节次、地点等展示信息，且是 HTML 片段。
- `zrl`、`yxzrs` 等字段已经足以做容量和余量判断。

### 如何使用

- 这是当前已确认的“查询可选课程”主接口。
- 通过切换 `p_xkfsdm` 可以查询不同课程池。
- 当前已经确认:
  - `bx-b-b` 会返回一类可选必修课程任务
  - `xx-b-b` 会返回另一类可选课程任务
- 如果你后续补充某个 `p_xkfsdm` 的完整返回，我可以继续把每个类别的精确含义写死。

## 5.3 加入选课购物车

### 接口定义

- 方法: POST
- 路径: /incoSpringBoot/app/Xsxk/addGouwuche?_lang=zh_CN
- 鉴权: bearer
- Content-Type: application/json

### 请求体格式

```json
{"RoleCode":"01","p_pylx":"1","p_xn":"2025-2026","p_xq":"2","p_xkfsdm":"bx-b-b","p_xktjz":"rwtjzyx","p_id":"434EF1311F5CA3D3E0630C18F80ADE30"}
```

### 已确认请求参数

- `p_xkfsdm`
  - 当前课程池代码
- `p_xktjz`
  - 当前样本为 `rwtjzyx`
  - 从命名看像选课提交动作类型
- `p_id`
  - 待加入购物车的课程任务 id

### 已确认失败返回

#### 非选课时间

```json
{"code":200,"msg":null,"msg_en":null,"content":{"gjhczztm":"XKGL.OPERATE.RESULT_BZXKSJN","message":"不在设置的时间范围内，课程：应用光学","jg":"-1"}}
```

#### 请求频率过高

```json
{"code":200,"msg":"选课请求频率过高 请稍后重试！","msg_en":null,"content":{"message":"选课请求频率过高 请稍后重试！","jg":"-1"}}
```

### 字段分析

- `content.message`
  - 服务端返回的中文结果描述
- `content.jg`
  - 结果标记
  - 当前失败样本为 `-1`
- `content.gjhczztm`
  - 操作结果码
  - 当前失败样本是 `XKGL.OPERATE.RESULT_BZXKSJN`

### 如何使用

- 这个接口在你们系统里虽然命名还是“加入购物车”，但实际就是“选课动作”本身。
- 当前由于不在选课时间，暂时没有成功样本，所以还不能确定成功时 `jg`、`message`、`content` 的完整结构。
- 已确认服务端有限流，频繁请求会直接返回“请求频率过高”。

## 登录与课表最小使用指南

### 目标

- 完成登录
- 获取当前学期列表
- 获取某一天课表

### 建议流程

1. 建立一个持久 HTTP Session
2. 调用 /component/queryApplicationSetting/rsa
3. 调用 /c_raskey
4. 调用 /authentication/ldap
5. 取出 access_token
6. 用同一个 Session 调用 /app/commapp/queryxnxqlist
7. 用同一个 Session 调用 /app/kbrcbyapp/querykbrcbyday

### 登录前建议请求头

```text
_lang: cn
authorization: Basic aW5jb246MTIzNDU=
rolecode: 01 或 06
Accept: */*
Content-Type: application/x-www-form-urlencoded
```

### 登录后建议请求头

```text
_lang: cn
authorization: bearer <access_token>
rolecode: 06
Accept: */*
```

### 实现建议

- 不要手动拼接 Cookie，直接复用同一个 requests.Session() 或同类会话对象。
- 登录后的业务调用至少要维持三类状态:
- access_token
- route
- JSESSIONID
- 当前还没有证据表明可以稳定去掉 JSESSIONID，所以先不要省略它。

## Cookie 结论

- route
- 来源: /component/queryApplicationSetting/rsa 响应头
- 作用判断: 网关路由或粘性会话标识
- JSESSIONID
- 来源: /authentication/ldap 成功响应头
- 后续变化: /app/kbrcbyapp/querykbrcbyday 会再次刷新
- 作用判断: Java 应用会话 cookie

## 当前仍未确认的点

- RSA 参数是否在某些版本里必须用于密码加密
- 只带 bearer、不带 JSESSIONID 是否仍能稳定访问课表接口
- refresh_token 的刷新接口路径

## Python 验证脚本

- 脚本: [validate_login.py](validate_login.py)
- 当前已验证内容
- route 的来源
- JSESSIONID 的来源与刷新
- 登录响应字段
- 按天查询课表接口可正常返回
