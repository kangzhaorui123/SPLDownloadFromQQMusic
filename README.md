# SPLDownloadFromQQMusic
这是一个从QQ音乐下载歌词的小程序，支持普通歌词和[SPL逐字歌词](https://moriafly.com/standards/spl.html)，建议配合音乐标签和[椒盐音乐](https://github.com/Moriafly/SaltPlayerSource)使用。**目前只测试了小米音乐**。

为了方便音乐标签APP的识别和导入，歌词后缀一律使用`lrc`。歌词的命名方式是`[歌曲名 - 歌手甲&歌手乙&···.lrc]`。

下载的普通歌词默认保存到`/sdcard/Download/LRC/`，逐字歌词保存到`/sdcard/Download/SPL/`。

本项目由[落月API](https://github.com/lvluoyue/api-doc)驱动，所有代码均由DeepSeek生成，本人仅作修正和发布。
