#!/usr/bin/env bash
set -euo pipefail

# Move to project root (4 levels up from src/test/java/scripts)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
cd "$REPO_ROOT"
echo "[ci-local] Running from project root: $REPO_ROOT"


# Copy envs from your .env or export manually before running:

export ADMIN_USER=erodriguez+a@effectussoftware.com
export ADMIN_PASS=Password#1
export BASE_URL=https://tilt-dashboard-dev.tilt365.com/
export STRIPE_TEST_SECRET_KEY=rk_test_40W8vi2ajcXnxpjHnMcNtfePyfHiIfhP8K7DTGUcMwfbVrMqky5BTlaWDzoDhorIHlFTaM5rV0F1rh8P9fW9UliXx00lfDit7Pq
export STRIPE_PUBLISHABLE_KEY=pk_test_40W8vi2ajcXnxpjHnMcNtdof2NpVc0vlzdpaDvaCEC4McUeiSO6AGItrEOdI8hnOYACqHUyYVyYbInMapgUHoDOgi008dvELtA5

export CI=true
export CHROME_MAJOR_PIN="${CHROME_MAJOR_PIN:-142}"
export CI_EXPLICIT_WAIT_SEC="${CI_EXPLICIT_WAIT_SEC:-60}"
#export MAILSLURP_EXPECTED_FP="${MAILSLURP_EXPECTED_FP:-579d2267880c}"


export MAILSLURP_API_KEY=sk_SGpxMjUdwbPpRDLs_4qruIteMSY2lfYorLHb2paRWmsoM0GLaHcMyY0vaOCKMQXAGJANOBcIYqrGPgvpt
export MAILSLURP_INBOX_ID=91072010-c653-408e-884b-48d435345bec

# Account 1
export MAILSLURP_API_KEY_1="sk_kZymxnA0S1M3oK0T_VabEI1sjiWCus4cjPHa3q9liqhKi5FkCxgvh4Xci70Wq51RGbZY6HRTnPJsQyuwO"
export MAILSLURP_INBOX_ID_1="a8745366-2ed9-4ac2-9b68-b286bf742524"

# Account 2
export MAILSLURP_API_KEY_2="sk_qQ7dobf8tvSiOUQ6_NH1Sl0rDteEzIdaXlJaDYYmR3TooGwAHmI61kMcKo1fY0X0tIa9jxu1U8p0HqndS"
export MAILSLURP_INBOX_ID_2="a2009c24-57c1-4618-8b49-9d8ea5d560ea"

# Account 3
export MAILSLURP_API_KEY_3="sk_170QvvcElWIu3ARj_U5PTfAsSP2mpzcN9WT9jZpASqRkj9u8cBnIfq756xrGqbrGfXbCoSycT7zaBKUjw"
export MAILSLURP_INBOX_ID_3="02589ce5-e5a9-45ff-b710-6620149bf6e4"

# Account 4
export MAILSLURP_API_KEY_4="sk_UaEVQnWqjiurplXd_FJQJAGI8Tch6twNHJUtg2bhKfUjySbHsglHuAlhwT6WVnD66dOYiFbtdZSM5LNkD"
export MAILSLURP_INBOX_ID_4="6e163729-ad04-450b-8ad0-c7f600431b5d"

# Account 5
export MAILSLURP_API_KEY_5="sk_GrSLhkKitDtvTwiq_ZikE80FubNXmnwhD0hL2Rga33utpw2a4QbESMgqAmDy5k1YbUyM48HWdLAODPE4s"
export MAILSLURP_INBOX_ID_5="afa6def5-909a-40bc-bec2-2f46112faba5"

# Account 6
export MAILSLURP_API_KEY_6="sk_bA9sLdCdwyXqra8e_bEH60luab2x18rIFyfQQOlNNkTgD49SpruhV0eGEvQprMipvDE7ClxZwATfA4SXM"
export MAILSLURP_INBOX_ID_6="aecb9d44-5e44-492a-b7a1-adde0f50b107"

# Account 7
export MAILSLURP_API_KEY_7="sk_oaaIIpHeaCz3MSGr_eNocmxNJdzOdPpXMpSGo9uZG7Zj8P1109nP5m2ZiLR8oJZQvjlAI8GtGnHFUnW2j"
export MAILSLURP_INBOX_ID_7="453b7ee5-514e-4aa7-a0bd-ba2d15fa0685"

# Account 8
export MAILSLURP_API_KEY_8="sk_IYoPEskMsCF5SSSs_FTIkyM7lZz5SbhTxwyptTVEfbKgws3EuXUpZ1iZ6s0mtxDk7bXlWeaPMjxKegu7w"
export MAILSLURP_INBOX_ID_8="408efb09-fad6-4fe8-8eb2-a8dfd6a71f91"

# Account 9
export MAILSLURP_API_KEY_9="sk_vRfDS5EIjoqx5qkj_GbTKvmRNVwcdQ64y0eVeQ1C3FsPUwa1zyenAWw7ghVA2p8Zk5NRzSyWjSGhvuD6G"
export MAILSLURP_INBOX_ID_9="020e1493-a77e-412b-9b1a-a9a19db49968"

# Account 10
export MAILSLURP_API_KEY_10="sk_bRWktBAPgQ8z29Re_sZyclqj8FbmrQPXXQW5yCs4Onxhhv7UPr6PWjCPeOgeAC6PScm7vNNfFU1AgqfXC"
export MAILSLURP_INBOX_ID_10="69b37666-8842-419f-bc27-777ac8409b97"






# Optional: reproduce Jenkins timeout etc.


mvn -B \
  -Dheadless=true -Dbrowser=chrome -DskipITs=false \
  -Dsurefire.suiteXmlFiles=testng-parallel.xml \
  -Dmailslurp.debug=true \
  -DdisableLocalConfig=true \
  -DbaseUrl="$BASE_URL" \
  -DADMIN_USER="$ADMIN_USER" \
  -DADMIN_PASS="$ADMIN_PASS" \
  -Dtimeout="$CI_EXPLICIT_WAIT_SEC" \
  -Dretry=1 \
  -DMAILSLURP_ALLOW_CREATE_INBOX_FALLBACK=true \
  clean test




#  -Dmailslurp.forceKey="$MAILSLURP_API_KEY" \
#  -Dmailslurp.apiKey="$MAILSLURP_API_KEY" \
#  -DMAILSLURP_INBOX_ID="$MAILSLURP_INBOX_ID" \
#  -Dmailslurp.expectedFingerprint="$MAILSLURP_EXPECTED_FP" \







# Account 1 - emilianorod14@op.xn--yaho-sqa.com, Password#1
# Account 2 - emilianorod15@op.xn--yaho-sqa.com, Password#1
# Account 3 - emilianorod16@op.xn--yaho-sqa.com, Password#1
# Account 4 - emilianorod17@op.xn--yaho-sqa.com, Password#1
# Account 5 - emilianorod18@op.xn--yaho-sqa.com, Password#1
# Account 6 - emilianorod19@op.xn--yaho-sqa.com, Password#1
# Account 7 - emilianorod20@op.xn--yaho-sqa.com, Password#1
# Account 8 - emilianorod21@op.xn--yaho-sqa.com, Password#1
# Account 9 - emilianorod22@op.xn--yaho-sqa.com, Password#1
# Account 10 - emilianorod23@op.xn--yaho-sqa.com, Password#1