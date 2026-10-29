# 沏 · Android 本地音乐播放器

按设计草图（拟物软阴影 / 墨绿 + Hi-Res 金 / 茶室气质）从零实现的安卓本地音乐播放器。

- 包名 `com.qi.player`，应用名「沏」
- minSdk 26（Android 8.0）/ targetSdk 34
- Kotlin + Jetpack Compose + Media3（ExoPlayer）
- 不依赖 Google Play 服务、Firebase、任何账号体系

## 构建

```bash
cd qi-player
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

构建环境（本机已验证）：JDK 17、Android SDK Platform 34、Build-Tools 34.0.0、Gradle 8.2。
`local.properties` 中的 `sdk.dir` 需指向本机 SDK 路径。

release 包使用 debug 签名，目的是去掉 `debuggable` 标记以获得正常帧率；
可直接覆盖安装同签名的后续版本，不会丢设置与收藏。

## 界面结构（对照草图）

| 草图元素 | 实现 |
| --- | --- |
| 顶部：设置圆钮 + 明暗开关 | `PlayerScreen` 顶部行，`NeuCircleButton` + `NeuToggle` |
| 中部：方形封面，点击翻转歌词 | `FlipStage`，`rotationY` + `cameraDistance` 真 3D 翻转 |
| 歌名 + Hi-Res 金标 | `SongInfo`，命中 Hi-Res 时整行转金 |
| 细进度条 | `QiProgressBar`，6dp 视觉 / 24dp 热区，按下即定位 |
| 三枚控制键 | 54 / 74 / 54dp，按下翻内凹 |
| 下拉曲库 | `LibraryPanel`，搜索 + 歌曲/歌手/专辑/歌单四页签 |
| 上滑 10 段调音台 | `EqualizerPanel`，10 个推子按绝对位置映射，真实生效 |
| 右侧半圆滚轮 | `WheelPanel`，拖动吸附，2.5 秒无操作自动收起 |
| 左侧设置抽屉 | `SettingsDrawer`，文件夹导入 / 联网歌词 / 强调色 / 淡入淡出 / 无缝 |

## 关键实现决策

### 十段均衡器是自己写的 DSP，不用系统 Equalizer

`android.media.audiofx.Equalizer` 的频段数由厂商决定，绝大多数机型只有 5 段，
蓝牙输出下还有相当比例直接不可用。草图要 10 段且每段都要能动，
所以本项目的 `TenBandEqualizer` 是一个 `BaseAudioProcessor`：

- 每声道 10 个串接 peaking biquad（RBJ Audio EQ Cookbook），31Hz–16kHz 倍频程分布，Q=1.41
- 通过自定义 `RenderersFactory` 的 `buildAudioSink` 挂进音频链，
  不是事后 attach，因此播放中途开调音台也立即生效
- 系数与滤波状态分离，UI 线程换系数不加锁；音频线程每个 buffer 只读一次系数引用
- 全 0dB 时走直通分支，不做无用运算

### 拟物效果没有用模糊滤镜

Compose 的 `Modifier.shadow` 只能画单向外阴影，`BlurMaskFilter` 在硬件加速下失效。
所以用三层拼出来：暗侧外阴影（`shadow` 带 `spotColor`，API 28+ 为有色阴影）
+ 亮侧渐变描边（跨 API 稳定）+ 内凹构件改在四边内侧贴渐变条。
代价是没有真正的模糊过渡，收益是任何机型上都不会「糊成一片」或直接失效。

### 「交叉渐入」是淡出 + 淡入，不是双流叠加

真正的 crossfade 需要两个播放器实例同时出声，资源翻倍而在手机上听感差异极小。
本项目用单播放器做音量斜坡，并在设置项副标题里如实写明「非双流叠加」。

### Hi-Res 判定宁可漏标，不可虚标

采样率 > 48kHz，或位深 ≥ 24bit，或码率 ≥ 2822kbps 才标 HR。
参数拿不到时一律不标。位深只有 PCM 类格式能读到，有损格式只显示采样率。
列表右侧：Hi-Res 显示「24bit/96kHz」这样的规格，普通曲目显示时长。

### 歌词三级来源

1. 导入文件夹内的同名 `.lrc`（SAF 目录扫描时一并收集，离线最准）
2. 联网 LRCLIB（默认关闭，需用户在设置里显式开启）
3. 都没有就显示「尚无歌词」并提示做法

Android 13+ 的 `READ_MEDIA_AUDIO` 不覆盖 `.lrc` 这类非音频文件，
所以直接读 MediaStore 曲目的同目录歌词在 13+ 上拿不到；
这也是保留「导入文件夹」这条通道的实际原因之一。

## 与草图的差异（有意为之）

1. **强调色改成色相条**：草图是自由取色，但拟物界面靠同色系明暗关系撑立体感，
   自由选高饱和色会当场破坏阴影关系。这里锁定低饱和，只放开色相。
2. **加了进度条时间刻度**（0:35 / 4:12），草图没有，但没有时间读数不方便。
3. **歌词行可点击跳转**，并在歌词页右上角加了返回封面的按钮——草图里点击歌词区域
   只是翻回封面，会与「点歌词跳转」冲突，二者只能取更实用的那个。
4. **歌单页签**呈现为「我喜欢的音乐 / 最近播放」，通过长按歌曲收藏。

## 已知限制

- 未在真机/模拟器上运行验证（本机构建环境无模拟器镜像、无连接设备），
  逻辑正确性依编译期校验与静态审查保证；首次安装后建议先验证播放与均衡器。
- 无损格式解码取决于设备：ExoPlayer 原生支持 FLAC/ALAC/WAV/MP3/AAC/OGG，
  DSF/APE 需额外扩展，本项目未接入。
- 歌单目前只支持收藏与最近播放，不支持自建歌单与拖拽排序。
