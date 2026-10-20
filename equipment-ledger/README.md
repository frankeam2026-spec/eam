# 设备运维平台

面向制造企业的设备管理产品，当前处于开发与验收阶段。生产交付范围、现状和缺口见 [企业级验收清单](ENTERPRISE-QUALITY.md)。

## 技术栈

- 前端：Vue 3、TypeScript、Element Plus、Vue Router、Pinia、Vite。
- 后端：Java 21、Spring Boot 3、MyBatis-Plus，无 Mapper XML。
- 数据库：MySQL 8.4，Flyway 管理版本化数据库迁移。
- 打印：Java PDFBox、ZXing，单元格模板设计与 PDF 输出。

工程规范入口见 [工程文档索引](docs/engineering/README.md)，接口入口见 [API清单](docs/engineering/API清单.md)。

本机运行说明见 [LOCAL-RUN.md](LOCAL-RUN.md)，前端维护约定见 [FRONTEND-ARCHITECTURE.md](FRONTEND-ARCHITECTURE.md)。

## 已有模块

设备台账、基础数据、维修工单、组织架构、用户、角色、标签模板与打印。已有代码不代表各模块已通过企业交付验收。

后端基于可信会话身份读取 tenant_id，并校验菜单操作与数据范围。员工仅属于一个部门，可以有多个角色。开发脚本使用 local-dev 配置自动进入演示身份；正式环境不能启用该配置。

基础数据和业务数据由 API/MySQL 提供；列表列宽、显示列与皮肤等个人界面偏好可以保存在浏览器。

## 测试与交付

```sh
npm run build
mvn -f backend/pom.xml test
```

前端要求 Node.js 22 或以上，后端要求 Java 21。后端现有自动化测试覆盖错误响应、操作授权的数据范围合并与跨租户资源拒绝；这些测试不替代真实 MySQL 集成、端到端、性能和视觉回归。

## 待完成范围

当前已实现业务字段配置、设备图片与附件等能力，其完整覆盖和验收状态以对应实施记录为准。生产租户管理、功能模块/用户数/到期授权、离线授权与升级包、生产部署和备份恢复仍需单独建设及验收；可配置审批不属于本轮工程改造范围。

采购、处置、特种设备管理、计量/盘点、IoT 与能耗不在本期开发范围。
