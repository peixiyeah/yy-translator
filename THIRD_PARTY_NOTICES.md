# 第三方组件与数据

YY 翻译的原创应用代码采用 MIT License，完整文本见根目录 LICENSE。下面列出的第三方材料仍遵循各自的许可证。

| 材料 | 来源与版本 | 许可及随包文件 |
| --- | --- | --- |
| 中文和日文读音表 | Unicode Unihan 17.0.0 的 Unihan_Readings.txt | Unicode License v3，app/src/main/res/raw/unicode_license.txt |
| Kotlin 标准库 | JetBrains Kotlin 2.2.20 | Apache License 2.0，app/src/main/res/raw/apache_license.txt 及 kotlin_notice.txt |
| JetBrains annotations | org.jetbrains annotations 13.0 | Apache License 2.0 |
| Gradle Wrapper | Gradle 8.14 | Apache License 2.0，脚本内保留原有版权声明 |

中文表根据 kMandarin、kHanyuPinyin 和 kHanyuPinlu 生成，去除声调标记，用 v 表示 ü，并结合字频与 GB2312 范围排序。日文表根据 kJapanese 生成，将片假名读音转为平假名。原始 Unicode 数据可从 https://www.unicode.org/Public/17.0.0/ucd/Unihan.zip 下载。

读音数据、许可文本均随 APK 放在 res/raw 中。应用代码可以按 raw 资源名访问这些文本。

源码内的 Gradle、Android Gradle Plugin、测试库等构建工具按各自许可使用。
