 #!/usr/bin/env bash
input="$(cat)"
agent=$(echo "$input" | grep -o '"agent_type"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*:[[:space:]]*"//;s/"$//')
case "$agent" in
  release-implementor) echo "You are Release Implementor. Read charter at docs/agents/claude/release-implementor.md before acting." ;;
  feature-planner) echo "You are Feature Planner. Read charter at docs/agents/claude/feature-planner.md before acting." ;;
  *) exit 0 ;;
esac