# StarWOL

局域网唤醒（Wake-on-LAN）的安卓客户端。深色主题，原生 Kotlin + Jetpack Compose。

## 功能

- 设备卡片：点一下发 WOL，长按/点右上角进入编辑
- 发送单播 magic packet 到 `IP:端口`，可选再向 `255.255.255.255` 广播一遍
- 发出后探测目的 IP 的回包（UDP 监听 + ICMP ping + 常见端口探测）
- 每天定时自动唤醒，滚轮选时间（24h 循环）
- 工作模式：正常 / Root
  - 正常模式：逐项申请通知、电池优化豁免、精确闹钟、自启动、后台限制、无障碍
  - Root 模式：检测 root，把应用写进 Doze 白名单 / 待机桶 / 后台 op 允许列表

## 本地构建

需要 JDK 17 + Android SDK（platform 34、build-tools 34.0.0）。

```bash
# local.properties 里写 sdk.dir=<你的 Android SDK 路径>
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

国内网络可以在 `gradle.properties` 或环境变量里开镜像：

```bash
export USE_CN_MIRROR=true
```

## GitHub Actions 打包

推送到 `main` 或有 tag 时自动跑 `.github/workflows/build.yml`，产出：

- `StarWOL-debug`：可直接安装的 debug 包
- `StarWOL-release`：release 包

release 默认用工程内 `.keystore/starwol.jks`（密码都是 `starwol`）。要换成自己的签名，在仓库 Secrets 里配：

| Secret | 说明 |
| --- | --- |
| `KEYSTORE_PATH` | keystore 文件路径（CI 里需先解码写入） |
| `KEYSTORE_PASSWORD` | store 密码 |
| `KEY_ALIAS` | 别名 |
| `KEY_PASSWORD` | key 密码 |

## 保活说明

WOL 定时任务要活得久，单靠一个 App 是打不过国产 ROM 的。这里做了两层：

1. 前台服务 + 精确闹钟 + 开机广播（正常模式，靠白名单）
2. root 下把包名写进 `deviceidle whitelist`、`am set-standby-bucket active`、`appops RUN_IN_BACKGROUND`

设置页里能逐项申请权限，缺哪个补哪个。

## 目录

```
app/src/main/java/com/starchen/wol/
├── MainActivity.kt          应用入口 + 页面切换 + 发送结果弹窗
├── data/                    设备模型 + DataStore 持久化
├── wol/                     MAC 解析、magic packet、回包探测
├── service/                 前台服务、定时调度、开机广播、保活
└── ui/                      主页、编辑页、设置页、滚轮组件
```
