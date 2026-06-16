#!/bin/bash
# ============================================================
# knowledge-assistant 运维脚本（一人公司版）
# 核心能力: 部署(带版本标签) / 回滚 / 健康检查 / 日志 / 状态
#
# 用法:
#   ./scripts/ops.sh deploy [版本]   - 部署（版本默认时间戳，回滚的基础）
#   ./scripts/ops.sh rollback [版本] - 回滚到指定版本（无参数则回到上一个）
#   ./scripts/ops.sh health          - 健康检查（actuator）
#   ./scripts/ops.sh status          - 服务状态
#   ./scripts/ops.sh logs [服务]     - 查看日志（默认 app）
#   ./scripts/ops.sh stop            - 停止服务
#   ./scripts/ops.sh versions        - 列出所有历史版本（镜像）
#
# 设计原则:
#   - 每次部署打版本标签，保留镜像，支持秒级回滚
#   - 部署后自动健康检查，失败提示回滚
#   - 部署前自动备份当前版本号，支持无参回滚
# ============================================================

set -e

PROJECT_NAME="ka"
APP_SERVICE="app"
HEALTH_URL="http://localhost:8080/actuator/health"
HEALTH_TIMEOUT=90
VERSION_FILE=".last-version"
VERSIONS_DIR=".versions"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
info()    { echo -e "${BLUE}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[OK]${NC} $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

cd "$(dirname "$0")/.." || error "无法切换到项目根目录"
mkdir -p "${VERSIONS_DIR}"

now_tag() { date +%Y%m%d-%H%M%S; }
current_version() { [ -f "${VERSION_FILE}" ] && cat "${VERSION_FILE}" || echo ""; }

record_version() {
  local v="$1"; local tmp; tmp=$(mktemp)
  echo "${v}" > "${tmp}"
  [ -f "${VERSIONS_DIR}/history" ] && grep -v "^${v}$" "${VERSIONS_DIR}/history" >> "${tmp}"
  mv "${tmp}" "${VERSIONS_DIR}/history"
}

wait_health() {
  info "等待应用健康 (${HEALTH_URL})，最多 ${HEALTH_TIMEOUT}s..."
  local elapsed=0
  while [ ${elapsed} -lt ${HEALTH_TIMEOUT} ]; do
    if curl -sf "${HEALTH_URL}" > /dev/null 2>&1; then
      success "应用健康检查通过"; return 0
    fi
    sleep 3; elapsed=$((elapsed + 3)); printf "."
  done
  echo ""; return 1
}

deploy() {
  local version="${1:-$(now_tag)}"
  local prev; prev=$(current_version)
  command -v docker &> /dev/null || error "Docker 未安装/未启动"
  docker info &> /dev/null || error "Docker daemon 未运行"

  info "部署版本: ${version}"
  [ -n "${prev}" ] && info "当前运行版本: ${prev}（已备份，可回滚）"
  [ -n "${prev}" ] && echo "${prev}" > "${VERSIONS_DIR}/.prev"

  info "构建镜像 ${PROJECT_NAME}:${version} ..."
  docker build -t "${PROJECT_NAME}:${version}" .
  docker tag "${PROJECT_NAME}:${version}" "${PROJECT_NAME}:latest"

  info "启动服务..."
  docker compose up -d

  if wait_health; then
    echo "${version}" > "${VERSION_FILE}"
    record_version "${version}"
    success "部署成功: ${version}"
    info "回滚命令: ./scripts/ops.sh rollback"
  else
    error "健康检查失败！请检查: ./scripts/ops.sh logs"
  fi
}

rollback() {
  local target="${1:-}"
  if [ -z "${target}" ]; then
    if [ -f "${VERSIONS_DIR}/.prev" ]; then
      target=$(cat "${VERSIONS_DIR}/.prev")
    else
      error "未指定回滚版本且无历史。可用版本: ./scripts/ops.sh versions"
    fi
  fi

  info "回滚到版本: ${target}"
  local curr; curr=$(current_version)
  [ -n "${curr}" ] && echo "${curr}" > "${VERSIONS_DIR}/.prev"

  if ! docker image inspect "${PROJECT_NAME}:${target}" > /dev/null 2>&1; then
    error "镜像 ${PROJECT_NAME}:${target} 不存在（可能已清理）。可用: ./scripts/ops.sh versions"
  fi

  info "切换到镜像 ${PROJECT_NAME}:${target} ..."
  docker tag "${PROJECT_NAME}:${target}" "${PROJECT_NAME}:latest"
  docker compose up -d --no-deps "${APP_SERVICE}"

  if wait_health; then
    echo "${target}" > "${VERSION_FILE}"
    record_version "${target}"
    success "回滚成功: ${target}"
  else
    error "回滚后健康检查仍失败，请检查: ./scripts/ops.sh logs"
  fi
}

health() {
  info "健康检查: ${HEALTH_URL}"
  if curl -sf "${HEALTH_URL}" 2>/dev/null; then
    echo ""; success "应用健康"
  else
    error "应用不健康或未启动"
  fi
}

status() {
  echo ""; info "=== 服务状态 ==="
  docker compose ps 2>/dev/null || warn "compose 未启动"
  echo ""; info "当前版本: $(current_version || echo '未知')"
  echo ""; info "=== 资源占用 ==="
  docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" 2>/dev/null | grep -E "${PROJECT_NAME}|NAME" || true
}

logs() {
  local svc="${1:-${APP_SERVICE}}"
  info "日志: ${svc} (Ctrl+C 退出)"
  docker compose logs -f --tail=100 "${svc}"
}

stop() {
  info "停止服务..."; docker compose down
  success "已停止（数据卷保留：redis-data, ollama-data）"
}

versions() {
  info "=== 已部署版本（最新在前）==="
  [ -f "${VERSIONS_DIR}/history" ] && cat "${VERSIONS_DIR}/history" || echo "（无历史）"
  echo ""; info "=== 本地镜像 ==="
  docker images "${PROJECT_NAME}" --format "table {{.Repository}}\t{{.Tag}}\t{{.CreatedAt}}\t{{.Size}}" 2>/dev/null || true
  echo ""
  warn "清理旧镜像释放空间: docker image prune（清理后对应版本将无法回滚）"
}

case "${1}" in
  deploy|d)    deploy "$2" ;;
  rollback|r)  rollback "$2" ;;
  health|h)    health ;;
  status|s)    status ;;
  logs|l)      shift; logs "$1" ;;
  stop|down)   stop ;;
  versions|v)  versions ;;
  *)
    echo "knowledge-assistant 运维脚本（一人公司版）"
    echo "用法: $0 {deploy|rollback|health|status|logs|stop|versions}"
    echo "  deploy [版本]   部署（默认时间戳版本，自动健康检查）"
    echo "  rollback [版本] 回滚（默认回到上一个版本）"
    echo "  health          健康检查 (actuator/health)"
    echo "  status          服务状态 + 资源占用 + 当前版本"
    echo "  logs [服务]     查看日志（默认 app，可选 redis/ollama）"
    echo "  stop            停止服务（保留数据卷）"
    echo "  versions        列出历史版本与本地镜像"
    exit 1
    ;;
esac
