#!/usr/bin/env bash

set -euo pipefail

PUBLIC_DIR="${PUBLIC_DIR:-/var/www/uniserve-admin}"
NGINX_CONF_TARGET="${NGINX_CONF_TARGET:-/etc/nginx/conf.d/uniserve-admin.conf}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
NGINX_CONF_SOURCE="${PROJECT_DIR}/deploy/nginx/uniserve-admin.conf"

if ! command -v node >/dev/null 2>&1; then
  echo "node 未安装，请先安装 Node.js 20+。"
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "npm 未安装，请先安装 npm。"
  exit 1
fi

if ! command -v nginx >/dev/null 2>&1; then
  echo "nginx 未安装，请先安装 nginx。"
  exit 1
fi

if ! command -v sudo >/dev/null 2>&1; then
  echo "缺少 sudo，无法写入 ${PUBLIC_DIR} 和 ${NGINX_CONF_TARGET}。"
  exit 1
fi

echo "安装前端依赖..."
cd "${PROJECT_DIR}"
npm ci

echo "构建前端..."
npm run build

echo "发布静态文件到 ${PUBLIC_DIR} ..."
sudo mkdir -p "${PUBLIC_DIR}"
sudo find "${PUBLIC_DIR}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
sudo cp -r "${PROJECT_DIR}/dist/." "${PUBLIC_DIR}/"

echo "安装 nginx 配置到 ${NGINX_CONF_TARGET} ..."
sudo mkdir -p "$(dirname "${NGINX_CONF_TARGET}")"
sudo cp "${NGINX_CONF_SOURCE}" "${NGINX_CONF_TARGET}"

echo "校验 nginx 配置..."
sudo nginx -t

echo "重载 nginx ..."
sudo systemctl reload nginx

echo "部署完成，访问: http://10.10.0.9/"
