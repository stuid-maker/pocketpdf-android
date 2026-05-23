# PocketPDF — Screenshots

真机型号：HBN-AL80 (Huawei)
分辨率：1260 × 2844
主题：Material 3 紫色品牌色
截取日期：2026-05-23

## 已截取的截图

| 文件名 | 界面 | 说明 |
|--------|------|------|
| `library-main.png` | 主界面 | LibraryActivity，material toolbar "PocketPDF" |
| `library-menu.png` | 主界面 + 菜单 | 点击 overflow menu 弹出"设置"菜单项 |
| `library-empty.png` | 主界面空状态 | 空文档列表 + FAB + "还没有文档"提示 |
| `library-back.png` | 主界面返回后 | 从其他活动返回后的状态 |
| `settings-main.png` | 设置页 | 预设、地址、模型名、API Key、切块策略、系统提示词 |
| `reader-error.png` | 阅读器错误状态 | 无文档时显示的错误提示 |
| `chat-empty.png` | 聊天页空状态 | 无文档时的聊天界面 |

## 需要手动补充的截图

以下界面需要**先在真机上导入 PDF**（FAB → 选择 PDF），才能正常显示：

| 文件名 | 界面 | 如何截取 |
|--------|------|----------|
| `reader-pdf.png` | 阅读器 PDF 渲染 | 导入 PDF → 点击文档 → 截取渲染页 |
| `reader-summary.png` | 总结弹窗 | 阅读页点击"总结本页"或"总结全文" → 截取 BottomSheet |
| `chat-conversation.png` | 聊天对话 | 阅读器工具栏"问答" → 输入问题 → 截取流式回答 + 引用 chip |
| `settings-model-dropdown.png` | 设置页模型下拉 | 测试连接成功后自动弹出模型列表 |
| `settings-preset-confirm.png` | 预设切换确认 | 手动改 URL 后切预设 → 截取确认对话框 |

## 生成方式

```bash
# 在连接真机/模拟器后：
adb shell am start -n com.asuka.pocketpdf/.ui.library.LibraryActivity
sleep 2
adb exec-out screencap -p > docs/screenshots/filename.png
```
