#!/usr/bin/env bash
set -euo pipefail

export CI=true
export THREADS="${THREADS:-4}"
export WDM_CHROME_MAJOR="${WDM_CHROME_MAJOR:-138}"

# MailSlurp strategy: fixed inbox; or flip to true to create per run
export MAILSLURP_ALLOW_CREATE="${MAILSLURP_ALLOW_CREATE:-false}"
export MAILSLURP_INBOX_ID="${MAILSLURP_INBOX_ID:-5b7473c7-bcd5-4ad4-b217-5c48310892e0}"

# App config
BASE_URL="${BASE_URL:-https://tilt-dashboard-dev.tilt365.com/}"

# Optional: Stripe CLI path sanity (only if your tests need it)
command -v stripe >/dev/null && stripe --version || echo "[WARN] Stripe CLI not found"

mvn -B -e -Dmaven.test.failure.ignore=false \
  -Dsurefire.suiteXmlFiles=testng-ci.xml \
  -DbaseUrl="$BASE_URL" \
  -Dparallel=methods \
  -Dsurefire.printSummary=true \
    -Dheadless=true -Dbrowser=chrome \
    -DpageLoad=normal -Dui.lang=en-US -Dui.tz=America/Montevideo \
    -Dui.window=1440,900 -Dui.scale=1 \
    -Dwd.pageLoadSec=60 -Dwd.scriptSec=30 \
  test

# Allure preview (if installed): allure serve target/allure-results
