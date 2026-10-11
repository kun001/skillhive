# 宝塔生产环境部署

生产环境运行在宝塔服务器的 `/www/skillhive`，用该目录下的 `compose.yml`（项目名 `skillhive-cloud`）运行 postgres、redis、skill-scanner、server、web 五个容器。镜像使用本地名称 `skillhive-<组件>:<标签>`，标签写在 `.env` 的 `SKILLHIVE_IMAGE_TAG`（scanner，也是默认值）、`SKILLHIVE_SERVER_IMAGE_TAG`、`SKILLHIVE_WEB_IMAGE_TAG` 中。宝塔的 Nginx 把站点反向代理到 `127.0.0.1:8081`（web 容器）。

`compose.yml`、`.env` 只存在于服务器上，不进仓库。

## deploy.sh 做什么

[`deploy.sh`](deploy.sh) 部署一个已发布的版本（`vX.Y.Z`，见 [部署文档 6.1](../../skillhive-docs/09-deployment.md#61-发布新版本)）：

1. 预检（不改动线上）：磁盘剩余空间大于 3 GB、`compose.yml` 可解析、`.env` 中 `BOOTSTRAP_ADMIN_ENABLED=false`、`SESSION_COOKIE_SECURE=true`、公开地址为 https；记录当前 Flyway 版本。
2. 拉取 `ghcr.io/kun001/skillhive-{scanner,server,web}:vX.Y.Z`，校验三个镜像的 `org.opencontainers.image.revision` 一致（设置了 `EXPECTED_REVISION` 时还要等于它），再改名为 `compose.yml` 使用的本地名称。
3. 在 `.release-vX.Y.Z-<时间>.XXXXXX/` 中备份 `.env`、`compose.yml` 和容器列表；停止 server 后 `pg_dump` 备份数据库并校验。
4. 把 `.env` 中三个镜像标签改为 `vX.Y.Z`，依次启动 scanner、server、web 并等待健康检查。
5. 检查 Flyway 版本没有倒退、五个容器都健康且使用新镜像、本机 `/nginx-health`、公开地址首页、`/api/v1/auth/methods` 和技能列表接口。
6. 成功后写入 `.release-vX.Y.Z.complete` 和 `deployment.log`。

第 3 步之后任何一步失败，脚本会恢复备份的 `.env` 和 `compose.yml` 并重新启动原来的镜像；数据库备份保留在备份目录中。新版本包含数据库迁移且已经执行时，回退镜像后可能还需要用备份恢复数据库。

## 通过 GitHub Actions 部署

在 GitHub 的 Actions → **Deploy production** → Run workflow，填入版本号（如 `v0.1.0`）。工作流用 SSH 把 `deploy.sh` 复制到服务器并执行，`EXPECTED_REVISION` 取该版本标签对应的提交。同一时间只运行一个部署。

### 首次配置

1. **在服务器上生成部署专用密钥**（宝塔「终端」中执行）：

   ```bash
   ssh-keygen -t ed25519 -N '' -C github-actions-deploy -f /root/.ssh/skillhive_deploy
   # restrict：禁止端口转发、终端分配等，只允许执行命令
   echo "restrict $(cat /root/.ssh/skillhive_deploy.pub)" >> /root/.ssh/authorized_keys
   chmod 600 /root/.ssh/authorized_keys
   cat /root/.ssh/skillhive_deploy          # 私钥：填入 DEPLOY_SSH_KEY
   echo "<服务器公网 IP> $(cut -d' ' -f1,2 /etc/ssh/ssh_host_ed25519_key.pub)"   # 主机指纹：填入 DEPLOY_KNOWN_HOSTS
   ```

   把私钥填入 GitHub 后，删除服务器上的私钥文件：`rm /root/.ssh/skillhive_deploy`（公钥已写入 `authorized_keys`）。

2. **在 GitHub 仓库添加 Secrets**（Settings → Secrets and variables → Actions → New repository secret）：

   | Secret | 值 |
   | --- | --- |
   | `DEPLOY_HOST` | 服务器公网 IP |
   | `DEPLOY_SSH_KEY` | 上一步输出的私钥全文（含 `-----BEGIN`/`-----END` 行） |
   | `DEPLOY_KNOWN_HOSTS` | 上一步 `echo` 输出的那一行（`IP ssh-ed25519 AAAA...`） |
   | `DEPLOY_USER` | 可选，默认 `root` |
   | `DEPLOY_PORT` | 可选，默认 `22` |

3. **放行 SSH**：GitHub 托管的 Runner 没有固定 IP，宝塔「安全」和云服务商安全组需要允许外部访问 SSH 端口。建议同时关闭 root 密码登录，只保留密钥登录。

4. 可以在 Settings → Environments → `production` 中添加 Required reviewers，部署前需要指定的人批准。

要停用自动部署，从 `/root/.ssh/authorized_keys` 删除 `github-actions-deploy` 那一行即可。

## 在服务器上手动部署

```bash
cd /www/skillhive
curl -fsSL -o deploy-release.sh https://raw.githubusercontent.com/kun001/skillhive/main/deploy/baota/deploy.sh
EXPECTED_REVISION=<版本标签对应的提交 SHA> bash deploy-release.sh vX.Y.Z
```

## 手动回退

部署成功后如需回到上一版本，用部署输出的 `BACKUP=` 目录：

```bash
cd /www/skillhive
cat <备份目录>/.env > .env
docker compose --env-file .env -f compose.yml up -d --no-deps --wait skill-scanner server web
```

旧版本镜像仍在本机（`docker images | grep skillhive`），回退不需要重新拉取。
