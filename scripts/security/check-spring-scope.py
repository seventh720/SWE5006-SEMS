#!/usr/bin/env python3
"""Require a new applicability review if server-rendered views/SSE are introduced."""
from pathlib import Path
import re

features = re.compile(
    r'XsltView|ViewResolver|SseEmitter|ServerSentEvent|text/event-stream|'
    r'@Controller\b|ModelAndView|FragmentsRendering|spring-boot-starter-(thymeleaf|freemarker|mustache)'
)
paths = [Path('backend/pom.xml'), *Path('backend/src/main').rglob('*')]
violations = [str(p) for p in paths if p.is_file()
              and p.suffix in {'.java', '.xml', '.yaml', '.yml', '.properties'}
              and features.search(p.read_text())]
if violations:
    raise SystemExit('Reassess Spring view/SSE exceptions: ' + ', '.join(violations))
print('Spring applicability guard passed (does not replace architectural review).')
