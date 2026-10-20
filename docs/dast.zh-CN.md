# DAST Baseline

2026-10-20核验业务基线`270c2b8`：[main运行39311200552](https://github.com/seventh720/SWE5006-SEMS/actions/runs/39311200552)的部署与DAST均成功。

## 当前云端结果

| 目标 | URL数 | 规则通过 | 高危 | 中危 | 低危 | 信息 |
|---|---:|---:|---:|---:|---:|---:|
| `https://swe5006.seventh720.com` | 22 | 66 | 0 | 0 | 0 | 4 |

无失败、警告或IGNORE规则。信息项为SPA识别及缓存策略；此结果不代表完成主动攻击或全站渗透测试。

此前发现的CSP、防嵌入及其他安全头已修复；COOP/COEP也已补齐。CSP保留同源脚本、React样式属性、data/blob头像及二维码，`camera=(self)`允许本站相机。后续跨源资源须满足CSP及CORP/CORS要求。

PR #38的首次云端扫描已经零高/中/低危，但容器用户生成的Python缓存导致runner清理失败；PR #39通过`PYTHONDONTWRITEBYTECODE=1`修复。最新main已验证报告保存、清理及作业整体退出成功，不再将该问题列为待办。

## 扫描范围与门槛

[脚本](../scripts/security/dast.sh)使用固定digest的官方ZAP 2.17.0镜像运行[Baseline](https://www.zaproxy.org/docs/docker/baseline-scan/)。爬行1分钟并显式访问首页、登录页、活动列表、公开活动GET API和健康接口，扫描关联静态资源；关闭表单处理/POST提交，不创建线上账号订单。

仅main部署后运行；PR不扫描既有部署。高/中危、扫描器错误、缺失报告或种子请求未全部200使作业失败；低危/信息项保留。部署后失败不自动回滚容器或数据库。

本基线没有登录后覆盖、SQL注入/XSS主动验证、业务越权验证或压力测试。云端业务权限/事务验收单独记录在[10月20日验收](acceptance/sprint4-2026-10-20/README.md)，不能用被动扫描替代。

## 运行与报告

```bash
bash scripts/security/dast.sh https://swe5006.seventh720.com
```

需要Docker及联网。输出至忽略目录`dast-reports/`，包含HTML/JSON/Markdown、摘要、种子覆盖、日志、时间/配置提交及镜像digest。`DAST_REPORT_DIR`可改目录，`DAST_DOCKER_NETWORK`用于已有隔离网络。

Actions上传`dast-report`并保留7天，摘要和Telegram汇总DAST状态。成功main的原报告已下载到本机`dast-reports/github-39311200552/`；演示前与其他离线材料一并备份，不依赖artifact永久可用。
