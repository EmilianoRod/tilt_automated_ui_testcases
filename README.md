# Tilt Automated UI Test Suite

This repository contains the end‑to‑end UI automation framework for **Tilt365** using Java, Selenium, TestNG, and Maven.  
It supports local execution, CI/CD (Jenkins & AWS), parallel test runs, MailSlurp email testing, Stripe flows, and advanced reporting with Allure.

---

## 🚀 Features

- **Selenium 4 + Java 17** automation framework  
- **Page Object Model (POM)** + custom utilities  
- **TestNG** test suites: Smoke, Regression, Parallel, CI  
- **MailSlurp integration** for invite & signup flows  
- **Stripe Checkout automation**  
- **Configurable environments** via `.env` or system properties  
- **Allure Reports** integration  
- **Retry logic**, improved waits, and BasePage utilities  
- **Designed for AWS EC2 + Jenkins pipelines**

---

## 📁 Project Structure

```
tilt_automated_ui_testcases-main/
│
├── pom.xml                 # Maven dependencies & plugins
├── src/
│   ├── main/java/          # Page objects, utilities, drivers
│   └── test/java/          # Test classes
│
├── testng-smoke.xml        # Smoke suite
├── testng-regression.xml   # Regression suite
├── testng-parallel.xml     # Parallel suite (methods-based)
├── testng-ci.xml           # CI suite for Jenkins/AWS
│
├── .env.sample             # Environment variable template
├── ci-local.sh             # Local CI simulation script
├── JenkinsfileForRepoUnderTest
│
└── ENDPOINTS_SUMMARY.md    # Backend endpoints documentation
```

---

## ⚙️ Setup

### 1. Install dependencies
```bash
mvn clean install
```

### 2. Configure environment variables  
Copy the sample file:

```bash
cp .env.sample .env
```

Edit with your credentials:

```
BASE_URL=https://tilt-dashboard-dev.tilt365.com/
ADMIN_EMAIL=...
ADMIN_PASSWORD=...
MAILSLURP_API_KEY=...
```

Or pass them as system properties:

```bash
-DbaseUrl=...
-DadminEmail=...
-DadminPassword=...
```

---

## ▶️ Running Tests

### **Smoke Suite**
```bash
mvn test -Dsurefire.suiteXmlFiles=testng-smoke.xml
```

### **Regression Suite**
```bash
mvn test -Dsurefire.suiteXmlFiles=testng-regression.xml
```

### **Parallel**
```bash
mvn test -Dsurefire.suiteXmlFiles=testng-parallel.xml -Dparallel=methods
```

### **CI Execution**
```bash
./ci-local.sh
```

---

## 🧪 Test Environments

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

## 📊 Reporting (Allure)

### Generate Allure Report  
```bash
mvn allure:report
```

### Serve UI  
```bash
mvn allure:serve
```

---

## 🔒 MailSlurp

Used for invite + signup flows.

Settings in `.env`:

```
MAILSLURP_API_KEY=...
MAILSLURP_INBOX_ID=...
MAILSLURP_ALLOW_CREATE=false
```

---

## 💳 Stripe Checkout Support

Some tests require the Stripe CLI or mock Stripe environment:

```bash
stripe listen --forward-to localhost:8080/webhook
stripe trigger payment_intent.succeeded
```

---

## 🧱 CI/CD (Jenkins + AWS)

The project includes:

- `JenkinsfileForRepoUnderTest` (pipeline definition)
- Headless Chrome execution
- Parallel test execution
- Artifact upload (screenshots + logs)
- Allure publishing

Typical Jenkins command:

```bash
mvn -B -Dmaven.test.failure.ignore=false     -Dsurefire.suiteXmlFiles=testng-ci.xml     -Dheadless=true -Dparallel=methods
```

---

## 🛠 Tech Stack

- **Java 17**
- **Selenium 4**
- **TestNG**
- **Maven**
- **Allure**
- **MailSlurp API**
- **Stripe CLI (optional)**
- **AWS EC2 (c7i.xlarge / t3.large)**
- **Jenkins**

---

## 👤 Author

Automation Framework developed and maintained by **Emiliano Rodríguez**.

For support or contribution, please open a PR or create an issue.

---

## ✔️ License

Private project – all rights reserved.
