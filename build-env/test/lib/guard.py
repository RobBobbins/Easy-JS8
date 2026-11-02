"""Claude Code hook: refuses screen test runs across more than one layout (Rob, 2026-11-02)."""
import json, re, sys
cmd = json.load(sys.stdin).get('tool_input', {}).get('command', '')
if re.search(r'\b(runui|ui1|ui2)(\.py)?\b', cmd) and re.search(r'(?<![\w.-])[UPGF]{2,}(?![\w.-])', cmd):
    print('Blocked: screen tests run one layout at a time, with JS8_ROWS for reruns. See TEST-PLAN.md.', file=sys.stderr)
    sys.exit(2)
