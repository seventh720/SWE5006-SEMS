# DAST Baseline

日期：2026-10-20（SGT）。使用官方 ZAP 2.17.0 镜像，并固定镜像 digest。

## 执行范围

[脚本](../scripts/security/dast.sh)运行[ZAP Baseline](https://www.zaproxy.org/docs/docker/baseline-scan/)，爬行1分钟后完成被动扫描。显式访问首页、登录页、活动列表、公开活动GET接口与健康接口，防止只扫描SPA的空HTML入口；同时爬取关联静态资源。关闭爬虫表单处理与POST提交，不创建线上账号或订单，不执行主动攻击扫描。

这是未登录公开范围的DAST基线，不包含登录后多角色接口、SQL注入/XSS主动攻击验证、业务越权、并发、压力测试或完整浏览器E2E。已有权限/事务集成测试和人工验收继续独立保留，不能用本报告替代。

## 本次实测

| 环境 | URL数 | 高危 | 中危 | 低危 | 信息 | 结果 |
|---|---:|---:|---:|---:|---:|---|
| 云端 `https://swe5006.seventh720.com`，已部署基线 `fce7f1f` | 22 | 0 | 2 | 7 | 5 | 按门槛失败，发现缺少CSP和防嵌入头 |
| 首次安全头修复，隔离HTTPS `https://dast.local` | 22 | 0 | 0 | 2 | 5 | 通过中高危门槛，继续修复COOP/COEP低危 |
| 补齐COOP/COEP后的最终镜像，隔离PostgreSQL/后端/前端及临时Caddy HTTPS | 22 | 0 | 0 | 0 | 5 | 66项规则通过，0失败、0警告、0忽略 |

计数为ZAP JSON中按来源站点划分的告警条目；同一规则可有多个实例或跨HTTP/HTTPS站点出现。没有将规则标为IGNORE，也没有将所有警告统一当作通过。

修复内容：Nginx统一发送CSP、`X-Frame-Options`、`X-Content-Type-Options`、HTTPS HSTS、Referrer/Permissions/Cross-Origin-Resource策略，并隐藏Nginx版本。CSP允许本地头像预览和二维码的data/blob图片、React样式属性及同源脚本；`camera=(self)`保留本站手机摄像头权限。Nginx代理响应去除重复的后端安全头，再统一添加一次。HSTS只对HTTPS或可信代理转发的HTTPS发送。

首次修复复扫的两项低危已通过`Cross-Origin-Opener-Policy: same-origin`和`Cross-Origin-Embedder-Policy: require-corp`修复；最终复扫高、中、低危均为0。当前脚本、样式、API及插图均同源，头像预览和二维码使用data/blob图片。后续若新增跨源资源，需要其显式支持CORP或CORS，见[COEP说明](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cross-Origin-Embedder-Policy)。剩余信息项包括缓存策略及SPA识别，不表示已发现可利用漏洞。

修复后的响应头、公开入口、API和HTTPS Origin注册/登录冒烟通过；Chrome活动页和日历选择/应用正常，应用error日志为空。DAST门槛测试5项通过，覆盖中高危失败、低危保留、缺失报告/站点和种子访问不完整不能误报通过。

**云端修复尚待本次PR合并与部署。隔离通过不等于线上已修复。** 部署后的自动扫描会产生新的云端报告；确认其通过后再完成该验收项。

## 运行与报告

从仓库根目录运行，需要Docker及联网：

```bash
bash scripts/security/dast.sh https://swe5006.seventh720.com
```

报告默认保存到被Git忽略的`dast-reports/`：`zap.html`、`zap.json`、`zap.md`、`summary.md`、`coverage.json`、`scan.log`、`run.json`和镜像digest。`run.json`记录目标、时间、配置提交及工作树是否有未提交改动。报告不保存登录凭据。

本次云端初扫位于该目录顶层，隔离复扫位于`dast-reports/isolated/`。补齐COOP/COEP后的最终复扫位于`dast-reports/isolated-final/`，三份报告均为实际运行产物。隔离环境使用自签临时证书，只验证应用响应；它不证明云端证书或TLS配置质量。

可用`DAST_REPORT_DIR`指定其他报告目录；`DAST_DOCKER_NETWORK`用于连接已有隔离Docker网络。不要将隔离环境目标写入云端验收结果。

## CI与失败处理

新增`Security · DAST Baseline`作业依赖成功的`Deploy · Staging`，仅main推送部署后运行；PR不扫描现有云端版本。过时main运行会跳过，避免把当前站点结果归给旧提交。作业保存`dast-report` artifact共7天，摘要和Telegram汇总包含DAST状态。

高危或中危使作业失败；低危和信息项完整保留并显示。扫描器启动/连接错误、非预期退出、报告缺失或5个种子请求没有全部返回200也使作业失败，不把扫描失败当作零漏洞。

这是部署后检查，失败不会撤销已完成的部署，也没有自动数据库恢复。保留报告、复核发现并通过修复PR再部署；演示需要的报告应在artifact到期前下载。
