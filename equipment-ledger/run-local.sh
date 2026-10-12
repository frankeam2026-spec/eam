#!/bin/zsh
set -eu
PROJECT_DIR="${0:A:h}"
RUNTIME_DIR="${PROJECT_DIR:h}/.local-runtime"
source "$RUNTIME_DIR/local.env"
export EAM_PRINT_FONT="${EAM_PRINT_FONT:-/Library/Fonts/Arial Unicode.ttf}"
export JAVA_HOME="$RUNTIME_DIR/jdk-21.0.12.1.jdk/Contents/Home"
case "${1:-}" in
  database)
    exec "$RUNTIME_DIR/mysql-8.4.6-macos15-arm64/bin/mysqld" --no-defaults --basedir="$RUNTIME_DIR/mysql-8.4.6-macos15-arm64" --datadir="$RUNTIME_DIR/mysql-data" --bind-address=127.0.0.1 --port=3306 --socket="$RUNTIME_DIR/mysql.sock" --mysqlx=OFF
    ;;
  backend)
    cd "$PROJECT_DIR/backend"
    exec "$JAVA_HOME/bin/java" -jar target/equipment-ledger-api-0.1.0.jar --spring.profiles.active=local-dev
    ;;
  frontend)
    cd "$PROJECT_DIR"
    NODE_RUNTIME="${EAM_NODE_BINARY:-/Users/frank/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node}"
    if [[ ! -x "$NODE_RUNTIME" ]]; then NODE_RUNTIME="$(command -v node)"; fi
    if [[ "$("$NODE_RUNTIME" -p 'Number(process.versions.node.split(".")[0])')" -lt 22 ]]; then
      echo '前端开发需要 Node.js 22 或以上，请设置 EAM_NODE_BINARY。' >&2
      exit 1
    fi
    exec "$NODE_RUNTIME" node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5177 --strictPort
    ;;
  *) echo '用法：zsh run-local.sh database|backend|frontend'; exit 1 ;;
esac
