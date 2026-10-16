# 本地运行

运行环境位于项目父目录的 `.local-runtime/`，包含 Java 21、Maven、MySQL 8.4 和数据库文件。环境与密码不进入 Git。

在本目录分别打开三个终端：

```sh
zsh run-local.sh database
zsh run-local.sh backend
zsh run-local.sh frontend
```

页面地址：http://localhost:5177/

前端需要 Node.js 22 或以上。本机脚本优先使用已有的 Node.js 24 运行环境；其他电脑可通过 `EAM_NODE_BINARY` 指定 Node 可执行文件路径。首次运行先执行 `npm ci` 安装锁定的依赖。

前端架构和维护约定见 [FRONTEND-ARCHITECTURE.md](FRONTEND-ARCHITECTURE.md)。

租户编号 `10001`，账号 `admin`。初始密码保存在父目录 `.local-runtime/local.env` 的 `EAM_BOOTSTRAP_ADMIN_PASSWORD` 中。修改用户密码后，该初始密码不再生效。

MySQL 仅监听本机 `127.0.0.1:3306`，数据库为 `eam_demo`。后端使用该文件中的数据库凭据，启动时由 Flyway 自动执行迁移。不要删除 `mysql-data`，其中保存本地业务数据。

修改 Java 代码后，需要先重新打包再重启后端：

```sh
export JAVA_HOME="$(cd .. && pwd)/.local-runtime/jdk-21.0.12.1.jdk/Contents/Home"
../.local-runtime/apache-maven-3.9.11/bin/mvn -f backend/pom.xml -Dmaven.repo.local="$(cd .. && pwd)/.local-runtime/m2" -DskipTests package
```

本地运行用于开发评审，生产部署配置需另行完善。

## 打印模板

单页设计器位于「设备管理 → 设备标签」。模板使用版本化单元格布局，旧坐标模板保持兼容，可显式转换后保存。

PDF 由 Java 统一生成，支持合并区域、自动分页、重复表头、页码、维修明细重复区域、PNG/JPEG图片、二维码和Code128条形码。打印数据与维修明细按登录用户的数据权限读取。未来模块应提供各自的可打印字段与明细数据，复用 `GridPdfService` 与 `PrintGridDesigner`。

本地脚本使用 `/Library/Fonts/Arial Unicode.ttf`。Linux部署时，设置 `EAM_PRINT_FONT` 为支持中文的合法TTF字体路径；字体会嵌入PDF，不依赖客户端字体。

编辑器显示的网格线不打印，实际边框按单元格设置。预览和打印使用同一PDF渲染服务。打印机选择相同纸张尺寸，缩放100%；设备驱动的不可打印边距需要在模板纸张属性中预留。

本地开发启动脚本使用 `local-dev` 配置，绑定127.0.0.1并以演示租户admin身份自动进入。菜单、操作及数据权限仍执行；正式部署不要启用此配置。

## 工单并发集成检查

运行业务测试前，先阅读脚本并核对实际服务地址、数据库、测试身份、写入与清理范围。写入、权限和库存测试优先使用独立测试库；运行配置名称、数据编号前缀或健康检查通过不能代替环境核对。

**历史脚本限制（2026-10-13）：** `scripts/check-repair-concurrency.py` 当前固定连接8080和 `eam_demo`，会创建及清理记录；不要直接对开发业务库运行。先将服务与数据库接入隔离测试环境，再核对清理只触及当前测试拥有的数据。本次只更新运行说明，尚未改造该脚本。

该脚本覆盖版本冲突、缺失版本、正常状态流转和重复旧版本操作。客户端路径可通过 `EAM_MYSQL_BIN` 指定，但改变客户端路径不会改变数据库或服务地址。

`scripts/check-repair-query.py` 检查工单服务端分页、队列、列筛选、跨租户隔离及分批导出，会创建和清理501条跨导出批次记录。运行前同样核对目标环境，不能仅按历史说明或脚本名称直接执行。

其他库存及权限验收脚本也应逐个核对：部分脚本会重置独立测试库的表，只有该测试专用且允许重置的环境才能使用；不得清理共享测试数据、真实用户权限或生产配置。缺少安全测试环境时记录未验证内容，不通过修改真实业务数据补足验收。

## 2026-10-12：后端运行文件隔离

后端从 `.local-runtime/backend-runs/run.*/equipment-ledger-api.jar` 独立副本启动。不要直接运行 Maven `target` 内的 JAR：Spring Boot 延迟加载嵌套类，构建覆盖正在运行的 JAR 会导致 ClassNotFound/NoClassDefFoundError，即使健康检查仍正常，报修、角色等保存请求也会失败。

`run-local.sh backend` 会复制完整构建产物并检查 ZIP 完整性，再启动副本。后端源码改动后仍需完成 Maven package 并重启服务；重新构建不会覆盖当前运行副本。

修复验证：独立 `eam_org_hierarchy_test` 中报修提交、详情回读和角色保存成功；开发库仅执行空请求校验，不创建测试业务记录。
