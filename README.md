# Tilt Automated UI Test Suite

End-to-end UI automation framework for **Tilt365**, built with **Java 17**, **Selenium 4**, **TestNG**, and **Maven**.

The suite supports:

- Local runs and full CI/CD (Jenkins + AWS EC2)
- Parallel execution
- MailSlurp-based email flows (invites, signup, reset)
- Stripe Checkout flows
- Rich reporting with **Allure** (screenshots, console logs, performance logs, network dumps)

---

## 🚀 Key Features

- **Selenium 4 + Java 17** UI automation
- **Page Object Model (POM)** with a shared `BasePage` and robust wait/click helpers
- **TestNG** suites: Smoke, Regression, CI/Parallel
- **MailSlurp integration** for email-driven flows
- **Stripe Checkout automation** (via Stripe REST API and Stripe CLI)
- **Configurable environments** via `.env.local`, system properties, or CI env vars
- **Allure Reports**:
  - Screenshots, page source, current URL
  - Browser console + performance logs (CDP)
  - Environment metadata (`environment.properties`)
- **Retries & stability**:
  - Retry transformer for flaky tests
  - Safer navigation (`robustGet`) and login bootstrap
- **CI-ready**:
  - Designed for Jenkins pipeline + AWS EC2 (headless Chrome)

---

## 📁 Project Structure

```text
tilt_automated_ui_testcases/
│
├── pom.xml                       # Maven dependencies & plugins (Allure, TestNG, Selenium, etc.)
├── src/
│   └── test/java/
│       ├── base/                 # BaseTest, DriverManager
│       ├── pages/                # Page Objects (POM) + BasePage
│       ├── tests/                # Test classes (Smoke, Regression, flows)
│       └── Utils/                # Config, MailSlurp, NetSniffer, DebugDumps, WaitUtils, etc.
│
├── testng-smoke.xml              # Smoke suite
├── testng-regression.xml         # Regression suite
├── testng-parallel.xml           # Parallel suite (methods-based, CI default)
├── testng-ci.xml                 # CI suite entry point (if needed)
│
├── .env.sample                   # Template for local env configuration
├── .env.local                    # Local overrides (gitignored) – read by Config
├── ci-local.sh                   # Local “mini-CI” runner script
├── JenkinsfileForRepoUnderTest   # Jenkins declarative pipeline
│
└── ENDPOINTS_SUMMARY.md          # Backend endpoints documentation (reference)
```

---

## ⚙️ Setup

### 1. Prerequisites

- Java 17+
- Maven 3.8+
- Chrome installed (local runs)
- (Optional) Stripe CLI
- (Optional) Allure CLI

### 2. Install dependencies

```bash
mvn clean install
```

### 3. Configure environment

The framework reads config from:

1. System properties
2. Environment variables
3. `.env.local`
4. Defaults (non-critical flags)

Create your local env:

```bash
cp .env.sample .env.local
```

Edit `.env.local`:

```properties
BASE_URL=https://tilt-dashboard-dev.tilt365.com/
ADMIN_EMAIL=...
ADMIN_PASSWORD=...
MAILSLURP_API_KEY=...
MAILSLURP_INBOX_ID=...
MAILSLURP_ALLOW_CREATE=false
STRIPE_SECRET_KEY=sk_test_...
```

Override at runtime:

```bash
mvn test -DbaseUrl=https://tilt-dashboard-dev.tilt365.com/
```

---

## ▶️ Running Tests

### Smoke Suite

```bash
mvn test -Dsurefire.suiteXmlFiles=testng-smoke.xml
```

### Regression Suite

```bash
mvn test -Dsurefire.suiteXmlFiles=testng-regression.xml
```

### Parallel Execution

```bash
mvn test -Dsurefire.suiteXmlFiles=testng-parallel.xml -Dparallel=methods
```

### CI-like Run

```bash
./ci-local.sh
```

---

## 🌎 Test Environments

| Environment | URL |
|------------|-----|
| Dev        | https://tilt-dashboard-dev.tilt365.com/ |
| Staging    | https://tilt-dashboard-staging.tilt365.com/ |
| Prod       | https://app.tilt365.com/ |

Switch via:

```bash
-DbaseUrl=<environment-url>
```

---

## 📊 Reporting with Allure

Allure output lives in:

```
target/allure-results/
```

Generate report:

```bash
mvn allure:report
```

Serve interactive UI:

```bash
mvn allure:serve
```

Artifacts include:

- Screenshots
- Page source
- Browser logs
- Performance logs
- environment.properties

---

## 🔒 MailSlurp

Handles email verification, signup, and invite tests.

Configuration:

```properties
MAILSLURP_API_KEY=...
MAILSLURP_INBOX_ID=...
MAILSLURP_ALLOW_CREATE=false
```

The pipeline validates the key and inbox before tests.

---

## 💳 Stripe Checkout

Supports both:

- Stripe test secret keys  
- Stripe CLI during development  

Example:

```bash
stripe listen --forward-to localhost:8080/webhook
stripe trigger payment_intent.succeeded
```

---

## 🧱 CI/CD (Jenkins + AWS)

Includes a full Jenkins pipeline with:

- Dockerized Maven + headless Chrome
- MailSlurp fixed inbox guard
- Parallel execution
- Screenshot + log archiving
- Allure report publishing
- Slack notifications (Allure / JUnit / Console links)

Typical CI run:

```bash
mvn -B -Dsurefire.suiteXmlFiles=testng-parallel.xml -Dheadless=true -Dparallel=methods
```

---

## 🛠 Tech Stack

- Java 17  
- Selenium 4  
- TestNG  
- Maven  
- Allure  
- MailSlurp  
- Stripe Java SDK  
- Jenkins + AWS EC2  

---

## 👤 Author

Automation framework developed and maintained by **Emiliano Rodríguez**.  
For support or contributions, open an issue or create a pull request.

---

## ✔️ License

Private project — all rights reserved.
