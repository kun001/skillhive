# SkillHive 本机启动与停止

项目源码位于 `E:\MyProjects\skillhive`，在 Ubuntu-22.04 WSL 中对应 `/mnt/e/MyProjects/skillhive`。前端依赖、技能文件与 Docker 数据位于 WSL；技能文件默认保存在 `~/skillhive-data/storage`。启动脚本使用本项目的 `compose.local.yml`、独立的 `skillhive` Compose 项目和本项目后端 JAR。

在 Windows PowerShell 中执行：

```powershell
wsl -d Ubuntu-22.04 -- bash /mnt/e/MyProjects/skillhive/scripts/start-local-wsl.sh
```

停止时执行：

```powershell
wsl -d Ubuntu-22.04 -- bash /mnt/e/MyProjects/skillhive/scripts/stop-local-wsl.sh
```

查看当前状态：

```powershell
wsl -d Ubuntu-22.04 -- bash /mnt/e/MyProjects/skillhive/scripts/status-local-wsl.sh
```

停止不会删除数据库与对象存储数据卷。默认端口与旧 SkillHub 相同，两套服务不能同时按默认配置运行。服务地址：Web `http://localhost:3000/`、API `http://localhost:8080/`、扫描器 `http://localhost:8000/`、MinIO 控制台 `http://localhost:19011/`。运行日志在本项目 `.dev/server.log` 和 `.dev/web.log`。

更改后端源码后，需要重新构建 `server/skillhub-app/target/skillhub-app-0.1.0.jar` 并重启 SkillHive；前端源码由 Vite 自动更新。不要使用 `/opt/skillhub-deps/start.sh` 启动此项目，该脚本固定指向原 SkillHub。

技能文件保存在 `~/skillhive-data/storage`；可用 `python3 /mnt/e/MyProjects/skillhive/scripts/verify-imported-skills.py` 验证已导入的技能与文件。原库迁入记录见 `docs/skill-import-2026-09-24.md`。

2026-09-24 启动验证：SkillHive 的 Web、API、扫描器均返回 HTTP 200；4 个容器健康，Flyway 到版本 66，知识库 4 张基础表已创建。旧套件与 CLI 接口不在当前 OpenAPI 清单中，带本地测试身份访问返回 HTTP 404。旧 SkillHub 进程已停止，旧数据卷仍保留。
