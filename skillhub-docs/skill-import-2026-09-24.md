# 原 SkillHub 技能迁入 SkillHive（2026-09-24）

## 范围与结果

原 SkillHub 数据库有 24 个已发布 Skill、24 个版本和 98 条技能文件记录，均位于 `global` 空间，由内置发布者发布。SkillHive 按当前产品范围迁入其中 23 个；`skillhub-cli` 对应的代码文件归档在 `legacy-upstream/builtin-skills/skillhub-cli`，没有发布到 SkillHive。原项目文件和业务数据未改动，原数据库卷未删除。

新库最终有 23 个已发布 Skill、23 个版本和 94 条技能文件记录。`/api/v1/resources?resourceType=SKILL&size=100` 返回 23 条；`weather` 下载接口返回 HTTP 200 和 ZIP 文件。

## 迁入方式

原开发环境的本地存储默认指向 `/tmp/skillhub-storage`，检查时该目录已经不存在，因此无法直接复制原服务的技能文件。原项目保留了与数据库条目对应的 24 项发布清单，含下载地址和 SHA-256。SkillHive 复制该清单并排除 `skillhub-cli` 后，用内置发布流程取回 23 个原始包、校验并发布。同步日志记录 `total=23, published=23, failed=0`。

SkillHive 的技能文件保存在 WSL 的 `~/skillhive-data/storage`。发布后用 `scripts/verify-imported-skills.py` 将数据库中的技能名、版本、发布状态与清单比对，并逐一核对 94 个文件的 SHA-256；结果为 `Skills: 23/23; files verified: 94/94`。完成后已关闭启动时的内置技能同步，重启验证数据仍在。

## 保留的备份

- 原 SkillHub 数据库：`/home/wangkun/skillhive-data/backups/skillhub-original-20260924.dump`
- 导入前的 SkillHive 数据库：`/home/wangkun/skillhive-data/backups/skillhive-before-skill-import-20260924.dump`
- 导入后的 SkillHive 数据库：`/home/wangkun/skillhive-data/backups/skillhive-after-skill-import-20260924.dump`
- 原 SkillHub 的 Docker 数据卷 `skillhub_postgres_data` 和 `skillhub_minio_data` 保留；原服务维持停止状态。

在 WSL 中复核当前导入结果：

```bash
python3 /mnt/e/MyProjects/skillhive/scripts/verify-imported-skills.py
```
