 #!/usr/bin/env bash
input="$(cat)"
agent=$(echo "$input" | grep -o '"agent_type"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*:[[:space:]]*"//;s/"$//')
case "$agent" in
  release-implementor) echo "You are Release Implementor. Read charter at docs/agents/claude/release-implementor.md before acting." ;;
  feature-planner) echo "You are Feature Planner. Read charter at docs/agents/claude/feature-planner.md before acting." ;;
  note-strategist) echo "You are the Claude Note Strategist (the GPT Note Strategist is a different party). Read charter at docs/agents/claude/note-strategist.md and docs/playbooks/study-journey-playbook.md before acting. You never write to production." ;;
  *) exit 0 ;;
esac