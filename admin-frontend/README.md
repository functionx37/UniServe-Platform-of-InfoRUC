# 信息学院管理后台前端

这是按同学 D 分工从零生成的 PC 管理后台演示版，覆盖以下需求：

- `F-10` 精准信息推送
- `F-11` 多渠道通知发送
- `F-14` Excel / CSV 批量导入导出
- 管理端侧边栏结构与教师后台界面
- Docker 化部署说明

## 本地运行

```bash
npm install
npm run dev
```

默认访问地址：

```text
http://localhost:5173
```

## 功能说明

1. `总览`
   - 展示待发布通知、近 7 日发送任务、最近导入成功率。
2. `批量导入导出`
   - 支持读取 `.xlsx`、`.xls`、`.csv`。
   - 支持下载导入模板和导出当前通知数据。
3. `精准推送`
   - 可按年级、专业、身份标签组合筛选学生。
   - 支持站内消息、邮件、导出名单三种渠道组合。
4. `联调与测试`
   - 给出建议接口路径、联调要求和测试建议。

## Docker 构建

```bash
docker build -t rucapp-admin .
docker run -p 8080:80 rucapp-admin
```

运行后访问：

```text
http://localhost:8080
```

## 宿主机 Nginx 部署

适用于服务器 IP 为 `10.10.0.9`，并希望直接通过 `http://10.10.0.9/` 访问前端的场景。

前置条件：

- 已安装 Node.js 20+、npm、nginx
- 当前用户可通过 `sudo` 写入 `/var/www` 和 `/etc/nginx`

部署脚本会执行以下操作：

1. 安装依赖并构建前端
2. 将静态产物发布到公共目录 `/var/www/uniserve-admin`
3. 安装 nginx 配置 `deploy/nginx/uniserve-admin.conf`
4. 校验并重载 nginx

执行命令：

```bash
chmod +x scripts/deploy-to-nginx.sh
./scripts/deploy-to-nginx.sh
```

部署完成后访问：

```text
http://10.10.0.9/
```

说明：

- SPA 路由已配置 `try_files`，刷新页面不会返回 404
- `/auth/*`、`/admin/*`、`/files/*` 会反向代理到 `http://127.0.0.1:8081`
- 若后端不在 `8081`，请同步修改 `deploy/nginx/uniserve-admin.conf`

## 后续联调点

- 将 `src/mockData.ts` 替换为真实后端接口返回。
- 将推送发送逻辑对接 `POST /admin/push/send`。
- 将导入后的通知记录写入数据库，并保留操作日志。
- 增加登录鉴权与角色控制，确保学生角色不可访问后台。
