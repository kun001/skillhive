# 技能易读说明

启用后，每次成功上传技能版本都会自动排队，根据技能名称、原简介和 SKILL.md 正文生成中文及英文的「功能描述」「使用方法」。使用方法直接说明用户需要向 AI 提供的资料、任务要求，并附一句示例请求。生成前会移除正文中的代码块和超长行，减少 API 签名对说明的干扰，原文件保持完整。说明保存在对应版本中，切换版本或页面语言时展示相应内容；原简介保留在正文上方。现有版本不会在启用时批量发送给模型，上传新版本才会触发生成。

这是展示辅助功能，生成结果可能存在偏差，应结合原始技能文档判断。生成不会执行技能里的指令、脚本或工具，也不改变审核、扫描和发布流程。

## 部署配置

使用服务器环境变量或发布环境的 `.env`，配置后重启 server。`compose.release.yml` 已转发这些变量，`.env.release.example` 提供全部默认值。API Key 只在服务端使用，不出现在浏览器、详情接口或日志中。使用外部服务时，启用此功能意味着会把上传的上述技能资料发送到配置的服务，包括私有技能；请使用符合团队数据要求的服务。

```dotenv
SKILLHUB_INTRODUCTION_ENABLED=true
SKILLHUB_INTRODUCTION_BASE_URL=https://llm.example.com/v1
SKILLHUB_INTRODUCTION_MODEL=your-model
SKILLHUB_INTRODUCTION_API_KEY=your-server-side-key
```

接口为 OpenAI 兼容的 `POST {BASE_URL}/chat/completions`。本地 LM Studio 可留空 API Key；服务在容器中运行时，BASE_URL 必须是容器能够访问的地址，不能使用指向容器自身的 localhost。

| 变量后缀（均以 SKILLHUB_INTRODUCTION_ 开头） | 默认值 | 用途 |
| --- | --- | --- |
| ENABLED | false | 开启上传自动生成 |
| BASE_URL / MODEL / API_KEY | 空 | 服务地址、模型名、服务端密钥 |
| CONNECT_TIMEOUT / READ_TIMEOUT | PT5S / PT120S | 连接及单次请求超时（ISO 8601 时长） |
| TEMPERATURE / MAX_TOKENS | 0.2 / 2048 | 采样温度、输出 token 上限 |
| MAX_INPUT_CHARS | 16000 | 提交资料字符上限，超出部分截断 |
| RESPONSE_FORMAT | json_schema | 可选 json_schema、json_object、none；按模型服务支持情况设置 |
| MAX_ATTEMPTS / RETRY_DELAY | 3 / PT30S | 最多尝试次数及重试基础间隔（后续按次数递增） |
| POLL_INTERVAL_MS | 5000 | 后台队列检查间隔 |

生成期间页面显示原简介和等待提示，完成后自动刷新。超时、模型错误、格式不正确或内容为空时自动重试；次数耗尽后保留原简介，不阻断上传。重启会继续尚未完成的任务；多实例用数据库租约避免同时处理同一任务，旧任务不能覆盖新租约的结果。关闭开关会暂停任务处理。

说明随版本权限校验后返回，未发布版本不会通过公开详情泄露；删除版本时相应说明和任务也会删除。
