#!/usr/bin/env bash
set -euo pipefail

# Copy envs from your .env or export manually before running:
export MAILSLURP_API_KEY=feba6e455ec86d89c45a7124e5529bcf944c543c3bd45cdd97cd84e0005fa87d
export MAILSLURP_INBOX_ID=890b3195-57ed-4c4d-be8b-5af1bf09548c
export ADMIN_USER=erodriguez+a@effectussoftware.com
export ADMIN_PASS=Password#1
export BASE_URL=https://tilt-dashboard-dev.tilt365.com/
export STRIPE_TEST_SECRET_KEY=rk_test_40W8vi2ajcXnxpjHnMcNtfePyfHiIfhP8K7DTGUcMwfbVrMqky5BTlaWDzoDhorIHlFTaM5rV0F1rh8P9fW9UliXx00lfDit7Pq
export STRIPE_PUBLISHABLE_KEY=pk_test_40W8vi2ajcXnxpjHnMcNtdof2NpVc0vlzdpaDvaCEC4McUeiSO6AGItrEOdI8hnOYACqHUyYVyYbInMapgUHoDOgi008dvELtA5

export CI=true
export CHROME_MAJOR_PIN="${CHROME_MAJOR_PIN:-142}"
export CI_EXPLICIT_WAIT_SEC="${CI_EXPLICIT_WAIT_SEC:-60}"
export MAILSLURP_EXPECTED_FP="${MAILSLURP_EXPECTED_FP:-579d2267880c}"

# Optional: reproduce Jenkins timeout etc.

mvn -B \
  -Dheadless=true -Dbrowser=chrome -DskipITs=false \
  -Dsurefire.suiteXmlFiles=testng-parallel.xml \
  -Dmailslurp.forceKey="$MAILSLURP_API_KEY" \
  -Dmailslurp.apiKey="$MAILSLURP_API_KEY" \
  -DMAILSLURP_INBOX_ID="$MAILSLURP_INBOX_ID" \
  -Dmailslurp.expectedFingerprint="$MAILSLURP_EXPECTED_FP" \
  -Dmailslurp.debug=true \
  -DdisableLocalConfig=true \
  -DbaseUrl="$BASE_URL" \
  -DADMIN_USER="$ADMIN_USER" \
  -DADMIN_PASS="$ADMIN_PASS" \
  -Dtimeout="$CI_EXPLICIT_WAIT_SEC" \
  clean test
