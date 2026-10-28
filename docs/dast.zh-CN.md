# DAST Baseline

以下统计对应2026-10-20保存的业务基线`270c2b8`及[main运行39311200552](https://github.com/seventh720/SWE5006-SEMS/actions/runs/39311200552)，部署与DAST均成功。后续已核验部署的扫描状态见[测试说明](testing.zh-CN.md)；此处保留原报告范围与执行方法。

2026-10-21补充核验：最新`565f08b`的[main运行39566477705](https://github.com/seventh720/SWE5006-SEMS/actions/runs/39566477705)中，DAST作业成功；日志记录High=0、Medium=0、Low=0、Informational=4、PASS=66。27 October 的 main 运行也已通过，见[验证索引](acceptance/report-evidence-2026-10-27/README.md)；下表仍是已保存的原基线报告。

## 已保存的扫描基线

| 目标 | URL数 | 规则通过 | 高危 | 中危 | 低危 | 信息 |
|---|---:|---:|---:|---:|---:|---:|
| `https://swe5006.seventh720.com` | 22 | 66 | 0 | 0 | 0 | 4 |

无失败、警告或IGNORE规则。信息项为SPA识别及缓存策略；此结果不代表完成主动攻击或全站渗透测试。

此前发现的CSP、防嵌入及其他安全头已修复；COOP/COEP也已补齐。CSP保留同源脚本、React样式属性、data/blob头像及二维码，`camera=(self)`允许本站相机。后续跨源资源须满足CSP及CORP/CORS要求。

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
